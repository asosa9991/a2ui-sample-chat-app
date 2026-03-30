import Foundation
import Combine

class DataContext: ObservableObject {
    @Published private(set) var data: [String: Any] = [:]

    func getString(path: String) -> String? {
        let val = resolve(path: path)
        if let s = val as? String { return s }
        if let n = val as? NSNumber { return n.stringValue }
        return nil
    }

    func getBoolean(path: String) -> Bool? {
        let val = resolve(path: path)
        if let b = val as? Bool { return b }
        if let n = val as? NSNumber { return n.boolValue }
        return nil
    }

    func update(path: String, value: String) {
        let keys = pathComponents(path)
        guard !keys.isEmpty else { return }
        data = setNestedValue(in: data, keys: keys, value: value)
    }

    func setData(_ newData: [String: Any]) {
        data = newData
    }

    private func resolve(path: String) -> Any? {
        let keys = pathComponents(path)
        var current: Any = data
        for key in keys {
            if let dict = current as? [String: Any], let next = dict[key] {
                current = next
            } else {
                return nil
            }
        }
        return current
    }

    private func pathComponents(_ path: String) -> [String] {
        path.split(separator: "/", omittingEmptySubsequences: true).map(String.init)
    }

    private func setNestedValue(in dict: [String: Any], keys: [String], value: Any) -> [String: Any] {
        var result = dict
        guard let first = keys.first else { return result }
        if keys.count == 1 {
            result[first] = value
        } else {
            let nested = (result[first] as? [String: Any]) ?? [:]
            result[first] = setNestedValue(in: nested, keys: Array(keys.dropFirst()), value: value)
        }
        return result
    }
}
