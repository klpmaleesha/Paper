package app.paper.print

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable fun UsbJobDialog(job:UsbJobState){
    if(job.title.isEmpty())return
    val clipboard=LocalClipboardManager.current
    AlertDialog(onDismissRequest={if(!job.active)UsbJobs.dismiss()},title={Text(job.title)},text={
        Column(verticalArrangement=Arrangement.spacedBy(17.dp)){
            Text(job.message)
            if(job.active)LinearProgressIndicator(progress={job.progress},modifier=Modifier.fillMaxWidth())
            if(job.attention)Text("Keep the USB cable connected. The job will wait while you follow the printer’s instructions.",color=MaterialTheme.colorScheme.primary)
        }
    },confirmButton={if(job.active)TextButton(onClick=UsbJobs::cancel){Text("Cancel printing")}else TextButton(onClick=UsbJobs::dismiss){Text("Done")}},dismissButton={if(!job.active)TextButton(onClick={clipboard.setText(AnnotatedString(UsbJobs.report))}){Text("Copy test report")}})
}
