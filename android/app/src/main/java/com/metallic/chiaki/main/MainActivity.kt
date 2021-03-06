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

package com.metallic.chiaki.main

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.metallic.chiaki.R
import com.metallic.chiaki.common.DiscoveredDisplayHost
import com.metallic.chiaki.common.DisplayHost
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.common.ext.putRevealExtra
import com.metallic.chiaki.common.ext.viewModelFactory
import com.metallic.chiaki.common.getDatabase
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.manualconsole.AddManualConsoleActivity
import com.metallic.chiaki.regist.RegistActivity
import com.metallic.chiaki.settings.SettingsActivity
import com.metallic.chiaki.stream.StreamActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity()
{
	private lateinit var viewModel: MainViewModel

	private var discoveryMenuItem: MenuItem? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		title = ""
		setSupportActionBar(toolbar)

		floatingActionButton.setOnClickListener {
			expandFloatingActionButton(!floatingActionButton.isExpanded)
		}
		floatingActionButtonDialBackground.setOnClickListener {
			expandFloatingActionButton(false)
		}

		addManualButton.setOnClickListener { addManualConsole() }
		addManualLabelButton.setOnClickListener { addManualConsole() }

		registerButton.setOnClickListener { showRegistration() }
		registerLabelButton.setOnClickListener { showRegistration() }

		viewModel = ViewModelProviders
			.of(this, viewModelFactory { MainViewModel(getDatabase(this), Preferences(this)) })
			.get(MainViewModel::class.java)

		val recyclerViewAdapter = DisplayHostRecyclerViewAdapter(this::hostTriggered)
		hostsRecyclerView.adapter = recyclerViewAdapter
		hostsRecyclerView.layoutManager = LinearLayoutManager(this)
		viewModel.displayHosts.observe(this, Observer {
			val top = hostsRecyclerView.computeVerticalScrollOffset() == 0
			recyclerViewAdapter.hosts = it
			if(top)
				hostsRecyclerView.scrollToPosition(0)
		})

		viewModel.discoveryActive.observe(this, Observer { active ->
			discoveryMenuItem?.let { updateDiscoveryMenuItem(it, active) }
		})
	}

	private fun expandFloatingActionButton(expand: Boolean)
	{
		floatingActionButton.isExpanded = expand
		floatingActionButton.isActivated = floatingActionButton.isExpanded
	}

	override fun onStart()
	{
		super.onStart()
		viewModel.discoveryManager.resume()
	}

	override fun onStop()
	{
		super.onStop()
		viewModel.discoveryManager.pause()
	}

	override fun onBackPressed()
	{
		if(floatingActionButton.isExpanded)
		{
			expandFloatingActionButton(false)
			return
		}
		super.onBackPressed()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean
	{
		menuInflater.inflate(R.menu.main, menu)
		val discoveryItem = menu.findItem(R.id.action_discover)
		discoveryMenuItem = discoveryItem
		val discoveryActive = viewModel.discoveryActive.value ?: false
		updateDiscoveryMenuItem(discoveryItem, discoveryActive)
		return true
	}

	private fun updateDiscoveryMenuItem(item: MenuItem, active: Boolean)
	{
		item.isChecked = active
		item.setIcon(if(active) R.drawable.ic_discover_on else R.drawable.ic_discover_off)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when(item.itemId)
	{
		R.id.action_discover ->
		{
			viewModel.discoveryManager.active = !(viewModel.discoveryActive.value ?: false)
			true
		}

		R.id.action_settings ->
		{
			Intent(this, SettingsActivity::class.java).also {
				startActivity(it)
			}
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	private fun addManualConsole()
	{
		Intent(this, AddManualConsoleActivity::class.java).also {
			it.putRevealExtra(addManualButton, rootLayout)
			startActivity(it, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
		}
	}

	private fun showRegistration()
	{
		Intent(this, RegistActivity::class.java).also {
			it.putRevealExtra(registerButton, rootLayout)
			startActivity(it, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
		}
	}

	private fun hostTriggered(host: DisplayHost)
	{
		val registeredHost = host.registeredHost
		if(registeredHost != null)
		{
			fun connect() {
				val connectInfo = ConnectInfo(host.host, registeredHost.rpRegistKey, registeredHost.rpKey, Preferences(this).videoProfile)
				Intent(this, StreamActivity::class.java).let {
					it.putExtra(StreamActivity.EXTRA_CONNECT_INFO, connectInfo)
					startActivity(it)
				}
			}

			if(host is DiscoveredDisplayHost && host.discoveredHost.state == DiscoveryHost.State.STANDBY)
			{
				MaterialAlertDialogBuilder(this)
					.setMessage(R.string.alert_message_standby_wakeup)
					.setPositiveButton(R.string.action_wakeup) { _, _ ->
						viewModel.discoveryManager.sendWakeup(host.host, registeredHost.rpRegistKey)
					}
					.setNeutralButton(R.string.action_connect_immediately) { _, _ ->
						connect()
					}
					.setNegativeButton(R.string.action_connect_cancel_connect) { _, _ -> }
					.create()
					.show()
			}
			else
				connect()
		}
		else
		{
			Intent(this, RegistActivity::class.java).let {
				it.putExtra(RegistActivity.EXTRA_HOST, host.host)
				it.putExtra(RegistActivity.EXTRA_BROADCAST, false)
				startActivity(it)
			}
		}
	}
}
