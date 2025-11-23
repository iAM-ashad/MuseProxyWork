package com.iamashad.musesample.screens.metadata

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iamashad.musesample.generatePcgPdf
import com.iamashad.musesample.model.PcgReportMeta
import com.iamashad.musesample.model.Session
import com.iamashad.musesample.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Metadata screen.
 *
 * Responsibilities:
 * - Accept a Session (new or existing-without-metadata).
 * - Generate the PCG PDF for that session.
 * - Upsert the Session row in the encrypted DB with pdfPath populated.
 */
class MetadataViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    fun saveSessionAndGeneratePdf(
        session: Session,
        onFinished: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // 1) Save / update the session in DB (metadata)
                SessionRepository.add(session)

                // 2) Build meta for PDF
                val meta = PcgReportMeta(
                    patientName = session.patientName,
                    patientId = session.patientId,
                    age = session.age,
                    sex = session.sex,
                    height = session.height,
                    weight = session.weight,
                    bmi = session.bmi,
                    posture = session.posture,
                    position = session.position,
                    sessionStart = session.sessionStart,
                    deviceModel = session.deviceModel,
                    notes = session.notes
                )

                // 3) Generate PDF
                val ctx = getApplication<Application>()
                val pdfFile = generatePcgPdf(
                    context = ctx,
                    filteredWavPath = session.wavPath,
                    rawWavPath = session.rawWavPath,   // may be null / empty
                    meta = meta
                )

                // 4) Attach PDF path to the existing session row
                SessionRepository.updatePdf(
                    id = session.id,
                    pdfPath = pdfFile.absolutePath
                )

                // 5) Notify caller after everything is done
                withContext(Dispatchers.Main) { onFinished() }
            } catch (t: Throwable) {
                t.printStackTrace()
                // TODO: surface error to UI if needed
            } finally {
                _isSaving.value = false
            }
        }
    }
}
