package app.paper.print

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import java.io.File

private val Blue=Color(0xFF2457D6)
private val PageShape=RoundedCornerShape(26.dp)
@Composable private fun OneSwitch(checked:Boolean,onCheckedChange:(Boolean)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){
    val left by animateDpAsState(if(checked)21.dp else 3.dp,label="Switch position")
    val track=if(checked)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(modifier.size(52.dp,48.dp).toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=onCheckedChange),contentAlignment=Alignment.Center){
        Box(Modifier.size(46.dp,27.dp).background(track.copy(alpha=if(enabled)1f else .35f),CircleShape)){
            Box(Modifier.padding(top=3.dp).offset(x=left).size(21.dp).background(if(checked)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface,CircleShape))
        }
    }
}
@Composable fun PaperTheme(dark:Boolean=false,content:@Composable () -> Unit){
    val scheme=if(dark)darkColorScheme(primary=Color(0xFFAFC6FF),onPrimary=Color(0xFF0B2772),background=Color(0xFF101114),surface=Color(0xFF1D1E23),onSurface=Color(0xFFF4F4F7),onSurfaceVariant=Color(0xFFB5B7C1),surfaceVariant=Color(0xFF292B33),outlineVariant=Color(0xFF353740))
    else lightColorScheme(primary=Blue,onPrimary=Color.White,background=Color(0xFFF3F4F7),surface=Color.White,onSurface=Color(0xFF17181D),onSurfaceVariant=Color(0xFF656874),surfaceVariant=Color(0xFFF0F2F7),outlineVariant=Color(0xFFE8E9EE))
    MaterialTheme(colorScheme=scheme,typography=Typography(),content=content)
}
@Composable fun PaperLogo(size:Int=36){
    Canvas(Modifier.size(size.dp).semantics{contentDescription="Paper app icon"}) {
        val scale=this.size.width/108f;val w=this.size.width;val h=this.size.height
        drawRoundRect(Blue,cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.26f))
        val p=Path().apply{moveTo(w*.32f,h*.22f);lineTo(w*.60f,h*.22f);lineTo(w*.74f,h*.36f);lineTo(w*.74f,h*.75f);quadraticBezierTo(w*.74f,h*.80f,w*.68f,h*.80f);lineTo(w*.34f,h*.80f);quadraticBezierTo(w*.28f,h*.80f,w*.28f,h*.74f);lineTo(w*.28f,h*.28f);quadraticBezierTo(w*.28f,h*.22f,w*.32f,h*.22f);close()}
        drawPath(p,Color.White)
        drawPath(Path().apply{moveTo(w*.60f,h*.22f);lineTo(w*.60f,h*.36f);lineTo(w*.74f,h*.36f);close()},Color(0xFFB9CEFF))
        for((i,length)in listOf(.24f,.24f,.15f).withIndex())drawLine(Blue,androidx.compose.ui.geometry.Offset(w*.40f,h*(.49f+i*.095f)),androidx.compose.ui.geometry.Offset(w*(.40f+length),h*(.49f+i*.095f)),strokeWidth=3.8f*scale,cap=StrokeCap.Round)
    }
}
@Composable private fun Group(content:@Composable ColumnScope.()->Unit){Surface(shape=PageShape,color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){Column(content=content)}}
@Composable private fun Separator(){HorizontalDivider(Modifier.padding(horizontal=22.dp),color=MaterialTheme.colorScheme.outlineVariant,thickness=.7.dp)}
@Composable private fun SectionLabel(text:String){Text(text,Modifier.padding(start=22.dp,top=23.dp,bottom=10.dp),fontSize=14.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun RowScope.CopyStepper(value:Int,change:(Int)->Unit){
    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.surfaceVariant){Row(verticalAlignment=Alignment.CenterVertically){
        IconButton(onClick={change(value-1)},enabled=value>1){Icon(Icons.Rounded.Remove,"Fewer copies",Modifier.size(19.dp))}
        Text("$value",Modifier.widthIn(min=25.dp).semantics{contentDescription="$value copies"},fontSize=17.sp,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick={change(value+1)},enabled=value<99){Icon(Icons.Rounded.Add,"More copies",Modifier.size(19.dp))}
    }}
}
@Composable private fun SettingsRow(label:String,value:String="",detail:String?=null,icon:ImageVector?=null,click:(()->Unit)?=null,tail:(@Composable RowScope.()->Unit)?=null){
    Row(Modifier.fillMaxWidth().then(if(click!=null)Modifier.clickable(onClick=click)else Modifier).heightIn(min=68.dp).padding(horizontal=22.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        if(icon!=null){Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(14.dp))}
        Column(Modifier.weight(1f)){Text(label,fontSize=16.sp,fontWeight=FontWeight.Normal);if(detail!=null)Text(detail,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))}
        if(tail!=null)tail() else {Text(value,color=MaterialTheme.colorScheme.primary,fontSize=14.sp,modifier=Modifier.widthIn(max=160.dp),maxLines=2);if(click!=null){Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Rounded.NavigateNext,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}}
    }
}
@Composable private fun ChoicePill(label:String,selected:Boolean,modifier:Modifier=Modifier,icon:ImageVector?=null,onClick:()->Unit){
    Surface(onClick=onClick,modifier=modifier.heightIn(min=50.dp).semantics{this.selected=selected;role=Role.RadioButton},shape=CircleShape,
        color=if(selected)MaterialTheme.colorScheme.primary.copy(alpha=.11f)else MaterialTheme.colorScheme.surfaceVariant,
        border=if(selected)BorderStroke(1.4.dp,MaterialTheme.colorScheme.primary)else null){
        Row(Modifier.padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
            if(icon!=null){Icon(icon,null,Modifier.size(18.dp),tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(8.dp))}
            Text(label,fontSize=14.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
@Composable private fun BindingIcon(short:Boolean,selected:Boolean){
    val color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(25.dp,29.dp)){
        val pad=3.dp.toPx();drawRoundRect(color,topLeft=androidx.compose.ui.geometry.Offset(pad,pad),size=androidx.compose.ui.geometry.Size(size.width-pad*2,size.height-pad*2),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),style=Stroke(1.4.dp.toPx()))
        val from=if(short)androidx.compose.ui.geometry.Offset(pad+4.dp.toPx(),pad+5.dp.toPx())else androidx.compose.ui.geometry.Offset(pad+5.dp.toPx(),pad+4.dp.toPx())
        val to=if(short)androidx.compose.ui.geometry.Offset(size.width-pad-4.dp.toPx(),pad+5.dp.toPx())else androidx.compose.ui.geometry.Offset(pad+5.dp.toPx(),size.height-pad-4.dp.toPx())
        drawLine(color,from,to,2.4.dp.toPx(),StrokeCap.Round)
    }
}
@Composable fun DuplexControl(s:PrintSettings,change:(PrintSettings)->Unit){
    Group {
        SettingsRow("Print on both sides",detail="Turn and reload the paper yourself",tail={OneSwitch(checked=s.duplex,onCheckedChange={change(s.copy(sides=if(it)Sides.LONG else Sides.ONE,photoPaper=if(it)false else s.photoPaper,reverse=if(it)false else s.reverse))},modifier=Modifier.semantics{contentDescription="Print on both sides"})})
        if(s.duplex){
            Row(Modifier.padding(start=20.dp,end=20.dp,bottom=19.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Sides.entries.filter{it!=Sides.ONE}.forEach{side->
                    val selected=s.sides==side
                    Surface(onClick={change(s.copy(sides=side))},modifier=Modifier.weight(1f).semantics{this.selected=selected;role=Role.RadioButton},shape=RoundedCornerShape(19.dp),color=if(selected)MaterialTheme.colorScheme.primary.copy(alpha=.09f)else MaterialTheme.colorScheme.surfaceVariant,border=if(selected)BorderStroke(1.4.dp,MaterialTheme.colorScheme.primary)else null){
                        Row(Modifier.padding(horizontal=13.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            BindingIcon(side==Sides.SHORT,selected)
                            Column{Text(side.label,fontSize=14.sp,fontWeight=FontWeight.Medium);Text(if(side==Sides.LONG)"Like a book"else"Like a notepad",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                    }
                }
            }
        }
    }
}
@Composable fun SamplePage(){
    Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){PaperLogo(16);Spacer(Modifier.width(7.dp));Text("PAPER / PRINT TEST",fontSize=5.sp,color=Color(0xFF555D6C))}
        Spacer(Modifier.height(15.dp));Text("A little more clarity.",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Color(0xFF1F293A))
        Spacer(Modifier.height(5.dp));Text("A simple document. A comfortable printing experience.",fontSize=4.sp,color=Color.DarkGray)
        Spacer(Modifier.height(12.dp));Column(Modifier.fillMaxWidth().background(Color(0xFFEBF0FB),RoundedCornerShape(5.dp)).padding(8.dp)){
            Text("Make it yours",fontSize=7.sp,color=Blue,fontWeight=FontWeight.Medium);Spacer(Modifier.height(5.dp))
            listOf("Colour or black & white. Your choice.","One side, or both. With a little guidance.","The pages you need. Nothing extra.").forEach{Text(it,fontSize=4.sp,color=Color(0xFF555D6C),modifier=Modifier.padding(vertical=3.dp))}
        }
        Spacer(Modifier.height(12.dp));Text("A quick check before you print",fontSize=6.sp,fontWeight=FontWeight.Medium,color=Color.DarkGray)
        repeat(4){Spacer(Modifier.height(6.dp));Box(Modifier.fillMaxWidth(if(it%2==0).9f else .7f).height(2.dp).background(Color(0xFFD6D9DE)))}
        Spacer(Modifier.weight(1f));Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(Blue,Color.Cyan,Color.Magenta,Color.Yellow,Color.Black).forEach{Box(Modifier.size(5.dp).background(it,CircleShape))}}
        Spacer(Modifier.height(14.dp));Text("Paper · Sample document                                     1 / 4",fontSize=4.sp,color=Color.Gray)
    }
}
@Composable private fun PreviewCard(state:PaperState,sampleRender:Boolean,open:()->Unit,turn:(Int)->Unit,zoom:()->Unit){
    val doc=state.document
    Group {
        if(doc==null){
            Column(Modifier.fillMaxWidth().padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Box(Modifier.size(78.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=.07f),RoundedCornerShape(25.dp)),contentAlignment=Alignment.Center){Icon(Icons.AutoMirrored.Rounded.NoteAdd,null,Modifier.size(37.dp),tint=MaterialTheme.colorScheme.primary)}
                Spacer(Modifier.height(19.dp));Text("What are we printing?",fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                Text("Choose a PDF, photo or text file.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=7.dp))
                TextButton(onClick=open,modifier=Modifier.padding(top=9.dp)){Text("Browse files",fontWeight=FontWeight.SemiBold)}
            }
        } else {
            Box(Modifier.fillMaxWidth().height(253.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.65f)),contentAlignment=Alignment.Center){
                Box(Modifier.padding(vertical=19.dp).aspectRatio(state.settings.width.toFloat()/state.settings.height).clip(RoundedCornerShape(2.dp)).background(Color.White).clickable(onClick=zoom)){
                    if(state.preview!=null)Image(state.preview.asImageBitmap(),"Print preview, side ${state.previewIndex+1}",Modifier.fillMaxSize())
                    else if(sampleRender)SamplePage()
                    else if(state.previewBusy)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(24.dp),color=Blue,strokeWidth=2.dp)}
                }
                IconButton(onClick=zoom,modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)){Icon(Icons.Rounded.OpenInFull,"Enlarge preview",Modifier.size(19.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(state.previewCount>1){
                    IconButton(onClick={turn(-1)},enabled=state.previewIndex>0,modifier=Modifier.align(Alignment.CenterStart).padding(start=3.dp)){Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Previous side",Modifier.size(20.dp))}
                    IconButton(onClick={turn(1)},enabled=state.previewIndex<state.previewCount-1,modifier=Modifier.align(Alignment.CenterEnd).padding(end=3.dp)){Icon(Icons.AutoMirrored.Rounded.NavigateNext,"Next side",Modifier.size(24.dp))}
                }
            }
            Row(Modifier.fillMaxWidth().clickable(onClick=open).padding(horizontal=20.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Rounded.Description,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){Text(doc.name,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${doc.pages} pages · ${(doc.bytes/1024).coerceAtLeast(1)} KB",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))}
                TextButton(onClick=open,contentPadding=PaddingValues(horizontal=8.dp)){Text("Change",fontSize=13.sp)}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PaperScreen(state:PaperState,change:(PrintSettings)->Unit={},open:()->Unit={},sample:()->Unit={},turn:(Int)->Unit={},review:()->Unit={},connect:()->Unit={},theme:()->Unit={},clear:()->Unit={},cancel:()->Unit={},sampleRender:Boolean=false,initialSheet:String?=null,
    applyPreset:(String)->Unit={},savePreset:(String)->Unit={},deletePreset:(String)->Unit={},scanNetwork:()->Unit={},addNetwork:(String)->Unit={},removeNetwork:(String)->Unit={},printSettings:()->Unit={},calibrationSample:()->Unit={},saveDuplex:(DuplexSetup)->Unit={}){
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    val s=state.settings
    val plan=remember(s,state.document){runCatching{state.document?.let{PrintPlan.build(s,it.pages)}}}
    val ink=MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxSize()){
        Box {
            Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0),bottomBar={
                Surface(color=MaterialTheme.colorScheme.background){Column(Modifier.navigationBarsPadding().padding(horizontal=22.dp,vertical=12.dp).widthIn(max=600.dp)){
                    if(state.document!=null){Row(Modifier.fillMaxWidth().padding(horizontal=5.dp).padding(bottom=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                        val p=plan.getOrNull();Text(if(p!=null)"${p.sheets} ${if(p.sheets==1)"sheet"else"sheets"} · ${s.copies} ${if(s.copies==1)"copy"else"copies"}"else"Check page selection",fontSize=13.sp,color=ink)
                        Text(if(s.duplex)"Both sides · ${s.sides.label.lowercase()}" else "One-sided",fontSize=12.sp,color=ink)
                    }}
                    Button(onClick={if(state.document==null)open()else review()},enabled=!state.busy&&(state.document==null||plan.isSuccess&&plan.getOrNull()!=null),modifier=Modifier.fillMaxWidth().heightIn(min=57.dp),shape=CircleShape,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary)){
                        Icon(if(state.document==null)Icons.Rounded.Add else Icons.Rounded.Print,null,Modifier.size(21.dp));Spacer(Modifier.width(11.dp));Text(if(state.document==null)"Choose document" else "Review & print",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                    }
                }}
            }){padding->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal=18.dp).widthIn(max=600.dp)){
                    Row(Modifier.fillMaxWidth().padding(start=6.dp,top=12.dp),verticalAlignment=Alignment.CenterVertically){PaperLogo(31);Column(Modifier.padding(start=10.dp).weight(1f)){Text("Paper",fontSize=19.sp,fontWeight=FontWeight.SemiBold);Text("by Avyntora",fontSize=11.sp,lineHeight=15.sp,color=ink)};IconButton(onClick=theme){Icon(Icons.Rounded.Contrast,"Change appearance",Modifier.size(22.dp))};IconButton(onClick={sheet="help"}){Icon(Icons.Rounded.HelpOutline,"Printing help",Modifier.size(22.dp))}}
                    Column(Modifier.fillMaxWidth().padding(top=40.dp,bottom=37.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        Text("Print",fontSize=40.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp,lineHeight=48.sp)
                        Text("From your phone. Onto paper.",fontSize=15.sp,color=ink,modifier=Modifier.padding(top=8.dp))
                    }
                    Surface(onClick={sheet="printer"},shape=PageShape,color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){
                        Row(Modifier.padding(horizontal=20.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
                            Box(Modifier.size(43.dp).background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Print,null,Modifier.size(25.dp),tint=MaterialTheme.colorScheme.onSurface)}
                            Column(Modifier.weight(1f).padding(start=14.dp)){Text("Your printers",fontSize=16.sp,fontWeight=FontWeight.SemiBold);Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=5.dp)){Box(Modifier.size(6.dp).background(if(state.usb.detected)Color(0xFFD89B29)else ink,CircleShape));Text(if(state.usb.detected)"USB · ${state.usb.compatibility.label}"else if(state.networkPrinters.isNotEmpty())"${state.networkPrinters.size} network printers saved"else"USB OTG or local network",fontSize=12.sp,color=ink,modifier=Modifier.padding(start=6.dp))}}
                            Icon(Icons.AutoMirrored.Rounded.NavigateNext,null,Modifier.size(21.dp),tint=ink)
                        }
                    }
                    Spacer(Modifier.height(14.dp));PreviewCard(state,sampleRender,open,turn,{sheet="preview"})
                    if(state.document==null){
                        Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.Center){TextButton(onClick=sample){Text("Try a sample document",fontSize=13.sp)}}
                    }else if(state.previewCount>1){Text("Side ${state.previewIndex+1} of ${state.previewCount} · tap to enlarge",Modifier.fillMaxWidth().padding(top=9.dp),fontSize=11.sp,color=ink,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
                    SectionLabel("Print settings")
                    Group{SettingsRow("Saved presets",state.presets.firstOrNull{it.id==state.activePreset}?.name?:"Choose or save",icon=Icons.Rounded.BookmarkBorder,click={sheet="presets"})}
                    Spacer(Modifier.height(13.dp))
                    Group{
                        Column(Modifier.padding(horizontal=20.dp,vertical=17.dp)){
                            Text("Colour",fontSize=16.sp,modifier=Modifier.padding(bottom=13.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                ChoicePill("Colour",s.color,Modifier.weight(1f),Icons.Rounded.Palette){change(s.copy(color=true))}
                                ChoicePill("Black & white",!s.color,Modifier.weight(1f),Icons.Rounded.Contrast){change(s.copy(color=false))}
                            }
                        };Separator();SettingsRow("Copies",tail={CopyStepper(s.copies){change(s.copy(copies=it))}})
                    }
                    Spacer(Modifier.height(13.dp));DuplexControl(s,change)
                    Spacer(Modifier.height(13.dp));Group{
                        SettingsRow("Pages",if(s.range.isBlank())s.filter.label else s.range,click={sheet="pages"});Separator()
                        SettingsRow("Paper & layout",s.paper.name+" · "+if(s.landscape)"Landscape"else"Portrait",click={sheet="layout"});Separator()
                        SettingsRow("More settings",if(s.perSheet>1)"${s.perSheet} per side"else"Scale, margins & more",click={sheet="more"})
                    }
                    if(state.selectionError!=null)Text(state.selectionError,Modifier.padding(horizontal=18.dp,vertical=12.dp),fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.error)
                    Text("Processed on your phone.",Modifier.fillMaxWidth().padding(top=20.dp,bottom=22.dp),color=ink,fontSize=12.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            if(state.busy){Surface(color=MaterialTheme.colorScheme.background.copy(alpha=.96f),modifier=Modifier.fillMaxSize().clickable(enabled=false){}){Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(22.dp));Text(if(state.progress>0)"Preparing document · ${state.progress}%"else"Opening your document…",fontSize=18.sp);TextButton(onClick=cancel){Text("Cancel")}}}}
        }
    }
    if(sheet!=null){
        ModalBottomSheet(onDismissRequest={sheet=null},containerColor=MaterialTheme.colorScheme.background,shape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp)){
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp)){
                val title=when(sheet){"pages"->"Pages to print";"layout"->"Paper & layout";"more"->"More settings";"printer"->"Your printers";"presets"->"Saved presets";"duplex"->"Manual two-sided setup";"help"->"A little printing help";else->"Print preview"}
                Text(title,Modifier.padding(start=7.dp,bottom=22.dp),fontSize=27.sp,fontWeight=FontWeight.Bold)
                when(sheet){
                    "pages"->PageOptions(s,state.document?.pages?:0,change)
                    "layout"->LayoutOptions(s,change)
                    "more"->MoreOptions(s,change)
                    "printer"->PrinterSetupContent(state,connect,scanNetwork,addNetwork,removeNetwork,printSettings,{sheet="duplex"})
                    "presets"->PresetContent(state,{applyPreset(it);sheet=null},savePreset,deletePreset)
                    "duplex"->DuplexSetupContent(state.duplexSetup,{calibrationSample();sheet=null},{saveDuplex(it);sheet=null})
                    "help"->HelpContent()
                    "preview"->{
                        if(state.preview!=null)Image(state.preview.asImageBitmap(),"Enlarged print preview",Modifier.fillMaxWidth().heightIn(max=600.dp))else if(sampleRender)Box(Modifier.height(500.dp).aspectRatio(.707f)){SamplePage()}
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){TextButton(onClick={turn(-1)},enabled=state.previewIndex>0){Text("Previous")};Text("${state.previewIndex+1} / ${state.previewCount}");TextButton(onClick={turn(1)},enabled=state.previewIndex<state.previewCount-1){Text("Next")}}
                    }
                }
                Spacer(Modifier.height(18.dp));Button(onClick={sheet=null},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=CircleShape){Text("Done",fontSize=16.sp,fontWeight=FontWeight.SemiBold)}
            }
        }
    }
    if(state.error!=null||state.message!=null){AlertDialog(onDismissRequest=clear,title={Text(if(state.error!=null)"Let’s check that"else"All set")},text={Text(state.error?:state.message.orEmpty())},confirmButton={TextButton(onClick=clear){Text("OK")}})}
}

@Composable private fun PageOptions(s:PrintSettings,count:Int,change:(PrintSettings)->Unit){
    var range by remember(s.range){mutableStateOf(s.range)}
    val error=if(count>0)runCatching{PrintPlan.build(s,count)}.exceptionOrNull()?.message else null
    Group {Column(Modifier.padding(20.dp)){
        Text("Page range",fontSize=16.sp);Spacer(Modifier.height(12.dp))
        OutlinedTextField(value=range,onValueChange={range=it;change(s.copy(range=it))},placeholder={Text("All pages")},isError=error!=null,supportingText={Text(error?:if(count>0)"$count pages · e.g. 1-3, 5"else"Choose a document first")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii))
    }}
    SectionLabel("Page selection");Group{PageFilter.entries.forEachIndexed{i,f->if(i>0)Separator();RadioRow(f.label,s.filter==f){change(s.copy(filter=f))}}}
}
@Composable private fun RadioRow(label:String,selected:Boolean,click:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=click).heightIn(min=57.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=15.sp);RadioButton(selected=selected,onClick=null)}}
@Composable internal fun LayoutOptions(s:PrintSettings,change:(PrintSettings)->Unit){
    Group{PaperSize.entries.forEachIndexed{i,p->if(i>0)Separator();RadioRow(p.label,s.paper==p){change(s.copy(paper=p))}}}
    SectionLabel("Orientation");Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){ChoicePill("Portrait",!s.landscape,Modifier.weight(1f),Icons.Rounded.StayCurrentPortrait){change(s.copy(landscape=false))};ChoicePill("Landscape",s.landscape,Modifier.weight(1f),Icons.Rounded.StayCurrentLandscape){change(s.copy(landscape=true))}}
    SectionLabel("Pages per side");Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf(1,2,4).forEach{n->ChoicePill("$n",s.perSheet==n,Modifier.weight(1f)){change(s.copy(perSheet=n))}}}
}
@Composable private fun MoreOptions(s:PrintSettings,change:(PrintSettings)->Unit){
    var scale by remember(s.scale){mutableStateOf(if(s.scale==0)""else s.scale.toString())}
    var margins by remember(s.marginMm){mutableStateOf(s.marginMm.toString())}
    Group{Column(Modifier.padding(20.dp)){
        Text("Scale",fontSize=16.sp);Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            ChoicePill("Fit to page",s.scale==0,Modifier.weight(1f)){change(s.copy(scale=0))};ChoicePill("Actual size",s.scale==100,Modifier.weight(1f)){change(s.copy(scale=100))}
        }
        Spacer(Modifier.height(15.dp));OutlinedTextField(value=scale,onValueChange={v->scale=v;v.toIntOrNull()?.takeIf{it in 25..200}?.let{change(s.copy(scale=it))}},label={Text("Custom scale (%)")},supportingText={Text("25–200% · larger sizes may crop content")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp));OutlinedTextField(value=margins,onValueChange={v->margins=v;v.toIntOrNull()?.takeIf{it in 5..30}?.let{change(s.copy(marginMm=it))}},label={Text("Margins (mm)")},supportingText={Text("5–30 mm · respects the G2010’s paper edges")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
    }}
    SectionLabel("Print quality");Group{RenderQuality.entries.forEachIndexed{i,q->if(i>0)Separator();RadioRow(q.label,s.quality==q){change(s.copy(quality=q))}}}
    Text("For USB, Draft / Standard / High also sets printer quality. DPI values describe the prepared PDF.",Modifier.padding(16.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Group{
        SettingsRow("Collate copies",detail="Print complete sets in order",tail={OneSwitch(s.collate,{change(s.copy(collate=it))},Modifier.semantics{contentDescription="Collate copies"})});Separator()
        SettingsRow("Reverse page order",detail=if(s.duplex)"Unavailable for two-sided printing"else"Last page first",tail={OneSwitch(s.reverse,{change(s.copy(reverse=it))},Modifier.semantics{contentDescription="Reverse page order"},enabled=!s.duplex)});Separator()
        SettingsRow("Filename & page numbers",detail="Add a small footer to each side",tail={OneSwitch(s.footer,{change(s.copy(footer=it))},Modifier.semantics{contentDescription="Filename and page numbers"})});Separator()
        SettingsRow("Photo paper",detail=if(s.duplex)"Use plain paper for both sides"else"Select the same media in your print service",tail={OneSwitch(s.photoPaper,{change(s.copy(photoPaper=it))},Modifier.semantics{contentDescription="Photo paper"},enabled=!s.duplex)})
    }
    if(s.duplex){SectionLabel("Manual two-sided setup");Group{SettingsRow("Reverse the back-side order",detail="Turn off if your test shows reversed sheets",tail={OneSwitch(s.reverseBacks,{change(s.copy(reverseBacks=it))},Modifier.semantics{contentDescription="Reverse the back-side order"})})}}
}
@Composable private fun PrinterInfo(usb:UsbStatus,connect:()->Unit){
    Group{Column(Modifier.padding(22.dp)){
        Icon(Icons.Rounded.Usb,null,Modifier.size(35.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(15.dp));Text("Canon PIXMA G2010",fontSize=21.sp,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp));Text(if(usb.detected)"${usb.name}\n${if(usb.authorized)"USB access allowed"else"USB access not allowed yet"}"else"Turn on your printer. Connect its USB cable to your phone using an OTG adapter.",fontSize=15.sp,lineHeight=23.sp)
        Spacer(Modifier.height(15.dp));Text("An experimental Canon raster driver is included. G2010 printing uses Gutenprint’s G2100 family profile and needs hardware testing. Start with one plain-paper page. No other printing app is required.",fontSize=14.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp));OutlinedButton(onClick=connect,modifier=Modifier.fillMaxWidth().height(49.dp)){Text(if(usb.detected&&!usb.authorized)"Allow USB access"else"Check USB connection")}
    }}
    Spacer(Modifier.height(16.dp));InfoNote("Paper checks that the device identifies itself as an IVEC-capable G2010 before sending a job. You can also save prepared PDFs or use an Android print service.")
}
@Composable fun InfoNote(text:String){Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha=.07f),RoundedCornerShape(20.dp)).padding(18.dp),horizontalArrangement=Arrangement.spacedBy(11.dp)){
    Icon(Icons.Rounded.Info,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary);Text(text,fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}}
@Composable private fun HelpContent(){
    Group{Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(17.dp)){
        Text("Printing on both sides",fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text("The G2010 turns paper manually. Paper prepares the front sides first, then guides you through reloading for the backs. Choose long edge for a book, or short edge for a notepad.",fontSize=14.sp,lineHeight=23.sp)
        Text("Start with a two-page test",fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text("Paper feed and stack order vary. Mark a sheet’s top edge, print two pages, and check the back is upright. Change back-side order in More settings if needed. Let the ink dry before reloading.",fontSize=14.sp,lineHeight=23.sp)
        Text("Files stay private",fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text("No ads, accounts or analytics. Network access connects to the printer you choose. PDFs, images, and plain text are supported. Export office files to PDF before opening them here.",fontSize=14.sp,lineHeight=23.sp)
        Text("About Paper",fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text("Paper is a product of Avyntora, guided by Samsung’s published One UI design principles. Free and open source under GPL-3.0-or-later. Not affiliated with Samsung or Canon. Version 0.3.0.",fontSize=13.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}
}
fun previewState()=PaperState(document=LocalDocument(File("sample.pdf"),"A little more clarity.pdf",4,184320),previewCount=4,settings=PrintSettings(sides=Sides.LONG))
@Preview(name="Print · light",widthDp=412,heightDp=1000,showBackground=true)
@Composable fun PrintPreview(){PaperTheme{PaperScreen(previewState(),sampleRender=true)}}
@Preview(name="Print · dark",widthDp=412,heightDp=1000,showBackground=true)
@Composable fun DarkPrintPreview(){PaperTheme(true){PaperScreen(previewState(),sampleRender=true)}}
@Preview(name="Welcome · compact",widthDp=360,heightDp=800,showBackground=true)
@Composable fun WelcomePreview(){PaperTheme{PaperScreen(PaperState())}}
