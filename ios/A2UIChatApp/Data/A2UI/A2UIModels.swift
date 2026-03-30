import Foundation

// A component in the A2UI tree
struct A2UIComponent {
    let id: String
    // e.g. ["Text": ["text": ["literalString": "Hello"], "usageHint": "body"]]
    let componentProperties: [String: Any]

    var widgetType: String {
        componentProperties.keys.first ?? ""
    }

    var props: [String: Any] {
        (componentProperties[widgetType] as? [String: Any]) ?? [:]
    }
}

// The full UI definition for a surface
struct UiDefinition {
    let surfaceId: String
    let rootComponentId: String
    let components: [String: A2UIComponent]
}

// UiEvent sent back to server
struct UiEvent {
    let surfaceId: String
    let eventType: String
    let name: String
    let sourceComponentId: String
    let path: String?
    let value: String?
    let context: [[String: String]]?
}

// DataReference kinds
enum DataReference {
    case literalString(String)
    case literalBoolean(Bool)
    case path(String)
    case componentRef(String)
    case none
}

// DataReferenceParser
enum DataReferenceParser {
    static func parse(_ dict: Any?) -> DataReference {
        guard let dict = dict as? [String: Any] else { return .none }
        if let s = dict["literalString"] as? String { return .literalString(s) }
        if let b = dict["literalBoolean"] as? Bool { return .literalBoolean(b) }
        if let p = dict["path"] as? String { return .path(p) }
        if let c = dict["componentId"] as? String { return .componentRef(c) }
        return .none
    }

    static func resolveString(_ dict: Any?, dataContext: DataContext) -> String {
        switch parse(dict) {
        case .literalString(let s): return s
        case .path(let p): return dataContext.getString(path: p) ?? ""
        default: return ""
        }
    }
}

// Children reference
enum ChildrenRef {
    case explicitList([String])
    case dynamicList(path: String, templateComponentId: String)
    case componentRef(String)
    case none
}

enum ChildrenRefParser {
    static func parse(_ value: Any?) -> ChildrenRef {
        if let dict = value as? [String: Any] {
            if let list = dict["explicitList"] as? [String] {
                return .explicitList(list)
            }
            if let compId = dict["componentId"] as? String {
                if let path = dict["path"] as? String {
                    return .dynamicList(path: path, templateComponentId: compId)
                }
                return .componentRef(compId)
            }
            if dict["path"] != nil {
                return .explicitList([])
            }
        }
        if let list = value as? [String] {
            return .explicitList(list)
        }
        return .none
    }
}
