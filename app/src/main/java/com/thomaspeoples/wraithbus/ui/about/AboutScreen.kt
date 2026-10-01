package com.thomaspeoples.wraithbus.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thomaspeoples.wraithbus.BuildConfig
import com.thomaspeoples.wraithbus.ntfy.TopicStore

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Wraith Bus",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Wraith Bus is a live UK bus tracker. It shows live bus " +
                "positions on a map and a dashboard of pipeline health and " +
                "punctuality stats, built from data polled from the UK Bus " +
                "Open Data Service.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Alerts",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        val context = LocalContext.current
        var topic by remember { mutableStateOf(TopicStore.get(context)) }
        Text(
            text = "Topic ID for pipeline alerts. Treat it like a password: " +
                "anyone who knows it can read the alerts. Leave blank to " +
                "turn alerts off.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("Topic ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { TopicStore.set(context, topic) }) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Data sources",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Live vehicle positions come from the UK Bus Open Data " +
                "Service (BODS), a UK Department for Transport service, " +
                "polled every 60 seconds.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Map tiles use OpenStreetMap data, self-hosted by this " +
                "app's own backend. © OpenStreetMap contributors.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Bus status (on time, late, early, or unknown) is a " +
                "model built from schedule data. Unknown is common -- " +
                "roughly three-quarters of live buses at any moment -- " +
                "and just means there isn't enough data yet to classify " +
                "that bus. The BODS feed itself occasionally has data " +
                "quality issues, such as an operator's route briefly " +
                "reporting coordinates far outside Great Britain.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Source code",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Wraith Bus is open source under the MIT license, split " +
                "across two repos: this app, and the ingestor/database/API " +
                "platform it talks to.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor-apk",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "https://git.thomaspeoples.com/thomaspeoples/wraith-bus-ingestor",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Privacy",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "There are no user accounts and no login. Wraith Bus " +
                "does not collect analytics and does not track its use. " +
                "It does not request or use your device's location -- it " +
                "only ever shows where the buses are, never where you are.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
