import XCTest
@testable import A2UIChatApp

final class SurfaceStateManagerTests: XCTestCase {

    // MARK: - beginRendering

    func test_beginRendering_setsIds() throws {
        let mgr = SurfaceStateManager()
        let op = #"{"beginRendering": {"surfaceId": "response_abc", "root": "root_col"}}"#
        mgr.processOperation(op)
        XCTAssertFalse(mgr.hasSurface, "hasSurface must be false until components arrive")
        let uiDef = mgr.buildUiDefinition()
        XCTAssertNil(uiDef, "buildUiDefinition must return nil when components are empty")
    }

    // MARK: - surfaceUpdate

    func test_surfaceUpdate_populatesComponents() throws {
        let mgr = SurfaceStateManager()
        mgr.processOperation(#"{"beginRendering": {"surfaceId": "s1", "root": "root"}}"#)
        let surfaceOp = """
        {"surfaceUpdate": {"surfaceId": "s1", "components": [
            {"id": "root", "component": {"Column": {"children": {"explicitList": ["t1"]}}}},
            {"id": "t1", "component": {"Text": {"text": {"path": "/title"}, "usageHint": "h4"}}}
        ]}}
        """
        mgr.processOperation(surfaceOp)
        XCTAssertTrue(mgr.hasSurface)
        let uiDef = mgr.buildUiDefinition()
        XCTAssertNotNil(uiDef)
        XCTAssertEqual(uiDef?.surfaceId, "s1")
        XCTAssertEqual(uiDef?.rootComponentId, "root")
        XCTAssertEqual(uiDef?.components.count, 2)
    }

    func test_surfaceUpdate_chunkedBuildsFullMap() {
        let mgr = SurfaceStateManager()
        mgr.processOperation(#"{"beginRendering": {"surfaceId": "s2", "root": "root"}}"#)
        // Chunk 1
        mgr.processOperation(#"{"surfaceUpdate": {"surfaceId": "s2", "components": [{"id": "root", "component": {"Column": {"children": {"explicitList": ["a", "b"]}}}}]}}"#)
        // Chunk 2
        mgr.processOperation(#"{"surfaceUpdate": {"surfaceId": "s2", "components": [{"id": "a", "component": {"Text": {"text": {"path": "/a"}}}}, {"id": "b", "component": {"Text": {"text": {"path": "/b"}}}}]}}"#)
        XCTAssertEqual(mgr.buildUiDefinition()?.components.count, 3)
    }

    // MARK: - dataModelUpdate

    func test_dataModelUpdate_buildsNestedDict() {
        let mgr = SurfaceStateManager()
        mgr.processOperation(#"{"beginRendering": {"surfaceId": "s3", "root": "root"}}"#)
        let dataOp = """
        {"dataModelUpdate": {"surfaceId": "s3", "path": "", "contents": [
            {"key": "title", "valueString": "Trade History"},
            {"key": "t_amt_0", "valueString": "-$2,131.50"}
        ]}}
        """
        mgr.processOperation(dataOp)
        let data = mgr.buildDataModelJson()
        XCTAssertEqual(data["title"] as? String, "Trade History")
        XCTAssertEqual(data["t_amt_0"] as? String, "-$2,131.50")
    }

    func test_dataModelUpdate_boolean() {
        let mgr = SurfaceStateManager()
        mgr.processOperation(#"{"beginRendering": {"surfaceId": "s4", "root": "root"}}"#)
        mgr.processOperation(#"{"dataModelUpdate": {"surfaceId": "s4", "path": "", "contents": [{"key": "flag", "valueBoolean": true}]}}"#)
        let data = mgr.buildDataModelJson()
        XCTAssertEqual(data["flag"] as? Bool, true)
    }

    // MARK: - deleteSurface

    func test_deleteSurface_resetsState() {
        let mgr = SurfaceStateManager()
        mgr.processOperation(#"{"beginRendering": {"surfaceId": "s5", "root": "root"}}"#)
        mgr.processOperation(#"{"surfaceUpdate": {"surfaceId": "s5", "components": [{"id": "root", "component": {"Text": {"text": {"path": "/x"}}}}]}}"#)
        XCTAssertTrue(mgr.hasSurface)
        mgr.processOperation(#"{"deleteSurface": {}}"#)
        XCTAssertFalse(mgr.hasSurface)
        XCTAssertNil(mgr.buildUiDefinition())
    }

    // MARK: - invalid JSON

    func test_invalidJson_doesNotCrash() {
        let mgr = SurfaceStateManager()
        mgr.processOperation("not json at all")
        mgr.processOperation("{}")
        mgr.processOperation("")
        XCTAssertFalse(mgr.hasSurface)
    }

    // MARK: - Full pipeline (matches real server data)

    func test_fullPipeline_tradeHistory() {
        let mgr = SurfaceStateManager()

        // 1. beginRendering
        mgr.processOperation(#"{"beginRendering": {"surfaceId": "response_j989g5", "root": "root"}}"#)
        XCTAssertFalse(mgr.hasSurface)

        // 2. dataModelUpdate (real data from server log)
        let dataOp = """
        {"dataModelUpdate": {"surfaceId": "response_j989g5", "path": "", "contents": [
            {"key": "title", "valueString": "Trade History"},
            {"key": "period", "valueString": "Mar 23 – Mar 27, 2026"},
            {"key": "t_action_0", "valueString": "Buy NVDA · 15 shares"},
            {"key": "t_amt_0", "valueString": "-$2,131.50"},
            {"key": "t_amt_1", "valueString": "+$4,618.00"}
        ]}}
        """
        mgr.processOperation(dataOp)
        XCTAssertFalse(mgr.hasSurface, "hasSurface must remain false until components arrive")

        // 3. surfaceUpdate chunk 1
        let surfaceOp1 = """
        {"surfaceUpdate": {"surfaceId": "response_j989g5", "components": [
            {"id": "root", "component": {"Column": {"children": {"explicitList": ["hdr_card", "trades_list"]}}}},
            {"id": "hdr_card", "component": {"Card": {"child": "hdr_col"}}},
            {"id": "title", "component": {"Text": {"text": {"path": "/title"}, "usageHint": "h4"}}}
        ]}}
        """
        mgr.processOperation(surfaceOp1)
        XCTAssertTrue(mgr.hasSurface)

        // Check uiDefinition
        let uiDef = mgr.buildUiDefinition()
        XCTAssertNotNil(uiDef)
        XCTAssertEqual(uiDef?.rootComponentId, "root")

        // Check dataModel
        let data = mgr.buildDataModelJson()
        XCTAssertEqual(data["title"] as? String, "Trade History")
        XCTAssertEqual(data["t_amt_0"] as? String, "-$2,131.50")
        XCTAssertEqual(data["t_amt_1"] as? String, "+$4,618.00")

        // Check DataContext path resolution
        let ctx = DataContext()
        ctx.setData(data)
        XCTAssertEqual(ctx.getString(path: "/title"), "Trade History")
        XCTAssertEqual(ctx.getString(path: "/t_action_0"), "Buy NVDA · 15 shares")
        XCTAssertNil(ctx.getString(path: "/nonexistent"))
    }
}
