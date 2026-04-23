package com.example.exchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifySequence
import org.iesra.revilofe.ExchangeRateProvider
import org.iesra.revilofe.ExchangeService
import org.iesra.revilofe.InMemoryExchangeRateProvider
import org.iesra.revilofe.Money

class ExchangeServiceDesignedBatteryTest : DescribeSpec({

    afterTest { clearAllMocks() }

    describe("ExchangeService") {

        describe("validación de entrada") {

            val provider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(provider)

            it("error si cantidad es 0") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(0, "USD"), "EUR")
                }
            }

            it("error si cantidad es negativa") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(-100, "USD"), "EUR")
                }
            }

            it("error si moneda origen no tiene 3 letras") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(100, "US"), "EUR")
                }
            }

            it("error si moneda destino no tiene 3 letras") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(100, "USD"), "EU")
                }
            }
        }

        describe("misma moneda") {
            it("devuelve mismo valor") {
                val provider = spyk(InMemoryExchangeRateProvider(emptyMap()))
                val service = ExchangeService(provider)

                val result = service.exchange(Money(500, "USD"), "USD")

                result shouldBe 500
                verify(exactly = 0) { provider.rate(any()) }
            }
        }

        describe("conversión directa") {
            it("usa tasa directa") {
                val provider = mockk<ExchangeRateProvider> {
                    every { rate("USDEUR") } returns 0.9
                }

                val result = ExchangeService(provider)
                    .exchange(Money(1000, "USD"), "EUR")

                result shouldBe 900
            }
        }

        describe("spy proveedor real") {
            it("usa InMemoryExchangeRateProvider") {
                val provider = spyk(
                    InMemoryExchangeRateProvider(mapOf("USDEUR" to 0.92))
                )

                val result = ExchangeService(provider)
                    .exchange(Money(1000, "USD"), "EUR")

                result shouldBe 920
                verify { provider.rate("USDEUR") }
            }
        }

        describe("conversión cruzada") {

            it("usa ruta cruzada válida") {
                val provider = mockk<ExchangeRateProvider> {
                    every { rate("GBPJPY") } throws Exception()
                    every { rate("GBPUSD") } returns 1.2
                    every { rate("USDJPY") } returns 150.0
                }

                val result = ExchangeService(provider)
                    .exchange(Money(2, "GBP"), "JPY")

                result shouldBe (2 * 1.2 * 150).toLong()
            }

            it("usa segunda ruta si la primera falla") {
                val provider = mockk<ExchangeRateProvider> {
                    every { rate("GBPEUR") } throws Exception()
                    every { rate("EURJPY") } throws Exception()
                    every { rate("GBPUSD") } returns 1.3
                    every { rate("USDJPY") } returns 140.0
                }

                val result = ExchangeService(
                    provider,
                    supportedCurrencies = setOf("EUR", "USD", "JPY")
                ).exchange(Money(2, "GBP"), "JPY")

                result shouldBe (2 * 1.3 * 140).toLong()
            }

            it("lanza excepción si no hay ruta") {
                val provider = mockk<ExchangeRateProvider> {
                    every { rate(any()) } throws Exception()
                }

                shouldThrow<IllegalArgumentException> {
                    ExchangeService(provider)
                        .exchange(Money(100, "GBP"), "JPY")
                }
            }

            it("respeta orden de llamadas") {
                val provider = mockk<ExchangeRateProvider> {
                    every { rate("GBPJPY") } throws Exception()
                    every { rate("GBPUSD") } returns 1.1
                    every { rate("USDJPY") } returns 150.0
                }

                ExchangeService(provider)
                    .exchange(Money(2, "GBP"), "JPY")

                verifySequence {
                    provider.rate("GBPJPY")
                    provider.rate("GBPUSD")
                    provider.rate("USDJPY")
                }
            }
        }
    }
})