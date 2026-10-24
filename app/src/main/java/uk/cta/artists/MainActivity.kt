package uk.cta.artists

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Opportunity(val title:String,val url:String,val deadline:String)
object Store {
 private const val KEY="opportunities"
 fun read(a:Activity):List<Opportunity> = runCatching {
  val arr=JSONArray(a.getSharedPreferences("cta",0).getString(KEY,"[]"))
  (0 until arr.length()).map { val o=arr.getJSONObject(it); Opportunity(o.getString("title"),o.getString("url"),o.getString("deadline")) }
 }.getOrDefault(emptyList())
 fun write(a:Activity,items:List<Opportunity>) {
  val arr=JSONArray()
  items.forEach { arr.put(JSONObject().put("title",it.title).put("url",it.url).put("deadline",it.deadline)) }
  a.getSharedPreferences("cta",0).edit().putString(KEY,arr.toString()).apply()
 }
}
class MainActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val shared=if(intent?.action==Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
  setContent { MaterialTheme { Screen(this,shared) } }
 }
}
@Composable
fun Screen(activity:Activity,shared:String) {
 var records by remember { mutableStateOf(Store.read(activity)) }
 var url by remember { mutableStateOf(Regex("https?://\\S+").find(shared)?.value ?: "") }
 var title by remember { mutableStateOf("") }
 var deadline by remember { mutableStateOf("") }
 var message by remember { mutableStateOf("Paste a public exhibition URL, inspect the page, then enter its verified deadline.") }
 var busy by remember { mutableStateOf(false) }
 val scope=rememberCoroutineScope()
 Scaffold(topBar={ Surface(tonalElevation=3.dp) { Text("CALL TO ARTISTS",Modifier.fillMaxWidth().padding(20.dp),style=MaterialTheme.typography.titleLarge) } }) { padding ->
  Column(Modifier.padding(padding).padding(16.dp)) {
   OutlinedTextField(url,{url=it},label={Text("Opportunity URL")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   Spacer(Modifier.height(8.dp))
   Button(enabled=!busy && url.startsWith("https://"),onClick={
    busy=true
    scope.launch {
     val result=withContext(Dispatchers.IO) {
      runCatching {
       val doc=Jsoup.connect(url).userAgent("CTA/0.1 (personal opportunity reader)").timeout(12000).get()
       (doc.selectFirst("h1")?.text().orEmpty().ifBlank {doc.title()}) to doc.body().text().take(1200)
      }
     }
     result.onSuccess { (heading,excerpt)->title=heading;message="Check the official deadline before saving.\n"+excerpt }
      .onFailure { message="Could not retrieve page: ${it.message}. Enter details manually." }
     busy=false
    }
   }) { Text(if(busy)"Reading..." else "Read page") }
   OutlinedTextField(title,{title=it},label={Text("Exhibition title")},modifier=Modifier.fillMaxWidth())
   OutlinedTextField(deadline,{deadline=it},label={Text("Deadline YYYY-MM-DD")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   Button(onClick={
    if(title.isBlank() || url.isBlank() || runCatching {LocalDate.parse(deadline)}.isFailure) message="Enter title, URL and valid YYYY-MM-DD date."
    else {
     records=(records.filterNot {it.url==url}+Opportunity(title,url,deadline)).sortedBy {it.deadline}
     Store.write(activity,records)
     message="Saved. User-entered date; not independently verified."
    }
   }) { Text("Save opportunity") }
   Text(message,style=MaterialTheme.typography.bodySmall)
   Spacer(Modifier.height(14.dp))
   Text("Saved deadlines",style=MaterialTheme.typography.titleMedium)
   LazyColumn {
    items(records,key={it.url}) { record ->
     val days=runCatching {ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(record.deadline))}.getOrDefault(0)
     Card(Modifier.fillMaxWidth().padding(vertical=5.dp)) {
      Column(Modifier.padding(12.dp)) {
       Text(record.title,style=MaterialTheme.typography.titleMedium)
       Text("${record.deadline} · ${days} days remaining · User-entered")
       Row {
        TextButton(onClick={
         val millis=LocalDate.parse(record.deadline).atTime(12,0).atZone(ZoneId.of("Europe/London")).toInstant().toEpochMilli()
         val i=Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
          .putExtra(CalendarContract.Events.TITLE,"Submission deadline: ${record.title}")
          .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,millis)
          .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,millis+3600000)
          .putExtra(CalendarContract.Events.DESCRIPTION,record.url+"\nVerify the deadline on the official website.")
         activity.startActivity(i)
        }) {Text("Add to calendar")}
        TextButton(onClick={records=records.filterNot {it.url==record.url};Store.write(activity,records)}) {Text("Delete")}
       }
      }
     }
    }
   }
  }
 }
}