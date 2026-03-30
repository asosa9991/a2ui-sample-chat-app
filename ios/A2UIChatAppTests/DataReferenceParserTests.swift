import XCTest
@testable import A2UIChatApp

final class DataReferenceParserTests: XCTestCase {

    func test_parse_literalString() {
        let ref = DataReferenceParser.parse(["literalString": "hello"])
        if case .literalString(let s) = ref {
            XCTAssertEqual(s, "hello")
        } else {
            XCTFail("Expected literalString")
        }
    }

    func test_parse_path() {
        let ref = DataReferenceParser.parse(["path": "/title"])
        if case .path(let p) = ref {
            XCTAssertEqual(p, "/title")
        } else {
            XCTFail("Expected path")
        }
    }

    func test_parse_literalBoolean() {
        let ref = DataReferenceParser.parse(["literalBoolean": true])
        if case .literalBoolean(let b) = ref {
            XCTAssertTrue(b)
        } else {
            XCTFail("Expected literalBoolean")
        }
    }

    func test_parse_none() {
        XCTAssertEqual(DataReferenceParser.parse(nil), .none)
        XCTAssertEqual(DataReferenceParser.parse("string"), .none)
        XCTAssertEqual(DataReferenceParser.parse([:] as [String: Any]), .none)
    }

    func test_resolveString_literal() {
        let ctx = DataContext()
        let result = DataReferenceParser.resolveString(["literalString": "world"], dataContext: ctx)
        XCTAssertEqual(result, "world")
    }

    func test_resolveString_path() {
        let ctx = DataContext()
        ctx.setData(["title": "Trade History"])
        let result = DataReferenceParser.resolveString(["path": "/title"], dataContext: ctx)
        XCTAssertEqual(result, "Trade History")
    }

    func test_resolveString_missingPath_returnsEmpty() {
        let ctx = DataContext()
        let result = DataReferenceParser.resolveString(["path": "/nonexistent"], dataContext: ctx)
        XCTAssertEqual(result, "")
    }
}

extension DataReference: Equatable {
    public static func == (lhs: DataReference, rhs: DataReference) -> Bool {
        switch (lhs, rhs) {
        case (.none, .none): return true
        case (.literalString(let a), .literalString(let b)): return a == b
        case (.literalBoolean(let a), .literalBoolean(let b)): return a == b
        case (.path(let a), .path(let b)): return a == b
        case (.componentRef(let a), .componentRef(let b)): return a == b
        default: return false
        }
    }
}
