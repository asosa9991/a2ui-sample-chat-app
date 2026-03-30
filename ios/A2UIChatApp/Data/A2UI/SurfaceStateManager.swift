import Foundation

class SurfaceStateManager {
    private var surfaceId: String = ""
    private var rootComponentId: String = ""
    private var components: [String: A2UIComponent] = [:]
    private var dataContents: [[String: String]] = []

    var hasSurface: Bool { !surfaceId.isEmpty && !components.isEmpty }

    func processOperation(_ json: String) {
        guard let data = json.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            print("[A2UI.Surface] ⚠️ Failed to parse op JSON: \(json.prefix(80))")
            return
        }

        if let beginRendering = dict["beginRendering"] as? [String: Any] {
            surfaceId = beginRendering["surfaceId"] as? String ?? ""
            rootComponentId = beginRendering["root"] as? String ?? ""
            components = [:]
            dataContents = []
            print("[A2UI.Surface] beginRendering surfaceId=\(surfaceId) root=\(rootComponentId)")
        } else if let surfaceUpdate = dict["surfaceUpdate"] as? [String: Any],
                  let comps = surfaceUpdate["components"] as? [[String: Any]] {
            for compDict in comps {
                guard let id = compDict["id"] as? String,
                      let component = compDict["component"] as? [String: Any] else { continue }
                components[id] = A2UIComponent(id: id, componentProperties: component)
            }
            print("[A2UI.Surface] surfaceUpdate +\(comps.count) components → total=\(components.count), hasSurface=\(hasSurface)")
        } else if let dataModelUpdate = dict["dataModelUpdate"] as? [String: Any],
                  let contents = dataModelUpdate["contents"] as? [[String: Any]] {
            for item in contents {
                var entry: [String: String] = [:]
                if let key = item["key"] as? String { entry["key"] = key }
                if let val = item["valueString"] as? String { entry["valueString"] = val }
                if let val = item["valueBoolean"] as? Bool { entry["valueBoolean"] = val ? "true" : "false" }
                if !entry.isEmpty { dataContents.append(entry) }
            }
            print("[A2UI.Surface] dataModelUpdate +\(contents.count) entries → total=\(dataContents.count)")
        } else if dict["deleteSurface"] != nil {
            print("[A2UI.Surface] deleteSurface")
            reset()
        } else {
            print("[A2UI.Surface] ⚠️ Unknown op keys: \(dict.keys.joined(separator: ", "))")
        }
    }

    func buildUiDefinition() -> UiDefinition? {
        guard hasSurface else { return nil }
        return UiDefinition(surfaceId: surfaceId, rootComponentId: rootComponentId, components: components)
    }

    func buildDataModelJson() -> [String: Any] {
        var result: [String: Any] = [:]
        for item in dataContents {
            guard let key = item["key"] else { continue }
            let keys = key.split(separator: "/", omittingEmptySubsequences: true).map(String.init)
            if let val = item["valueString"] {
                result = setNested(in: result, keys: keys, value: val)
            } else if let val = item["valueBoolean"] {
                result = setNested(in: result, keys: keys, value: val == "true")
            }
        }
        return result
    }

    private func reset() {
        surfaceId = ""
        rootComponentId = ""
        components = [:]
        dataContents = []
    }

    private func setNested(in dict: [String: Any], keys: [String], value: Any) -> [String: Any] {
        var result = dict
        guard let first = keys.first else { return result }
        if keys.count == 1 {
            result[first] = value
        } else {
            let nested = (result[first] as? [String: Any]) ?? [:]
            result[first] = setNested(in: nested, keys: Array(keys.dropFirst()), value: value)
        }
        return result
    }
}
