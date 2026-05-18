package com.victor.postly.utils

import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.IOException
import java.util.Locale

class LocalizacaoHelper(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) {
    interface Callback {
        fun onLocalizacaoRecebida(endereco: Address, latitude: Double, longitude: Double)
        fun onErro(mensagem: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun obterLocalizacaoAtual(callback: Callback) {
        val locationRequest = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .build()

        fusedLocationClient.getCurrentLocation(locationRequest, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    // BUG 1 CORRIGIDO: não bloqueia a main thread com chamada de rede síncrona
                    obterEndereco(location.latitude, location.longitude, callback)
                } else {
                    callback.onErro("Localização indisponível")
                }
            }
            .addOnFailureListener {
                callback.onErro("Falha ao obter localização: ${it.message}")
            }
    }

    private fun obterEndereco(latitude: Double, longitude: Double, callback: Callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // BUG 2 CORRIGIDO: onGeocode() pode ser chamado em background thread;
            // usamos mainHandler.post para garantir atualização de UI na main thread
            val geocoder = Geocoder(context, Locale.getDefault())
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        mainHandler.post {
                            if (addresses.isNotEmpty()) {
                                callback.onLocalizacaoRecebida(addresses[0], latitude, longitude)
                            } else {
                                callback.onErro("Endereço não encontrado")
                            }
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        mainHandler.post {
                            callback.onErro(errorMessage ?: "Erro desconhecido no Geocoder")
                        }
                    }
                }
            )
        } else {
            // BUG 1 CORRIGIDO: geocoder síncrono rodando em thread de background,
            // resultado postado de volta à main thread via Handler
            Thread {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())

                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    mainHandler.post {
                        if (!addresses.isNullOrEmpty()) {
                            callback.onLocalizacaoRecebida(addresses[0], latitude, longitude)
                        } else {
                            callback.onErro("Endereço não encontrado")
                        }
                    }
                } catch (e: IOException) {
                    mainHandler.post {
                        callback.onErro("Erro no Geocoder: ${e.message}")
                    }
                }
            }.start()
        }
    }
}