import XCTest
@testable import A2UIChatApp

final class FinancialCatalogTests: XCTestCase {

    func test_allExpectedWidgetTypesRegistered() {
        let catalog = FinancialCatalog()
        let expected = ["Text", "Row", "Column", "Card", "Divider", "Button", "TextField", "List"]
        for widgetType in expected {
            XCTAssertNotNil(catalog.renderer(for: widgetType), "Missing renderer for '\(widgetType)'")
        }
    }

    func test_unknownWidgetType_returnsNil() {
        let catalog = FinancialCatalog()
        XCTAssertNil(catalog.renderer(for: "UnknownWidget"))
        XCTAssertNil(catalog.renderer(for: ""))
    }

    func test_a2uiComponent_widgetType() {
        let comp = A2UIComponent(id: "t1", componentProperties: ["Text": ["text": ["literalString": "Hello"]]])
        XCTAssertEqual(comp.widgetType, "Text")
        XCTAssertNotNil(comp.props["text"])
    }

    func test_a2uiComponent_emptyProps() {
        let comp = A2UIComponent(id: "d1", componentProperties: ["Divider": [:] as [String: Any]])
        XCTAssertEqual(comp.widgetType, "Divider")
    }
}
