// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print.ipp

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.net.URI
import java.util.ArrayDeque

class NetworkPrinterStore(context:Context) {
    private val prefs=context.getSharedPreferences("network-printers",0)
    fun all():List<NetworkPrinter> = runCatching{val a=JSONArray(prefs.getString("printers","[]")!!);(0 until minOf(a.length(),32)).mapNotNull{i->runCatching{NetworkPrinter.decode(a.getJSONObject(i))}.getOrNull()}.distinctBy{it.id}}.getOrDefault(emptyList())
    fun save(printer:NetworkPrinter)=synchronized(lock){val list=(all().filterNot{it.id==printer.id}+printer).takeLast(32);prefs.edit().putString("printers",JSONArray().also{a->list.forEach{a.put(it.encode())}}.toString()).apply()}
    fun remove(id:String)=synchronized(lock){prefs.edit().putString("printers",JSONArray().also{a->all().filterNot{it.id==id}.forEach{a.put(it.encode())}}.toString()).apply()}
    companion object {private val lock=Any()}
}

/** Bounded, user-initiated discovery. Android's resolver is serialized on older devices. */
class NetworkDiscovery(context:Context,private val found:(String)->Unit,private val error:(String)->Unit) {
    private val manager=context.getSystemService(NsdManager::class.java)
    private val main=Handler(Looper.getMainLooper())
    private val listeners=mutableListOf<NsdManager.DiscoveryListener>()
    private val pending=ArrayDeque<NsdServiceInfo>();private val seen=mutableSetOf<String>()
    private var resolving=false;private var active=false;private var generation=0
    fun start(){
        stop();active=true;val current=generation
        listOf("_ipp._tcp.","_ipps._tcp.").forEach{type->
            val listener=object:NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t:String){}
                override fun onDiscoveryStopped(t:String){}
                override fun onStartDiscoveryFailed(t:String,code:Int){main.post{if(active&&generation==current)error("Network discovery could not start ($code). You can add a printer by address.")}}
                override fun onStopDiscoveryFailed(t:String,code:Int){}
                override fun onServiceLost(info:NsdServiceInfo){}
                override fun onServiceFound(info:NsdServiceInfo){main.post{val key=info.serviceName+info.serviceType;if(active&&generation==current&&seen.size<32&&seen.add(key)){pending.add(info);resolveNext()}}}
            }
            listeners.add(listener);runCatching{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener)}.onFailure{error(it.message?:"Discovery is unavailable.")}
        }
    }
    @Suppress("DEPRECATION") private fun resolveNext(){
        if(!active||resolving||pending.isEmpty())return;resolving=true;val info=pending.removeFirst();val current=generation
        runCatching{manager.resolveService(info,object:NsdManager.ResolveListener {
            override fun onResolveFailed(i:NsdServiceInfo,code:Int){main.post{if(generation==current){resolving=false;resolveNext()}}}
            override fun onServiceResolved(i:NsdServiceInfo){main.post{
                if(generation!=current)return@post
                resolving=false
                if(active)runCatching{
                    val host=requireNotNull(requireNotNull(i.host).hostAddress)
                    val rp=i.attributes["rp"]?.toString(Charsets.UTF_8)?.take(512)?.trimStart('/')?:"ipp/print"
                    val scheme=if(i.serviceType.contains("_ipps"))"ipps"else"ipp"
                    found(PrinterAddress.parse(URI(scheme,null,host,i.port,"/$rp",null,null).toASCIIString()).canonical)
                }.onFailure{error("A printer advertised an invalid address.")}
                resolveNext()
            }}
        })}.onFailure{resolving=false;resolveNext()}
    }
    fun stop(){active=false;generation++;resolving=false;listeners.forEach{runCatching{manager.stopServiceDiscovery(it)}};listeners.clear();pending.clear();seen.clear()}
}
