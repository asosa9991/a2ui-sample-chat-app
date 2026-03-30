import Foundation

struct Message: Identifiable {
    let id: String
    var content: String
    let sender: Sender
    let timestamp: Date
    var isLoading: Bool = false
    var uiDefinition: UiDefinition? = nil
    var dataModelJson: [String: Any]? = nil

    init(
        id: String = UUID().uuidString,
        content: String,
        sender: Sender,
        timestamp: Date = Date(),
        isLoading: Bool = false,
        uiDefinition: UiDefinition? = nil,
        dataModelJson: [String: Any]? = nil
    ) {
        self.id = id
        self.content = content
        self.sender = sender
        self.timestamp = timestamp
        self.isLoading = isLoading
        self.uiDefinition = uiDefinition
        self.dataModelJson = dataModelJson
    }
}
