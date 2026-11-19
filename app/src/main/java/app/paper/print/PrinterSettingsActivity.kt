// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print
import android.app.Activity
import android.os.Bundle
import android.content.Intent
/** Dedicated entry point from Android's print-service settings. */
class PrinterSettingsActivity:Activity(){override fun onCreate(state:Bundle?){super.onCreate(state);startActivity(Intent(this,MainActivity::class.java).putExtra("printers",true));finish()}}
