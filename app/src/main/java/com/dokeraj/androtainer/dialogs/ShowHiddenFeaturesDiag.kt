package com.dokeraj.androtainer.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.dokeraj.androtainer.R
import com.dokeraj.androtainer.databinding.FragmentHiddenFeaturesDialogBinding
import io.noties.markwon.Markwon

class ShowHiddenFeaturesDiag() : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentHiddenFeaturesDialogBinding.inflate(
            inflater,
            container,
            false
        )

        val markwon = Markwon.builder(requireContext()).build()
        markwon.setMarkdown(
            binding.tvDiagExplanation,
            getString(R.string.hiddenExplanations)
        )

        binding.clCancel.setOnClickListener {
            dismiss()
        }

        return binding.root
    }
}