package com.dokeraj.androtainer.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.dokeraj.androtainer.MainActiviy
import com.dokeraj.androtainer.databinding.FragmentThresholdDialogBinding
import com.dokeraj.androtainer.globalvars.GlobalApp
import com.dokeraj.androtainer.globalvars.MonitorStore
import com.dokeraj.androtainer.models.ContainerThreshold
import java.util.Locale

/** Per-container CPU/memory alert thresholds + the global poll interval. */
class ThresholdDialog(private val containerName: String) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentThresholdDialogBinding.inflate(inflater, container, false)

        val globActivity = requireActivity() as MainActiviy
        val globalVars = globActivity.application as GlobalApp

        binding.tvThresholdTitle.text =
            String.format(Locale.getDefault(), "Alerts for %s", containerName)

        // seed from persisted values
        val existing = MonitorStore.readThresholds(requireContext())
            .firstOrNull { it.containerName == containerName }
        existing?.cpuPct?.let { binding.etCpuThreshold.setText(trimNumber(it)) }
        existing?.memMib?.let { binding.etMemThreshold.setText(trimNumber(it)) }

        val currentInterval = globalVars.appSettings?.monitorIntervalMinutes ?: 15
        binding.actvMonitorInterval.setText(
            String.format(Locale.US, "%d min", currentInterval), false)

        binding.btnThresholdCancel.setOnClickListener { dismiss() }

        binding.btnThresholdDelete.setOnClickListener {
            val remaining = MonitorStore.readThresholds(requireContext())
                .filterNot { it.containerName == containerName }
            MonitorStore.writeThresholds(requireContext(), remaining)
            globActivity.syncMonitorWork()
            dismiss()
        }

        binding.btnThresholdSave.setOnClickListener {
            val cpu = binding.etCpuThreshold.text?.toString()?.toDoubleOrNull()
            val mem = binding.etMemThreshold.text?.toString()?.toDoubleOrNull()

            val others = MonitorStore.readThresholds(requireContext())
                .filterNot { it.containerName == containerName }
            val updated = if (cpu != null || mem != null)
                others + ContainerThreshold(containerName, cpuPct = cpu, memMib = mem)
            else
                others // both blank = same as remove
            MonitorStore.writeThresholds(requireContext(), updated)

            val pickedInterval = binding.actvMonitorInterval.text?.toString()
                ?.takeWhile { c -> c.isDigit() }?.toIntOrNull() ?: currentInterval
            globActivity.setGlobalAppSettings(monitorIntervalMinutes = pickedInterval)

            globActivity.syncMonitorWork()
            globActivity.requestNotificationPermissionIfNeeded()
            dismiss()
        }

        return binding.root
    }

    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else value.toString()
}
