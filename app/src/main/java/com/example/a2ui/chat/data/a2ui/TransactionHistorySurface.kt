package com.example.a2ui.chat.data.a2ui

import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.json.*

/**
 * Builds a [UiDefinition] for the "show my last months transactions" mock response (Run 1).
 *
 * Surface ID : response_run1
 * Root       : root
 *
 * Structure:
 *   root (Column)
 *     └─ hdr_card (Card)
 *          └─ hdr_card_col (Column)
 *               ├─ hdr_col (Column): title, period, count
 *               └─ txns_list (Column): t_row_0..13, t_div_0..12
 *
 * Each transaction row:
 *   t_row_N (Row, spaceBetween)
 *     ├─ t_left_N (Column): t_action_N, t_date_N
 *     └─ t_amt_N (Text)
 */
object TransactionHistorySurface {

    private data class Tx(val action: String, val date: String, val amount: String)

    private val transactions = listOf(
        Tx("Direct Deposit – Employer Payroll", "2026-03-28", "+\$4,250.00"),
        Tx("Buy NVDA · 8 shares",               "2026-03-26", "-\$2,184.00"),
        Tx("Rent Payment – Oakwood Apartments", "2026-03-25", "-\$1,850.00"),
        Tx("Whole Foods Market",                "2026-03-22", "-\$134.57"),
        Tx("Sell AAPL · 5 shares",              "2026-03-20", "+\$1,062.50"),
        Tx("Netflix Subscription",              "2026-03-18", "-\$22.99"),
        Tx("Transfer to Savings",               "2026-03-17", "-\$500.00"),
        Tx("Dividend – MSFT",                   "2026-03-15", "+\$61.20"),
        Tx("Shell Gas Station",                 "2026-03-14", "-\$78.40"),
        Tx("Buy VOO · 3 shares",                "2026-03-11", "-\$1,371.00"),
        Tx("Amazon Purchase",                   "2026-03-09", "-\$56.83"),
        Tx("Spotify Premium",                   "2026-03-07", "-\$11.99"),
        Tx("Direct Deposit – Employer Payroll", "2026-03-14", "+\$4,250.00"),
        Tx("ATM Withdrawal – Chase ••••4421",   "2026-03-03", "-\$200.00")
    )

    fun build(): UiDefinition {
        val c = mutableMapOf<String, Component>()

        // ── root ──────────────────────────────────────────────────────────
        c["root"] = comp("root", "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray { add("hdr_card") })
            })
        })

        // ── hdr_card ──────────────────────────────────────────────────────
        c["hdr_card"] = comp("hdr_card", "Card", buildJsonObject {
            put("child", "hdr_card_col")
        })
        c["hdr_card_col"] = comp("hdr_card_col", "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    add("hdr_col")
                    add("txns_list")
                })
            })
        })

        // ── Header section ────────────────────────────────────────────────
        c["hdr_col"] = comp("hdr_col", "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    add("title"); add("period"); add("count")
                })
            })
        })
        c["title"]  = text("title",  "March 2026 Transactions", "h5")
        c["period"] = text("period", "Mar 1 – Mar 31, 2026",    "caption")
        c["count"]  = text("count",  "14 transactions",         "caption")

        // ── Transactions list ─────────────────────────────────────────────
        c["txns_list"] = comp("txns_list", "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    transactions.indices.forEach { idx ->
                        add("t_row_$idx")
                        if (idx < transactions.lastIndex) add("t_div_$idx")
                    }
                })
            })
        })

        transactions.forEachIndexed { idx, tx ->
            val rowId    = "t_row_$idx"
            val leftId   = "t_left_$idx"
            val actionId = "t_action_$idx"
            val dateId   = "t_date_$idx"
            val amtId    = "t_amt_$idx"

            c[rowId] = comp(rowId, "Row", buildJsonObject {
                put("children", buildJsonObject {
                    put("explicitList", buildJsonArray {
                        add(leftId); add(amtId)
                    })
                })
                put("distribution", "spaceBetween")
            })
            c[leftId] = comp(leftId, "Column", buildJsonObject {
                put("children", buildJsonObject {
                    put("explicitList", buildJsonArray {
                        add(actionId); add(dateId)
                    })
                })
            })
            c[actionId] = text(actionId, tx.action, "body")
            c[dateId]   = text(dateId,   tx.date,   "caption")
            c[amtId]    = text(amtId,    tx.amount, "body")

            if (idx < transactions.lastIndex) {
                val divId = "t_div_$idx"
                c[divId] = comp(divId, "Divider", buildJsonObject {})
            }
        }

        return UiDefinition(surfaceId = "response_run1", root = "root", components = c)
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun comp(id: String, type: String, props: JsonObject) =
        Component(id = id, componentProperties = mapOf(type to props))

    private fun text(id: String, value: String, usageHint: String) =
        comp(id, "Text", buildJsonObject {
            put("text", buildJsonObject { put("literalString", value) })
            put("usageHint", usageHint)
        })
}
