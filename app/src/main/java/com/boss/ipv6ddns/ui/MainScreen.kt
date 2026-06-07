package com.boss.ipv6ddns.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.boss.ipv6ddns.R

private enum class Tab { STATUS, CONFIG, LOG }

@Composable
fun MainScreen(activity: com.boss.ipv6ddns.MainActivity? = null) {
    var current by rememberSaveable { mutableStateOf(Tab.STATUS) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = current == Tab.STATUS,
                    onClick = { current = Tab.STATUS },
                    icon = { Icon(Icons.Filled.Insights, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_status)) }
                )
                NavigationBarItem(
                    selected = current == Tab.CONFIG,
                    onClick = { current = Tab.CONFIG },
                    icon = { Icon(Icons.Filled.Dns, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_config)) }
                )
                NavigationBarItem(
                    selected = current == Tab.LOG,
                    onClick = { current = Tab.LOG },
                    icon = { Icon(Icons.Filled.Article, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_log)) }
                )
            }
        }
    ) { inner ->
        when (current) {
            Tab.STATUS -> StatusScreen(
                contentPadding = inner,
                activity = activity,
                modifier = Modifier.fillMaxSize()
            )
            Tab.CONFIG -> ConfigScreen(
                contentPadding = inner,
                modifier = Modifier.fillMaxSize()
            )
            Tab.LOG -> LogScreen(
                contentPadding = inner,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
