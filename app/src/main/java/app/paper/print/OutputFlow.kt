package app.paper.print

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

enum class OutputTarget { USB, ANDROID, EXPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OutputFlow(document:LocalDocument,settings:PrintSettings,onClose:()->Unit,prepare:(List<SheetSide>,PrintSettings,OutputTarget,String)->Unit,busy:Boolean=false,initialStep:Int=0,cancel:()->Unit={},usb:UsbStatus=UsbStatus(),connect:()->Unit={},duplexSetup:DuplexSetup?=null,hasNetwork:Boolean=false){
    // Snapshot settings for both passes. A change to the main form cannot alter half of a two-sided job.
    val s=remember{settings};val doc=remember{document};val plan=remember{PrintPlan.build(s,doc.pages)}
    var step by rememberSaveable{mutableIntStateOf(initialStep)}
    var startedFront by rememberSaveable{mutableStateOf(false)}
    var startedBack by rememberSaveable{mutableStateOf(false)}
    var frontTarget by rememberSaveable{mutableStateOf<OutputTarget?>(null)}
    var confirmed by rememberSaveable{mutableStateOf(false)}
    var closePrompt by remember{mutableStateOf(false)}
    val close={if(s.duplex&&startedFront&&!startedBack)closePrompt=true else onClose()}
    ModalBottomSheet(onDismissRequest={if(!busy)close()},containerColor=MaterialTheme.colorScheme.background,shape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp),dragHandle={BottomSheetDefaults.DragHandle()}){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=23.dp).navigationBarsPadding().padding(bottom=25.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            if(s.duplex){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){(0..2).forEach{i->Box(Modifier.weight(1f).height(4.dp).background(if(i<=step)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,CircleShape))}}
                Text("${step+1} of 3 · Two-sided printing",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if(!s.duplex)"Ready to print?"else when(step){0->"Start with the fronts";1->"Turn. Reload. Continue.";else->"Now, the other side"},fontSize=29.sp,fontWeight=FontWeight.Bold,lineHeight=35.sp)
            when {
                !s.duplex || step==0 -> {
                    Surface(shape=RoundedCornerShape(23.dp)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                        Text(doc.name,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text("${plan.selected} selected pages · ${plan.sheets} ${if(plan.sheets==1)"sheet"else"sheets"}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${if(s.color)"Colour"else"Black & white"} · ${s.paper.name} · ${s.copies} ${if(s.copies==1)"copy"else"copies"}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }}
                    InfoNote(if(usb.compatibility==Compatibility.UNSUPPORTED)"This USB printer is unsupported. Use a compatible network printer through Android’s Print menu, or save a PDF."else"USB printing uses an experimental Canon raster driver. Start with one test page. ${if(!usb.detected)"Connect your printer with an OTG adapter."else if(!usb.authorized)"Allow USB access before printing."else"Check that the correct paper is loaded."}")
                    if(s.duplex)Text("Paper will prepare ${plan.front.size} front sides first. Use plain paper. Start with a two-page test to check how your printer feeds and stacks sheets.",fontSize=14.sp,lineHeight=23.sp)
                    Text("Copies are already included. Leave copies at 1 and two-sided printing off in the Android print dialog. Keep the paper size and orientation shown here.",fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick={prepare(plan.front,s,OutputTarget.USB,if(s.duplex)" - fronts"else" - prepared");startedFront=true;frontTarget=OutputTarget.USB},enabled=!busy&&usb.canPrint,modifier=Modifier.fillMaxWidth().heightIn(min=55.dp),shape=CircleShape){Icon(Icons.Rounded.Usb,null,Modifier.size(20.dp));Spacer(Modifier.width(9.dp));Text(if(s.duplex)"Print front sides via USB"else"Print via USB",fontSize=15.sp,fontWeight=FontWeight.SemiBold)}
                    if(!usb.detected||!usb.authorized)TextButton(onClick=connect,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(usb.detected)"Allow USB access"else"Check USB connection")}
                    OutlinedButton(onClick={prepare(plan.front,s,OutputTarget.EXPORT,if(s.duplex)" - fronts"else" - prepared");startedFront=true;frontTarget=OutputTarget.EXPORT},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=CircleShape){Text(if(s.duplex)"Save front sides as PDF"else"Save prepared PDF")}
                    TextButton(onClick={prepare(plan.front,s,OutputTarget.ANDROID,if(s.duplex)" - fronts"else" - prepared");startedFront=true;frontTarget=OutputTarget.ANDROID},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(hasNetwork)"Choose a network printer in Android"else"Use Android print service")}
                    if(s.photoPaper&&hasNetwork)InfoNote("Paper’s network service uses the printer’s configured media type and tray. Photo-paper and specialised quality controls are not included in this network backend yet.")
                    if(s.duplex&&startedFront)TextButton(onClick={step=1},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("My front sides are printed",fontWeight=FontWeight.SemiBold)}
                }
                step==1 -> {
                    Row(Modifier.fillMaxWidth().padding(vertical=7.dp),horizontalArrangement=Arrangement.Center){BindingDrawing(s.sides==Sides.SHORT)}
                    Text(if(s.sides==Sides.LONG)"Turn the sheets like a book, along the long edge."else"Turn the sheets like a notepad, along the short edge.",fontSize=17.sp,fontWeight=FontWeight.Medium,lineHeight=25.sp)
                    Text("1. Wait until every front side is physically printed.\n\n2. Let the ink dry. Keep the sheets in a single stack.\n\n3. Reload so the blank side will be printed. Use the edge and stack order confirmed by your setup test.",fontSize=14.sp,lineHeight=23.sp)
                    if(frontTarget==OutputTarget.USB&&duplexSetup!=null&&s.sides==Sides.LONG&&!s.landscape&&s.paper==PaperSize.A4)InfoNote(
                        "Your saved G2010 reload: ${duplexSetup.face.label}. ${duplexSetup.edge.label}."+
                            if(s.reverseBacks!=duplexSetup.reverseBacks)" Back-side order has changed since setup; repeat the two-sheet test before a large job."else" ${if(s.reverseBacks)"Back-side order is reversed."else"Back-side order is forward."}")
                    InfoNote("${if(s.reverseBacks)"Backs are prepared in reverse order."else"Backs are prepared in forward order."} Paper keeps a blank back for an odd final page so your copies stay paired. Change stack order in More settings before starting a new job if your test needs it.")
                    Row(Modifier.fillMaxWidth().clickable{confirmed=!confirmed}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(confirmed,{confirmed=it});Text("The fronts are printed, dry, and reloaded with the blank side ready.",Modifier.weight(1f),fontSize=14.sp,lineHeight=21.sp)}
                    Button(onClick={step=2},enabled=confirmed&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=55.dp),shape=CircleShape){Text("Continue to back sides",fontWeight=FontWeight.SemiBold)}
                }
                else -> {
                    InfoNote("${plan.back.size} back sides · ${s.sides.label} binding. Keep copies at 1 and two-sided printing off in Android’s print dialog. Keep blank pages: they preserve sheet pairing.")
                    Button(onClick={prepare(plan.back,s,OutputTarget.USB," - backs");startedBack=true},enabled=!busy&&usb.canPrint,modifier=Modifier.fillMaxWidth().heightIn(min=55.dp),shape=CircleShape){Icon(Icons.Rounded.Usb,null,Modifier.size(20.dp));Spacer(Modifier.width(9.dp));Text("Print back sides via USB",fontWeight=FontWeight.SemiBold)}
                    if(!usb.detected||!usb.authorized)TextButton(onClick=connect,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Check USB access")}
                    OutlinedButton(onClick={prepare(plan.back,s,OutputTarget.EXPORT," - backs");startedBack=true},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=CircleShape){Text("Save back sides as PDF")}
                    TextButton(onClick={prepare(plan.back,s,OutputTarget.ANDROID," - backs");startedBack=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Use Android print service")}
                    if(startedBack)Text("Check the printed sheets before closing. Paper cannot verify physical output through the Android print dialog.",fontSize=13.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick=onClose,enabled=!busy,modifier=Modifier.fillMaxWidth().height(48.dp)){Text("Close two-sided guide")}
                }
            }
            if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Preparing your document…",fontSize=13.sp);TextButton(onClick=cancel){Text("Cancel preparation")}}
            if(step>0)TextButton(onClick={step--;confirmed=false},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Back")}
        }
    }
    if(closePrompt)AlertDialog(onDismissRequest={closePrompt=false},title={Text("Close the two-sided guide?")},text={Text("Keep your printed fronts together. Closing this guide will reset the pass sequence; it will not cancel jobs already sent through Android.")},confirmButton={TextButton(onClick=onClose){Text("Close guide")}},dismissButton={TextButton(onClick={closePrompt=false}){Text("Keep printing")}})
}
@Composable private fun BindingDrawing(short:Boolean){
    val ink=MaterialTheme.colorScheme.primary
    Box(Modifier.size(170.dp,125.dp),contentAlignment=Alignment.Center){
        Surface(color=ink.copy(alpha=.09f),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxSize()){}
        Icon(if(short)Icons.Rounded.FlipToBack else Icons.Rounded.AutoStories,null,Modifier.size(65.dp),tint=ink)
    }
}
