/*
 *     Copyright (C) 2022-present StellarSand
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.iyps.activities

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.iyps.R
import com.iyps.adapters.MultiPwdAdapter
import com.iyps.databinding.ActivityMultiPwdBinding
import com.iyps.models.LinePointer
import com.iyps.objects.AppState
import com.iyps.objects.MultiPwdsInput
import com.iyps.paging.MultiPwdsPagingSource
import com.iyps.preferences.PreferenceManager
import com.iyps.preferences.PreferenceManager.Companion.BLOCK_SS
import com.iyps.preferences.PreferenceManager.Companion.GRID_VIEW
import com.iyps.preferences.PreferenceManager.Companion.SORT_ASC
import com.iyps.utils.UiUtils.Companion.blockScreenshots
import com.iyps.utils.UiUtils.Companion.convertDpToPx
import com.iyps.utils.UiUtils.Companion.setNavBarContrastEnforced
import com.iyps.utils.UiUtils.Companion.showSupportAnimBtmSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.stellarsand.android.fastscroll.FastScrollerBuilder
import org.koin.android.ext.android.inject
import kotlin.getValue

class MultiPwdActivity : AppCompatActivity(), MultiPwdAdapter.OnItemClickListener {
    
    private lateinit var activityBinding: ActivityMultiPwdBinding
    private val prefManager by inject<PreferenceManager>()
    private lateinit var multiPwdAdapter: MultiPwdAdapter
    private val currentInputSource by lazy { MultiPwdsInput.currentSource }
    private val pagingConfig =
        PagingConfig(
            pageSize = 25,
            prefetchDistance = 10,
            enablePlaceholders = false
        )
    private var pagingJob: Job? = null
    private var originalIndicesList = listOf<Int>()
    private var originalPointersList = listOf<LinePointer>()
    private var shouldScrollToTop = false
    var isGridView = false
    var isAscSort = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.apply {
            setNavBarContrastEnforced()
            requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
            returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
        }
        
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        activityBinding = ActivityMultiPwdBinding.inflate(layoutInflater)
        setContentView(activityBinding.root)
        
        val gridLayoutManager = GridLayoutManager(this, 1)
        isGridView = prefManager.getBoolean(GRID_VIEW, defValue = false)
        isAscSort = prefManager.getBoolean(SORT_ASC)
        
        multiPwdAdapter = MultiPwdAdapter(this)
        activityBinding.recyclerViewRoot.recyclerView.apply {
            // Adjust recyclerview for edge to edge
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                                                            or WindowInsetsCompat.Type.displayCutout())
                v.updatePadding(
                    left = insets.left,
                    top = insets.top + convertDpToPx(this@MultiPwdActivity, 10f),
                    right = insets.right,
                    bottom = insets.bottom + convertDpToPx(this@MultiPwdActivity, 90f)
                    // 90 = 64 + 16 + 10
                    // Floating toolbar height = 64dp
                    // Floating toolbar bottom margin = 16dp
                    // Space between floating toolbar & last item in recycler view = 10dp
                )
                WindowInsetsCompat.CONSUMED
            }
            
            adapter = multiPwdAdapter
            layoutManager = gridLayoutManager
            FastScrollerBuilder(this).build()
        }
        
        // Disable screenshots and screen recordings
        window.blockScreenshots(prefManager.getBoolean(BLOCK_SS))
        
        // Back
        activityBinding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        // View
        activityBinding.viewButton.apply {
            setViewButtonIcon()
            setOnClickListener {
                isGridView = !isGridView
                setViewButtonIcon()
                gridLayoutManager.spanCount = if (isGridView) 2 else 1
                multiPwdAdapter.notifyItemRangeChanged(0, multiPwdAdapter.itemCount)
            }
        }
        
        // Sort
        activityBinding.sortButton.setOnClickListener {
            isAscSort = !isAscSort
            shouldScrollToTop = true
            lifecycleScope.launch {
                sortAndLoadData()
            }
        }
        
        lifecycleScope.launch {
            if (AppState.showSupportBtmSheet) {
                showSupportAnimBtmSheet(supportFragmentManager)
            }
            currentInputSource?.let {
                when (it) {
                    is MultiPwdsInput.Source.ManualInput -> {
                        originalIndicesList = it.lines.indices.toList()
                        sortAndLoadData()
                    }
                    is MultiPwdsInput.Source.FileInput -> {
                        buildFileLinePointersMap(it.fileUri)
                        sortAndLoadData()
                    }
                }
            }
        }
    }
    
    private fun MaterialButton.setViewButtonIcon() {
        icon = ContextCompat.getDrawable(this@MultiPwdActivity,
                                         if (!isGridView) R.drawable.ic_view_grid
                                         else R.drawable.ic_view_list)
    }
    
    // This function scans the text file line by line to map it out,
    // without loading all the text into memory at once.
    // For every line, it saves a pointer object.
    // This pointer remembers:
    // - The exact byte position where the line starts in the file
    // - The size of that line in bytes
    // - The actual text (stored temporarily so we can sort it later)
    private suspend fun buildFileLinePointersMap(fileUri: Uri) {
        withContext(Dispatchers.IO) {
            val pointers = mutableListOf<LinePointer>()
            var currentOffset = 0L
            var lineCount = 0
            
            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                inputStream.bufferedReader().forEachLine { line ->
                    val lineBytes = line.toByteArray(Charsets.UTF_8)
                    val lineSize = lineBytes.size
                    val totalLineLength = lineSize + 1 // + 1 for "\n" character
                    
                    if (line.isNotEmpty()) {
                        lineCount++
                        pointers.add(
                            LinePointer(
                                originalLineNumber = lineCount,
                                byteOffset = currentOffset,
                                length = lineSize,
                                lineText = line
                            )
                        )
                    }
                    
                    currentOffset += totalLineLength
                }
            }
            
            originalPointersList = pointers
        }
    }
    
    private suspend fun sortAndLoadData() {
        currentInputSource?.let { source ->
            var sortedIndicesList = listOf<Int>()
            var sortedPointersList = listOf<LinePointer>()
            withContext(Dispatchers.Default) {
                when (source) {
                    is MultiPwdsInput.Source.ManualInput -> {
                        sortedIndicesList =
                            if (isAscSort) originalIndicesList.sortedBy { source.lines[it] }
                            else originalIndicesList.sortedByDescending { source.lines[it] }
                    }
                    
                    is MultiPwdsInput.Source.FileInput -> {
                        sortedPointersList =
                            if (isAscSort) originalPointersList.sortedBy { it.lineText }
                            else originalPointersList.sortedByDescending { it.lineText }
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                loadPagedData(sortedIndicesList, sortedPointersList)
            }
        } ?: return
    }
    
    private fun loadPagedData(sortedIndicesList: List<Int>, sortedPointersList: List <LinePointer>) {
        currentInputSource?.let { source ->
            pagingJob?.cancel()
            pagingJob =
                lifecycleScope.launch {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        launch {
                            multiPwdAdapter.onPagesUpdatedFlow.collect {
                                // Scroll to top only when sort prefs changed,
                                // not when items inserted/deleted/updated
                                if (shouldScrollToTop) {
                                    activityBinding.recyclerViewRoot.recyclerView.scrollToPosition(0)
                                    shouldScrollToTop = false
                                }
                            }
                        }
                        
                        launch {
                            Pager(
                                config = pagingConfig,
                                pagingSourceFactory = {
                                    MultiPwdsPagingSource(
                                        this@MultiPwdActivity,
                                        source,
                                        sortedIndicesList,
                                        sortedPointersList
                                    )
                                }
                            ).flow
                                .collectLatest { pagingData ->
                                    multiPwdAdapter.submitData(pagingData)
                                }
                        }
                    }
                }
        } ?: return
    }
    
    // On click
    override fun onItemClick(position: Int) {
        startActivity(
            Intent(this, DetailsActivity::class.java)
                .putExtra("PwdLine", multiPwdAdapter.peek(position)),
            ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        )
    }
    
    // On back pressed
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            finishAfterTransition()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        prefManager.apply {
            setBoolean(GRID_VIEW, isGridView)
            setBoolean(SORT_ASC, isAscSort)
        }
        MultiPwdsInput.clear()
    }
}