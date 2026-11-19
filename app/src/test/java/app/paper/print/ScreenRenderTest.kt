package app.paper.print

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ScreenRenderTest {
    @get:Rule val paparazzi=Paparazzi(deviceConfig=DeviceConfig.PIXEL_5.copy(screenWidth=1080,screenHeight=2400,softButtons=false),theme="android:style/Theme.Material.Light.NoActionBar")
    @Test fun printLight(){paparazzi.snapshot{PaperTheme{PaperScreen(previewState(),sampleRender=true)}}}
    @Test fun printDark(){paparazzi.snapshot{PaperTheme(true){PaperScreen(previewState(),sampleRender=true)}}}
    @Test fun welcome(){paparazzi.snapshot{PaperTheme{PaperScreen(PaperState())}}}
    // Layoutlib captures one window; Android ModalBottomSheet dialogs are separate windows.
    // Render production content directly so an invisible dialog cannot masquerade as visual QA.
    private fun sheet(title:String,content:@Composable ()->Unit){paparazzi.snapshot{PaperTheme{
        Surface(color=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxSize()){
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)){
                BottomSheetDefaults.DragHandle(modifier=Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
                Text(title,fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(bottom=22.dp));content()
            }
        }
    }}}
    @Test fun layoutSheet(){sheet("Paper & layout"){LayoutOptions(PrintSettings(),{})}}
    @Test fun presetsSheet(){sheet("Saved presets"){PresetContent(PaperState(),{},{},{})}}
    @Test fun printerSheet(){sheet("Your printers"){PrinterSetupContent(PaperState(),{},{},{},{},{},{})}}
    @Test fun duplexSetup(){sheet("Manual two-sided setup"){DuplexSetupContent(null,{},{})}}
}
