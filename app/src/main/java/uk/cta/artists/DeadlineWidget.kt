package uk.cta.artists

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.provideContent
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class DeadlineWidget:GlanceAppWidgetReceiver() {
 override val glanceAppWidget:GlanceAppWidget=DeadlineGlanceWidget()
}
class DeadlineGlanceWidget:GlanceAppWidget() {
 override suspend fun provideGlance(context:Context,id:GlanceId) {
  val arr=org.json.JSONArray(context.getSharedPreferences("cta",0).getString("opportunities","[]"))
  val items=(0 until arr.length()).mapNotNull { i ->
   runCatching {
    val o=arr.getJSONObject(i)
    val days=ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(o.getString("deadline")))
    o.getString("title") to days
   }.getOrNull()
  }.filter {it.second>=0}.sortedBy {it.second}.take(3)
  provideContent {
   Column(modifier=GlanceModifier.padding(12.dp)) {
    Text("CALL TO ARTISTS")
    if(items.isEmpty()) Text("No upcoming deadlines")
    items.forEach {Text("${it.second}d · ${it.first.take(24)}")}
   }
  }
 }
}