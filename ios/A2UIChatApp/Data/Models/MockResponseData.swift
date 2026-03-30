import Foundation

struct MockResponseData {
    static func buildBrokerageActivityUiDefinition() -> UiDefinition {
        var comps: [String: A2UIComponent] = [:]

        // Root column
        comps["root"] = A2UIComponent(id: "root", componentProperties: [
            "Column": [
                "children": ["explicitList": ["header_card", "divider_top", "tx1_row", "divider1", "tx2_row", "divider2", "tx3_row", "divider3", "tx4_row", "divider4", "tx5_row"]]
            ]
        ])

        // Header card
        comps["header_card"] = A2UIComponent(id: "header_card", componentProperties: [
            "Card": ["child": ["componentId": "header_col"]]
        ])
        comps["header_col"] = A2UIComponent(id: "header_col", componentProperties: [
            "Column": [
                "children": ["explicitList": ["account_label", "balance_label"]],
                "spacing": "form"
            ]
        ])
        comps["account_label"] = A2UIComponent(id: "account_label", componentProperties: [
            "Text": ["text": ["literalString": "Brokerage Account"], "usageHint": "h3"]
        ])
        comps["balance_label"] = A2UIComponent(id: "balance_label", componentProperties: [
            "Text": ["text": ["literalString": "$48,291.73"], "usageHint": "h2"]
        ])

        // Dividers
        for name in ["divider_top", "divider1", "divider2", "divider3", "divider4"] {
            comps[name] = A2UIComponent(id: name, componentProperties: ["Divider": [:]])
        }

        // Transaction rows: (prefix, title, subtitle, date, amount)
        let transactions: [(String, String, String, String, String)] = [
            ("tx1", "Buy AAPL", "10 shares @ $187.50", "Mar 25", "-$1,875.00"),
            ("tx2", "Sell TSLA", "5 shares @ $245.30", "Mar 24", "+$1,226.50"),
            ("tx3", "Dividend · VTI", "Q1 2026 dividend", "Mar 22", "+$45.20"),
            ("tx4", "Buy MSFT", "3 shares @ $412.00", "Mar 20", "-$1,236.00"),
            ("tx5", "ACH Deposit", "Transfer from Chase ••4521", "Mar 18", "+$5,000.00"),
        ]

        for (prefix, title, subtitle, date, amount) in transactions {
            let rowId = "\(prefix)_row"
            let leftColId = "\(prefix)_left"
            let titleId = "\(prefix)_title"
            let subtitleId = "\(prefix)_subtitle"
            let dateId = "\(prefix)_date"
            let amountId = "\(prefix)_amount"

            comps[rowId] = A2UIComponent(id: rowId, componentProperties: [
                "Row": [
                    "children": ["explicitList": [leftColId, amountId]],
                    "distribution": "spaceBetween"
                ]
            ])
            comps[leftColId] = A2UIComponent(id: leftColId, componentProperties: [
                "Column": [
                    "children": ["explicitList": [titleId, subtitleId, dateId]],
                    "spacing": ""
                ]
            ])
            comps[titleId] = A2UIComponent(id: titleId, componentProperties: [
                "Text": ["text": ["literalString": title], "usageHint": "body"]
            ])
            comps[subtitleId] = A2UIComponent(id: subtitleId, componentProperties: [
                "Text": ["text": ["literalString": subtitle], "usageHint": "caption"]
            ])
            comps[dateId] = A2UIComponent(id: dateId, componentProperties: [
                "Text": ["text": ["literalString": date], "usageHint": "caption"]
            ])
            comps[amountId] = A2UIComponent(id: amountId, componentProperties: [
                "Text": ["text": ["literalString": amount], "usageHint": "body"]
            ])
        }

        return UiDefinition(surfaceId: "mock_brokerage", rootComponentId: "root", components: comps)
    }
}
