package com.voiceassistant

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class VoicePickerSheet(
    private val tts: TextToSpeech,
    private val currentVoice: Voice?,
    private val onPick: (Voice) -> Unit,
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_voices, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvVoices)
        rv.layoutManager = LinearLayoutManager(requireContext())

        // Собираем голоса: предпочтительно русские, затем все остальные
        val voices = tts.voices
            ?.filter { !it.isNetworkConnectionRequired }
            ?.sortedWith(
                compareByDescending<Voice> { it.locale.language == "ru" }
                    .thenBy { it.name }
            ) ?: emptyList()

        rv.adapter = VoiceAdapter(voices, currentVoice) { voice ->
            onPick(voice)
            dismiss()
        }
    }

    private class VoiceAdapter(
        private val items: List<Voice>,
        private val current: Voice?,
        private val onClick: (Voice) -> Unit,
    ) : RecyclerView.Adapter<VoiceAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvVoiceName)
            val ivCheck: ImageView = view.findViewById(R.id.ivCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_voice, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val voice = items[position]
            val lang = voice.locale.displayLanguage
            val country = voice.locale.displayCountry
            holder.tvName.text = "$lang ($country) — ${voice.name}"
            holder.ivCheck.visibility =
                if (voice.name == current?.name) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onClick(voice) }
        }
    }
}
