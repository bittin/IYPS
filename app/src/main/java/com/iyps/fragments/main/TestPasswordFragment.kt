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

package com.iyps.fragments.main

import android.graphics.Color
import android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.inputmethod.EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doOnTextChanged
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.transition.MaterialContainerTransform
import com.iyps.activities.MainActivity
import com.iyps.bottomsheets.TestMultiPwdBottomSheet
import com.iyps.fragments.common.BasePwdResultsFragment
import com.iyps.objects.AppState
import com.iyps.preferences.PreferenceManager
import com.iyps.preferences.PreferenceManager.Companion.INCOG_KEYBOARD
import com.iyps.utils.ClipboardUtils.Companion.scheduleClipboardClear
import com.iyps.utils.UiUtils.Companion.convertDpToPx
import com.iyps.utils.UiUtils.Companion.showSupportAnimBtmSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import kotlin.time.Duration.Companion.milliseconds

class TestPasswordFragment : BasePwdResultsFragment() {
    
    private lateinit var mainActivity: MainActivity
    
    override fun setupFragmentContent() {
        mainActivity = requireActivity() as MainActivity
        var job: Job? = null
        var isInitialLaunch = true
        val isIncogKeyboard = get<PreferenceManager>().getBoolean (INCOG_KEYBOARD)
        
        // Adjust UI components for edge to edge
        ViewCompat.setOnApplyWindowInsetsListener(fragmentBinding.scrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                                                        or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = insets.left, right = insets.right, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(fragmentBinding.testMultipleFab) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                                                        or WindowInsetsCompat.Type.displayCutout())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = insets.right + convertDpToPx(requireContext(), 16f)
                bottomMargin = insets.bottom + convertDpToPx(requireContext(), 25f)
            }
            WindowInsetsCompat.CONSUMED
        }
        
        // Prevent dragging of appbar when scrollview is not visible
        val appBarLayoutBehavior =
            AppBarLayout.Behavior().also {
                (fragmentBinding.appBar.layoutParams as CoordinatorLayout.LayoutParams).behavior = it
            }
        appBarLayoutBehavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout): Boolean {
                return !isInitialLaunch
            }
        })
        
        fragmentBinding.topPasswordBox.isVisible = false
        fragmentBinding.centerPasswordBox.isVisible = true
        fragmentBinding.scrollView.isVisible = false
        
        if (isInitialLaunch) {
            fragmentBinding.centerPasswordText.apply {
                if (isIncogKeyboard) setIncogMode()
                setOnEditorActionListener { textView, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        textView.text.let {
                            if (it.isNotEmpty()){
                                transformToTopTextBox(it)
                                displayPwdResults(it)
                                isInitialLaunch = false
                            }
                            
                        }
                        true
                    }
                    else false
                }
            }
        }
        
        fragmentBinding.topPasswordText.apply {
            if (isIncogKeyboard) setIncogMode()
            doOnTextChanged { charSequence, _, _, _ ->
                if (isInitialLaunch) return@doOnTextChanged
                
                // Introduce a subtle delay
                // So passwords are checked after typing is finished
                job?.cancel()
                job =
                    lifecycleScope.launch {
                        delay(350.milliseconds)
                        if (charSequence!!.isNotEmpty()) {
                            fragmentBinding.copyChipGroup.forEach {
                                (it as? Chip)?.apply {
                                    if (!isEnabled) isEnabled = true
                                }
                            }
                            displayPwdResults(charSequence)
                            if (AppState.showSupportBtmSheet) {
                                showSupportAnimBtmSheet(parentFragmentManager)
                            }
                        }
                        // If edit text is empty or cleared, reset everything
                        else {
                            fragmentBinding.copyChipGroup.forEach {
                                (it as? Chip)?.isEnabled = false
                            }
                            resetDetails()
                        }
                    }
            }
            
            // Detect if copied from this app
            customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    return true
                }
                
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    return true
                }
                
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                    when (item?.itemId) {
                        android.R.id.copy -> {
                            copyToClipboard(text.toString())
                            scheduleClipboardClear(requireContext())
                        }
                    }
                    return true
                }
                
                override fun onDestroyActionMode(mode: ActionMode?) {}
            }
        }
        
        // Fab
        fragmentBinding.testMultipleFab.setOnClickListener {
            TestMultiPwdBottomSheet().show(parentFragmentManager, "TestMultiplePwdBottomSheet")
        }
    }
    
    private fun TextInputEditText.setIncogMode() {
        imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING
        inputType = TYPE_TEXT_VARIATION_PASSWORD
    }
    
    private fun transformToTopTextBox(initialText: CharSequence) {
        val transform =
            MaterialContainerTransform().apply {
                startView = fragmentBinding.centerPasswordBox
                endView = fragmentBinding.topPasswordBox
                duration = 350L
                scrimColor = Color.TRANSPARENT // Prevent dark/dim background
                drawingViewId = fragmentBinding.testCoordLayout.id
                fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
                
                addListener(object : TransitionListenerAdapter() {
                    override fun onTransitionEnd(transition: Transition) {
                        transition.removeListener(this)
                        TransitionManager.beginDelayedTransition(
                            fragmentBinding.root,
                            Fade(Fade.IN).apply {
                                duration = 250L
                                interpolator = FastOutSlowInInterpolator()
                                addTarget(fragmentBinding.scrollView)
                            }
                        )
                        fragmentBinding.scrollView.isVisible = true
                    }
                })
            }
        
        fragmentBinding.topPasswordText.apply {
            setText(initialText)
            setSelection(initialText.length)
        }
        
        TransitionManager.beginDelayedTransition(
            fragmentBinding.root,
            transform
        )
        
        fragmentBinding.centerPasswordBox.isVisible = false
        fragmentBinding.topPasswordBox.apply {
            isVisible = true
            requestFocus()
        }
    }
    
    private fun resetDetails() {
        fragmentBinding.apply {
            tenBGuessesStrength.text = naString
            tenKGuessesStrength.text = naString
            tenGuessesStrength.text = naString
            hundredGuessesStrength.text = naString
            tenBGuessesSubtitle.text = naString
            tenKGuessesSubtitle.text = naString
            tenGuessesSubtitle.text = naString
            hundredGuessesSubtitle.text = naString
            warningSubtitle.text = naString
            suggestionsSubtitle.text = naString
            guessesSubtitle.text = naString
            orderMagnSubtitle.text = naString
            orderMagnSubtitle.text = naString
            entropySubtitle.text = naString
            matchSequenceSubtitle.text = naString
            statsSubtitle.text = naString
            
            tenBGuessesStrengthMeter.apply {
                setIndicatorColor(emptyMeterColor)
                setProgressCompat(0, true)
            }
            
            tenKGuessesStrengthMeter.apply {
                setIndicatorColor(emptyMeterColor)
                setProgressCompat(0, true)
            }
            
            tenGuessesStrengthMeter.apply {
                setIndicatorColor(emptyMeterColor)
                setProgressCompat(0, true)
            }
            
            hundredGuessesStrengthMeter.apply {
                setIndicatorColor(emptyMeterColor)
                setProgressCompat(0, true)
            }
        }
        
    }
    
    override fun getCoordinatorLayout(): CoordinatorLayout {
        return mainActivity.activityBinding.mainCoordLayout
    }
    
    override fun getSnackbarAnchorView(): View {
        return fragmentBinding.testMultipleFab
    }
}