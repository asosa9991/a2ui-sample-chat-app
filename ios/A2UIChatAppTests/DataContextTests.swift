import XCTest
@testable import A2UIChatApp

final class DataContextTests: XCTestCase {

    func test_getString_simple() {
        let ctx = DataContext()
        ctx.setData(["name": "Alice", "count": "42"])
        XCTAssertEqual(ctx.getString(path: "/name"), "Alice")
        XCTAssertEqual(ctx.getString(path: "/count"), "42")
    }

    func test_getString_nested() {
        let ctx = DataContext()
        ctx.setData(["fields": ["first": ["value": "John"]]])
        XCTAssertEqual(ctx.getString(path: "/fields/first/value"), "John")
    }

    func test_getString_missing() {
        let ctx = DataContext()
        ctx.setData(["name": "Alice"])
        XCTAssertNil(ctx.getString(path: "/missing"))
        XCTAssertNil(ctx.getString(path: "/name/subkey"))
    }

    func test_update_setsValue() {
        let ctx = DataContext()
        ctx.setData(["x": "old"])
        ctx.update(path: "/x", value: "new")
        XCTAssertEqual(ctx.getString(path: "/x"), "new")
    }

    func test_getBoolean() {
        let ctx = DataContext()
        ctx.setData(["active": true, "inactive": false])
        XCTAssertEqual(ctx.getBoolean(path: "/active"), true)
        XCTAssertEqual(ctx.getBoolean(path: "/inactive"), false)
        XCTAssertNil(ctx.getBoolean(path: "/missing"))
    }

    func test_setData_replacesAll() {
        let ctx = DataContext()
        ctx.setData(["a": "1"])
        ctx.setData(["b": "2"])
        XCTAssertNil(ctx.getString(path: "/a"))
        XCTAssertEqual(ctx.getString(path: "/b"), "2")
    }
}
