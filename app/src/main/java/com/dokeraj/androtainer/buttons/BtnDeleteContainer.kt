package com.dokeraj.androtainer.buttons

import android.content.Context
import android.graphics.drawable.TransitionDrawable
import android.view.View
import android.view.animation.AnimationUtils
import com.dokeraj.androtainer.R
import com.dokeraj.androtainer.databinding.DeleteDockerContainerBtnBinding

class BtnDeleteContainer(
    private val ct: Context,
    private val binding: DeleteDockerContainerBtnBinding
) {
    fun changeBtnState(enable: Boolean) {
        val fadeIn = AnimationUtils.loadAnimation(ct, R.anim.fade_in_btn)
        val colorTransition =
            binding.clContainerDelete.background as TransitionDrawable

        if (enable != binding.root.isClickable) {
            binding.root.isClickable = enable
            if (enable) {
                binding.tvContainerDelete.visibility = View.VISIBLE
                binding.pbContainerDelete.visibility = View.GONE
                binding.ivTrashContainerDelete.visibility = View.VISIBLE
                colorTransition.reverseTransition(200)
            } else {
                binding.pbContainerDelete.animation = fadeIn
                colorTransition.startTransition(200)
                binding.tvContainerDelete.visibility = View.GONE
                binding.pbContainerDelete.visibility = View.VISIBLE
                binding.ivTrashContainerDelete.visibility = View.GONE
            }
        }
    }
}