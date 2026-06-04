package com.frenky.egypt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frenky.egypt.data.EgyptConfig
import java.text.DecimalFormat

private enum class ConvertDirection { EgpToEur, EurToEgp }

@Composable
fun ConverterScreen(modifier: Modifier = Modifier, config: EgyptConfig) {
    var direction by remember { mutableStateOf(ConvertDirection.EgpToEur) }
    var amountInput by remember { mutableStateOf("") }
    val rate = config.egp_to_eur
    val amount = amountInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val fmt = remember { DecimalFormat("#,##0.00") }
    val fmtRate = remember { DecimalFormat("#,##0.####") }

    val egpToEur = direction == ConvertDirection.EgpToEur
    val inputLabel = if (egpToEur) "Sterline egiziane (EGP)" else "Euro (EUR)"
    val resultLabel = if (egpToEur) "Euro (EUR)" else "Sterline egiziane (EGP)"
    val resultValue = if (egpToEur) amount * rate else if (rate > 0) amount / rate else 0.0
    val resultText = if (egpToEur) "${fmt.format(resultValue)} €" else "${fmt.format(resultValue)} EGP"
    val secondaryLine = when {
        amount <= 0 -> null
        egpToEur -> "${fmt.format(amount)} EGP"
        else -> "${fmt.format(amount)} €"
    }
    val title = if (egpToEur) "Convertitore EGP → EUR" else "Convertitore EUR → EGP"
    val rateLine = if (egpToEur) {
        "Tasso: 1 EGP = ${fmtRate.format(rate)} EUR"
    } else {
        val eurToEgp = if (rate > 0) 1.0 / rate else 0.0
        "Tasso: 1 EUR = ${fmt.format(eurToEgp)} EGP"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    direction = if (egpToEur) ConvertDirection.EurToEgp else ConvertDirection.EgpToEur
                },
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = "Inverti direzione (EGP ↔ EUR)",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            rateLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = amountInput,
            onValueChange = { v ->
                if (v.isEmpty() || v.matches(Regex("^\\d*([.,]\\d{0,2})?$"))) amountInput = v
            },
            label = { Text(inputLabel) },
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
                Text(resultLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(resultText, style = MaterialTheme.typography.displaySmall)
                secondaryLine?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
