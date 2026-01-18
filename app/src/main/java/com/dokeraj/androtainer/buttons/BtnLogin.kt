package com.dokeraj.androtainer.buttons

import android.content.Context
import android.graphics.drawable.TransitionDrawable
import android.view.View
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import com.dokeraj.androtainer.R
import com.dokeraj.androtainer.databinding.LoginBtnBinding

class BtnLogin(
    private val ct: Context,
    private val binding: LoginBtnBinding
) {
    fun changeBtnState(enable: Boolean) {
        val fadeIn = AnimationUtils.loadAnimation(ct, R.anim.fade_in_btn)
        val colorTransition =
            binding.clLogin.background as TransitionDrawable

        if (enable != binding.root.isClickable) {
            binding.root.isClickable = enable
            if (enable) {
                binding.tvLogin.setTextColor(ContextCompat.getColor(ct, R.color.dis4))
                binding.tvLogin.visibility = View.VISIBLE
                binding.pbLogin.visibility = View.GONE
                colorTransition.reverseTransition(200)
            } else {
                binding.pbLogin.animation = fadeIn
                colorTransition.startTransition(200)
                binding.tvLogin.visibility = View.GONE
                binding.tvLogin.setTextColor(ContextCompat.getColor(ct, R.color.dis6))
                binding.pbLogin.visibility = View.VISIBLE
            }
        }
    }
}