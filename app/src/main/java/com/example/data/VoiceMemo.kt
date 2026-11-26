package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Keep
@IgnoreExtraProperties
data class VoiceMemo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun VoiceMemo.formattedKoreanDateTime(): String {
    val displayTimestamp = createdAt ?: updatedAt ?: Timestamp.now()
    val formatter = SimpleDateFormat("yyyy년 M월 d일 (E) a h:mm", Locale.KOREA)
    return formatter.format(displayTimestamp.toDate())
}
