package com.frenky.egypt.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frenky.egypt.data.EgyptConfig
import java.text.DecimalFormat

@Composable
fun ConverterScreen(modifier: Modifier = Modifier, config: EgyptConfig) {
    var egpInput by remember { mutableStateOf("") }
    val rate = config.egp_to_eur
    val egp = egpInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val eur = egp * rate
    val fmt = remember { DecimalFormat("#,##0.00") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text("Convertitore EGP → EUR", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tasso: 1 EGP = $rate EUR",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = egpInput,
            onValueChange = { v ->
                if (v.isEmpty() || v.matches(Regex("^\\d*([.,]\\d{0,2})?$"))) egpInput = v
            },
            label = { Text("Sterline egiziane (EGP)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Euro (EUR)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text("${fmt.format(eur)} €", style = MaterialTheme.typography.displaySmall)
                if (egp > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("${fmt.format(egp)} EGP", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
