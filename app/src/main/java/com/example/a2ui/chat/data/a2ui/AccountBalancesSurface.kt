package com.example.a2ui.chat.data.a2ui

import com.contextable.a2ui4k.model.Component
import com.contextable.a2ui4k.model.UiDefinition
import kotlinx.serialization.json.*

/**
 * Builds a [UiDefinition] for the "show my account balances" mock response (Run 2).
 *
 * Surface ID : response_run2
 * Root       : root
 *
 * Structure:
 *   root (Column)
 *     ├─ bank_card (Card)   → bank_col (Column)
 *     │    bank_hdr (Row): bank_lbl, bank_tot
 *     │    bdiv1 (Divider)
 *     │    chk_row (Row): chk_left(name,num), chk_right(bal,chg)
 *     │    sav_row (Row): sav_left(name,num), sav_right(bal,chg)
 *     ├─ invest_card (Card) → invest_col (Column)
 *     │    inv_hdr (Row): inv_lbl, inv_tot
 *     │    idiv1 (Divider)
 *     │    brok_row, roth_row, k401_row
 *     └─ total_card (Card)  → total_row (Row): total_lbl, total_val
 */
object AccountBalancesSurface {

    fun build(): UiDefinition {
        val c = mutableMapOf<String, Component>()

        // ── root ──────────────────────────────────────────────────────────
        c["root"] = comp("root", "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    add("bank_card"); add("invest_card"); add("total_card")
                })
            })
        })

        // ── Banking Card ──────────────────────────────────────────────────
        c["bank_card"] = comp("bank_card", "Card", buildJsonObject { put("child", "bank_col") })
        c["bank_col"] = comp("bank_col", "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    add("bank_hdr"); add("bdiv1"); add("chk_row"); add("sav_row")
                })
            })
        })

        // Banking header
        c["bank_hdr"] = comp("bank_hdr", "Row", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray { add("bank_lbl"); add("bank_tot") })
            })
            put("distribution", "spaceBetween")
        })
        c["bank_lbl"] = text("bank_lbl", "BANKING",     "caption")
        c["bank_tot"] = text("bank_tot", "\$24,580.47", "h5")
        c["bdiv1"]    = comp("bdiv1", "Divider", buildJsonObject {})

        // Checking row
        accountRow(
            prefix  = "chk",
            name    = "Premier Checking",
            num     = "••••3847",
            balance = "\$8,214.63",
            change  = "+\$1,200.00 (direct deposit)",
            c       = c
        )

        // Savings row
        accountRow(
            prefix  = "sav",
            name    = "High-Yield Savings",
            num     = "••••5291",
            balance = "\$16,365.84",
            change  = "+\$64.22 (4.75% APY)",
            c       = c
        )

        // ── Investing Card ────────────────────────────────────────────────
        c["invest_card"] = comp("invest_card", "Card", buildJsonObject { put("child", "invest_col") })
        c["invest_col"] = comp("invest_col", "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray {
                    add("inv_hdr"); add("idiv1"); add("brok_row"); add("roth_row"); add("k401_row")
                })
            })
        })

        // Investing header
        c["inv_hdr"] = comp("inv_hdr", "Row", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray { add("inv_lbl"); add("inv_tot") })
            })
            put("distribution", "spaceBetween")
        })
        c["inv_lbl"] = text("inv_lbl", "INVESTING",       "caption")
        c["inv_tot"] = text("inv_tot", "\$198,342.11",    "h5")
        c["idiv1"]   = comp("idiv1", "Divider", buildJsonObject {})

        // Brokerage, Roth IRA, 401(k) rows
        accountRow("brok", "Individual Brokerage", "••••8677", "\$134,987.55", "-\$2,341.80 (-1.71%)", c)
        accountRow("roth", "Roth IRA",             "••••1331", "\$28,754.22",  "+\$318.44 (+1.12%)",  c)
        accountRow("k401", "401(k) Plan",          "••••4402", "\$34,600.34",  "+\$512.90 (+1.50%)",  c)

        // ── Total Card ────────────────────────────────────────────────────
        c["total_card"] = comp("total_card", "Card", buildJsonObject { put("child", "total_row") })
        c["total_row"] = comp("total_row", "Row", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray { add("total_lbl"); add("total_val") })
            })
            put("distribution", "spaceBetween")
        })
        c["total_lbl"] = text("total_lbl", "TOTAL NET WORTH", "h5")
        c["total_val"] = text("total_val", "\$222,922.58",    "h4")

        return UiDefinition(surfaceId = "response_run2", root = "root", components = c)
    }

    /**
     * Adds a two-column account row to [c]:
     *   {prefix}_row (Row) ─── {prefix}_left (Column): name, num
     *                      └── {prefix}_right (Column): balance, change
     */
    private fun accountRow(
        prefix: String,
        name: String,
        num: String,
        balance: String,
        change: String,
        c: MutableMap<String, Component>
    ) {
        val rowId   = "${prefix}_row"
        val leftId  = "${prefix}_left"
        val nameId  = "${prefix}_name"
        val numId   = "${prefix}_num"
        val rightId = "${prefix}_right"
        val balId   = "${prefix}_bal"
        val chgId   = "${prefix}_chg"

        c[leftId] = comp(leftId, "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray { add(nameId); add(numId) })
            })
        })
        c[nameId] = text(nameId, name, "body")
        c[numId]  = text(numId,  num,  "caption")

        c[rightId] = comp(rightId, "Column", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray { add(balId); add(chgId) })
            })
        })
        c[balId] = text(balId, balance, "body")
        c[chgId] = text(chgId, change,  "caption")

        c[rowId] = comp(rowId, "Row", buildJsonObject {
            put("children", buildJsonObject {
                put("explicitList", buildJsonArray { add(leftId); add(rightId) })
            })
            put("distribution", "spaceBetween")
        })
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
