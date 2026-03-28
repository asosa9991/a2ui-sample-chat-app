package com.example.a2ui.chat.data.a2ui

import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.json.*

object BrokerageActivitySurface {

    private data class Transaction(
        val id: String,
        val description: String,
        val subtitle: String,
        val date: String,
        val amount: String
    )

    private val mockTransactions = listOf(
        Transaction("tx1", "Buy AAPL", "10 shares @ \$187.50", "Mar 25, 2026", "-\$1,875.00"),
        Transaction("tx2", "Sell TSLA", "5 shares @ \$245.30", "Mar 24, 2026", "+\$1,226.50"),
        Transaction("tx3", "Dividend · VTI", "Q1 2026 dividend", "Mar 22, 2026", "+\$45.20"),
        Transaction("tx4", "Buy MSFT", "3 shares @ \$412.00", "Mar 20, 2026", "-\$1,236.00"),
        Transaction("tx5", "ACH Deposit", "Transfer from Chase ••4521", "Mar 18, 2026", "+\$5,000.00")
    )

    fun build(): UiDefinition {
        val components = mutableMapOf<String, Component>()

        // Build card column children list
        val cardColChildren = buildList {
            add("header_row")
            add("balance_amount")
            add("balance_change")
            add("divider_main")
            add("tx_section_header")
            mockTransactions.forEachIndexed { idx, tx ->
                add("${tx.id}_row")
                if (idx < mockTransactions.size - 1) add("${tx.id}_div")
            }
        }

        // Root column containing the card
        components["root"] = makeComponent("root", "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray { add("acct_card") })
            })
        })

        // Card widget
        components["acct_card"] = makeComponent("acct_card", "Card", buildJsonObject {
            put("child", "card_col")
        })

        // Card inner column
        components["card_col"] = makeComponent("card_col", "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    cardColChildren.forEach { add(it) }
                })
            })
        })

        // Header row: account name + type badge
        components["header_row"] = makeComponent("header_row", "Row", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    add("acct_name")
                    add("acct_type")
                })
            })
            put("distribution", "spaceBetween")
        })
        components["acct_name"] = makeText("acct_name", "Fidelity Brokerage ••••1234", "h5")
        components["acct_type"] = makeText("acct_type", "INDIVIDUAL", "caption")

        // Balance
        components["balance_amount"] = makeText("balance_amount", "\$48,291.73", "h2")
        components["balance_change"] = makeText("balance_change", "+\$1,203.45  (+2.56%) today", "caption")

        // Divider
        components["divider_main"] = makeComponent("divider_main", "Divider", buildJsonObject {})

        // Section header
        components["tx_section_header"] = makeText("tx_section_header", "Recent Transactions", "h5")

        // Transaction rows
        mockTransactions.forEachIndexed { idx, tx ->
            // Row: left column + amount
            components["${tx.id}_row"] = makeComponent("${tx.id}_row", "Row", buildJsonObject {
                put("children", buildJsonObject {
                    put("explicitList", buildJsonArray {
                        add("${tx.id}_col")
                        add("${tx.id}_amount")
                    })
                })
                put("distribution", "spaceBetween")
            })

            // Left column: description, subtitle, date
            components["${tx.id}_col"] = makeComponent("${tx.id}_col", "Column", buildJsonObject {
                put("children", buildJsonObject {
                    put("explicitList", buildJsonArray {
                        add("${tx.id}_desc")
                        add("${tx.id}_sub")
                        add("${tx.id}_date")
                    })
                })
            })

            components["${tx.id}_desc"] = makeText("${tx.id}_desc", tx.description, "body")
            components["${tx.id}_sub"] = makeText("${tx.id}_sub", tx.subtitle, "caption")
            components["${tx.id}_date"] = makeText("${tx.id}_date", tx.date, "caption")
            components["${tx.id}_amount"] = makeText("${tx.id}_amount", tx.amount, "body")

            // Divider between rows (not after last)
            if (idx < mockTransactions.size - 1) {
                components["${tx.id}_div"] = makeComponent("${tx.id}_div", "Divider", buildJsonObject {})
            }
        }

        return UiDefinition(
            surfaceId = "brokerage_activity",
            root = "root",
            components = components
        )
    }

    private fun makeComponent(id: String, type: String, props: JsonObject): Component =
        Component(id = id, componentProperties = mapOf(type to props))

    private fun makeText(id: String, text: String, usageHint: String): Component =
        makeComponent(id, "Text", buildJsonObject {
            put("text", buildJsonObject { put("literalString", text) })
            put("usageHint", usageHint)
        })
}
