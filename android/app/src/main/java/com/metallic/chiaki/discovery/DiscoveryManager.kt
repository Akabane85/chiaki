/*
 * This file is part of Chiaki.
 *
 * Chiaki is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Chiaki is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Chiaki.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.metallic.chiaki.discovery

import android.util.Log
import com.metallic.chiaki.common.MacAddress
import com.metallic.chiaki.common.ext.hexToByteArray
import com.metallic.chiaki.lib.CreateError
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.lib.DiscoveryService
import com.metallic.chiaki.lib.DiscoveryServiceOptions
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.lang.NumberFormatException
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

val DiscoveryHost.ps4Mac get() = this.hostId?.hexToByteArray()?.let {
	if(it.size == MacAddress.LENGTH)
		MacAddress(it)
	else
		null
}

class DiscoveryManager
{
	companion object
	{
		const val HOSTS_MAX: ULong = 16U
		const val DROP_PINGS: ULong = 3U
		const val PING_MS: ULong = 500U
		const val PORT = 987
	}

	private var discoveryService: DiscoveryService? = null

	private val discoveryActiveSubject: Subject<Boolean> = BehaviorSubject.create<Boolean>().also { it.onNext(false) }
	val discoveryActive: Observable<Boolean> get() = discoveryActiveSubject
	var active = false
		set(value)
		{
			field = value
			discoveryActiveSubject.onNext(value)
			updateService()
		}
	private var paused = false

	private var discoveredHostsSubject: Subject<List<DiscoveryHost>> = BehaviorSubject.create<List<DiscoveryHost>>().also {
		it.onNext(listOf())
	}.toSerialized()
	val discoveredHosts: Observable<List<DiscoveryHost>> get() = discoveredHostsSubject

	fun resume()
	{
		paused = false
		updateService()
	}

	fun pause()
	{
		paused = true
		updateService()
	}

	fun dispose()
	{
		active = false
	}

	fun sendWakeup(host: String, registKey: ByteArray)
	{
		val registKeyString = registKey.indexOfFirst { it == 0.toByte() }.let { end -> registKey.copyOfRange(0, if(end >= 0) end else registKey.size) }.toString(StandardCharsets.UTF_8)
		val credential = try { registKeyString.toULong(16) } catch(e: NumberFormatException) {
			Log.e("DiscoveryManager", "Failed to convert registKey to int", e)
			return
		}
		DiscoveryService.wakeup(discoveryService, host, credential)
	}

	private fun updateService()
	{
		if(active && !paused && discoveryService == null)
		{
			try
			{
				discoveryService = DiscoveryService(DiscoveryServiceOptions(
					HOSTS_MAX, DROP_PINGS, PING_MS, InetSocketAddress("255.255.255.255", PORT)
				), discoveredHostsSubject::onNext)
			}
			catch(e: CreateError)
			{
				Log.e("DiscoveryManager", "Failed to start Discovery Service: $e")
			}
		}
		else if((!active || paused) && discoveryService != null)
		{
			val service = discoveryService ?: return
			service.dispose()
			discoveryService = null
			discoveredHostsSubject.onNext(listOf())
		}
	}
}