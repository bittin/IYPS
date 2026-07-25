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

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.iyps.R
import com.iyps.databinding.ActivityDetailsBinding
import com.iyps.fragments.details.PassphraseDetailsFragment
import com.iyps.fragments.details.PasswordDetailsFragment
import com.iyps.models.GenPhraseDetails
import com.iyps.preferences.PreferenceManager
import com.iyps.preferences.PreferenceManager.Companion.BLOCK_SS
import com.iyps.utils.UiUtils.Companion.blockScreenshots
import com.iyps.utils.UiUtils.Companion.setNavBarContrastEnforced
import org.koin.android.ext.android.get

class DetailsActivity : AppCompatActivity() {
    
    lateinit var passwordLine: CharSequence
    lateinit var activityBinding: ActivityDetailsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        val position = intent.getIntExtra("ItemPos", -1)
        val sharedTransitionName = if (position != -1) "item_$position" else "gen_details_btn"
        window.apply {
            setNavBarContrastEnforced()
            requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
            returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
            setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
            setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
            MaterialContainerTransform()
                .apply {
                    addTarget(sharedTransitionName)
                    duration = 300L
                    scrimColor = Color.TRANSPARENT
                    fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
                }
                .let {
                    sharedElementEnterTransition = it
                    sharedElementReturnTransition = it
                }
        }
        super.onCreate(savedInstanceState)
        activityBinding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(activityBinding.root)
        
        ViewCompat.setTransitionName(activityBinding.root, sharedTransitionName)
        val isPassphrase = intent.getBooleanExtra("isPassphrase", false)
        
        passwordLine = intent.getStringExtra("PwdLine")!!
        
        // Disable screenshots and screen recordings
        window.blockScreenshots(get<PreferenceManager>().getBoolean(BLOCK_SS))
        
        // Back
        activityBinding.detailsBackBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.activity_host_fragment,
                if (!isPassphrase) PasswordDetailsFragment()
                else PassphraseDetailsFragment().apply {
                    arguments =
                        Bundle().apply {
                            putParcelable(
                                "phraseDetails",
                                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("phraseDetails", GenPhraseDetails::class.java)
                                else intent.getParcelableExtra("phraseDetails")
                            )
                        }
                }
            )
            .commit()
        
    }
    
}