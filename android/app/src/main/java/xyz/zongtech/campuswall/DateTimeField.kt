@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package xyz.zongtech.campuswall

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun DateTimeField(label:String,value:String,onChange:(String)->Unit) {
    var calendar by remember{mutableStateOf(false)};var clock by remember{mutableStateOf(false)}
    val current=runCatching{OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Shanghai"))}.getOrElse{ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))}
    val date=rememberDatePickerState(initialSelectedDateMillis=current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val time=rememberTimePickerState(current.hour,current.minute,true)
    OutlinedTextField(if(value.isBlank())"未设置"else current.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")),{},Modifier.fillMaxWidth(),label={Text(label)},readOnly=true,trailingIcon={IconButton(onClick={calendar=true}){Icon(Icons.Default.DateRange,"选择日期和时间")}})
    if(value.isNotBlank())TextButton(onClick={onChange("")}){Text("清除时间")}
    if(calendar)DatePickerDialog(onDismissRequest={calendar=false},confirmButton={TextButton(onClick={calendar=false;clock=true},enabled=date.selectedDateMillis!=null){Text("下一步")}},dismissButton={TextButton(onClick={calendar=false}){Text("取消")}}){DatePicker(date)}
    if(clock)AlertDialog(onDismissRequest={clock=false},title={Text("选择时间")},text={TimePicker(time)},confirmButton={TextButton(onClick={val day=Instant.ofEpochMilli(date.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate();onChange(day.atTime(time.hour,time.minute).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString());clock=false}){Text("确定")}},dismissButton={TextButton(onClick={clock=false}){Text("取消")}})
}
