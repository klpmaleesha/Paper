// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable private fun SetupCard(content:@Composable ColumnScope.()->Unit){Surface(shape=RoundedCornerShape(26.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}}
@Composable private fun Detail(text:String){Text(text,fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable fun PresetContent(state:PaperState,apply:(String)->Unit,save:(String)->Unit,remove:(String)->Unit){
    var name by remember{mutableStateOf("")}
    Detail("Save your favourite print settings. Page ranges and copy counts stay with the current document.")
    Spacer(Modifier.height(16.dp))
    state.presets.forEach{p->
        Surface(onClick={apply(p.id)},shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().padding(bottom=9.dp),color=if(state.activePreset==p.id)MaterialTheme.colorScheme.primary.copy(alpha=.09f)else MaterialTheme.colorScheme.surface){
            Row(Modifier.padding(start=18.dp,top=12.dp,bottom=12.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Medium);Detail(p.summary)}
                if(!p.builtIn)IconButton(onClick={remove(p.id)}){Icon(Icons.Rounded.DeleteOutline,"Delete ${p.name}")}
                else RadioButton(state.activePreset==p.id,null)
            }
        }
    }
    Spacer(Modifier.height(10.dp));SetupCard{
        Text("Keep these settings",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        OutlinedTextField(name,{name=it.take(40)},label={Text("Preset name")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
        Button(onClick={save(name);name=""},enabled=name.isNotBlank()&&state.presets.count{!it.builtIn}<20,modifier=Modifier.fillMaxWidth(),shape=CircleShape){Text("Save preset")}
    }
}

@Composable fun PrinterSetupContent(state:PaperState,connect:()->Unit,scan:()->Unit,add:(String)->Unit,remove:(String)->Unit,printSettings:()->Unit,duplex:()->Unit){
    var address by remember{mutableStateOf("")}
    SetupCard{
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Rounded.Usb,null,tint=MaterialTheme.colorScheme.primary);Column{Text(state.usb.name?:PrinterProfiles.g2010.name,fontWeight=FontWeight.SemiBold,fontSize=18.sp);Detail("USB OTG · ${state.usb.compatibility.label}")}}
        Detail(if(state.usb.detected)if(state.usb.authorized)"USB access allowed. The printer identity is checked again before every job."else"Allow USB access to inspect this printer."else"Connect the printer using an OTG adapter.")
        InfoNote(if(state.usb.compatibility==Compatibility.UNSUPPORTED)"This USB printer has no compatible Paper driver yet. It will not receive a guessed print format."else PrinterProfiles.g2010.note)
        OutlinedButton(onClick=connect,modifier=Modifier.fillMaxWidth(),shape=CircleShape){Text(if(state.usb.detected&&!state.usb.authorized)"Allow USB access"else"Check USB connection")}
        TextButton(onClick=duplex,modifier=Modifier.fillMaxWidth()){Text("Set up manual two-sided printing")}
    }
    Spacer(Modifier.height(22.dp));Text("Network printers",fontSize=19.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=5.dp,bottom=12.dp))
    Detail("Connect your phone and printer to the same network. This build supports printers that advertise direct PDF printing over IPP. Other formats need another driver.")
    Spacer(Modifier.height(12.dp))
    Button(onClick=scan,enabled=!state.networkBusy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=CircleShape){Icon(Icons.Rounded.Wifi,null);Spacer(Modifier.width(9.dp));Text(if(state.networkBusy)"Looking for printers…"else"Find network printers")}
    if(state.networkBusy){Spacer(Modifier.height(10.dp));LinearProgressIndicator(Modifier.fillMaxWidth())}
    if(state.networkError!=null)Text(state.networkError,fontSize=13.sp,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(vertical=12.dp))
    state.networkPrinters.forEach{p->
        Spacer(Modifier.height(12.dp));SetupCard{
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.capabilities.name,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Detail(p.capabilities.profile.compatibility.label+" · "+if(p.uri.startsWith("ipps:"))"Encrypted connection"else"Unencrypted printer connection")};IconButton(onClick={remove(p.id)}){Icon(Icons.Rounded.DeleteOutline,"Forget ${p.capabilities.name}")}}
            Detail(p.uri)
            Detail(p.capabilities.unsupportedReason?:"${p.capabilities.paperSizes.joinToString{it.name}} · ${if(p.capabilities.profile.colour)"Colour"else"Monochrome"} · ${if(p.capabilities.profile.automaticDuplex.isNotEmpty())"Automatic two-sided available"else"One-sided"}")
            if(p.capabilities.supported)Detail("Choose this printer under Paper in Android’s Print menu. A capability check does not confirm physical output.")
        }
    }
    Spacer(Modifier.height(18.dp));SetupCard{
        Text("Add by address",fontWeight=FontWeight.Medium)
        OutlinedTextField(address,{address=it.take(512)},label={Text("Printer address")},placeholder={Text("ipp://192.168.1.50:631/ipp/print")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
        OutlinedButton(onClick={add(address)},enabled=address.isNotBlank(),modifier=Modifier.fillMaxWidth(),shape=CircleShape){Text("Check & add printer")}
    }
    Spacer(Modifier.height(16.dp));OutlinedButton(onClick=printSettings,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=CircleShape){Text("Open Android printing settings")}
    Detail("Enable Paper there to print from Chrome and other apps. Documents go directly to the printer you choose. No accounts, advertising or analytics.")
}

@Composable fun DuplexSetupContent(existing:DuplexSetup?,sample:()->Unit,save:(DuplexSetup)->Unit){
    var face by remember{mutableStateOf(existing?.face?:ReloadFace.UP)}
    var edge by remember{mutableStateOf(existing?.edge?:ReloadEdge.FIRST)}
    var reverse by remember{mutableStateOf(existing?.reverseBacks?:true)}
    var confirmed by remember{mutableStateOf(false)}
    InfoNote("For the Canon G2010, A4 portrait and long-edge binding. First confirm that a single page prints correctly.")
    Spacer(Modifier.height(16.dp));SetupCard{
        Text("Try two sheets",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        Detail("1. Mark the top edge of two blank sheets.\n\n2. Open the four-page setup document below and print using Paper’s manual two-sided guide.\n\n3. Note how you reloaded the sheets. Check that each numbered front has its matching back and both TOP arrows point to the same physical edge.")
        OutlinedButton(onClick=sample,modifier=Modifier.fillMaxWidth(),shape=CircleShape){Text("Open setup document")}
    }
    Spacer(Modifier.height(16.dp));SetupCard{
        Text("Remember the reload that worked",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        ReloadFace.entries.forEach{value->Row(Modifier.fillMaxWidth().clickable{face=value},verticalAlignment=Alignment.CenterVertically){RadioButton(face==value,{face=value});Text(value.label)}}
        HorizontalDivider()
        ReloadEdge.entries.forEach{value->Row(Modifier.fillMaxWidth().clickable{edge=value},verticalAlignment=Alignment.CenterVertically){RadioButton(edge==value,{edge=value});Text(value.label)}}
        Row(Modifier.fillMaxWidth().clickable{reverse=!reverse},verticalAlignment=Alignment.CenterVertically){Checkbox(reverse,{reverse=it});Text("Reverse back-side sheet order",Modifier.weight(1f))}
        Detail("If numbered fronts and backs do not match, change the back-side order and repeat the test. A test with only one sheet cannot check stack order.")
        Row(Modifier.fillMaxWidth().clickable{confirmed=!confirmed},verticalAlignment=Alignment.CenterVertically){Checkbox(confirmed,{confirmed=it});Text("I checked both physical sheets. The backs match and are upright.",Modifier.weight(1f),fontSize=14.sp)}
        Button(onClick={save(DuplexSetup(PrinterProfiles.g2010.id,face,edge,reverse))},enabled=confirmed,modifier=Modifier.fillMaxWidth(),shape=CircleShape){Text("Save reload instructions")}
    }
}
