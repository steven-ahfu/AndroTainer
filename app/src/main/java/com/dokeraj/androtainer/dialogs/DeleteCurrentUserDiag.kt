package com.dokeraj.androtainer.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.dokeraj.androtainer.MainActiviy
import com.dokeraj.androtainer.ManageUsersListerFragmentDirections
import com.dokeraj.androtainer.R
import com.dokeraj.androtainer.databinding.FragmentDeleteCurrentUserDialogBinding
import com.dokeraj.androtainer.models.Credential
import io.noties.markwon.Markwon

class DeleteCurrentUserDiag(
    val selectedUser: Credential,
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentDeleteCurrentUserDialogBinding.inflate(
            inflater,
            container,
            false
        )

        val mainActivity: MainActiviy = (activity as MainActiviy?)!!

        val markwon = Markwon.builder(requireContext()).build()
        markwon.setMarkdown(
            binding.tvDiagExplanation,
            getString(R.string.dialogWarning)
        )

        binding.btnDeleteUserCancel.setOnClickListener {
            dismiss()
        }

        binding.btnDeleteUserContinue.setOnClickListener {
            mainActivity.deleteUser(selectedUser)
            mainActivity.setLogoutMsg("Logged in user `${selectedUser.username}` was deleted")

            dismiss()
            val action =
                ManageUsersListerFragmentDirections.actionUsersListerFragmentToHomeFragment()
            findNavController().navigate(action)
        }

        return binding.root
    }
}