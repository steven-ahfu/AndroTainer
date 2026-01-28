package com.dokeraj.androtainer

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.dokeraj.androtainer.adapter.ManageUsersAdapter
import com.dokeraj.androtainer.databinding.FragmentUsersListerBinding
import com.dokeraj.androtainer.globalvars.GlobalApp
import com.dokeraj.androtainer.models.Credential
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme

class ManageUsersListerFragment : Fragment(R.layout.fragment_users_lister) {
    private var _binding: FragmentUsersListerBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        _binding = FragmentUsersListerBinding.bind(view)

        binding.tbUsersLister.navigationIcon =
            ContextCompat.getDrawable(requireActivity(), R.drawable.ic_back)

        val globActivity: MainActiviy = (activity as MainActiviy?)!!
        val globalVars: GlobalApp = (globActivity.application as GlobalApp)

        // load the user credentials into the drawer recyclerview
        val savedUsers: List<Credential> = globalVars.credentials.map { (_, v) -> v }
        val curLoggedUserKey =
            "${globalVars.currentUser!!.serverUrl}.${globalVars.currentUser!!.username}"

        val recyclerAdapter =
            ManageUsersAdapter(savedUsers,
                curLoggedUserKey,
                globActivity,
                requireContext(),
                parentFragmentManager)
        binding.rvManageUsers.adapter = recyclerAdapter
        binding.rvManageUsers.layoutManager = LinearLayoutManager(activity)
        binding.rvManageUsers.setHasFixedSize(true)

        setNoteText()

        // on back pressed set the global var that the swiperRefresh should be turned on
        binding.tbUsersLister.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setNoteText() {
        val markwon = Markwon.builder(requireContext())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(ContextCompat.getColor(requireContext(), R.color.dis6))
                }
            })
            .build()

        markwon.setMarkdown(binding.tvUsersListerNote, getString(R.string.users_manage_note))
    }
}