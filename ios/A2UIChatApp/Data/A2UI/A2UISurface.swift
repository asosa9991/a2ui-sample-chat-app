import SwiftUI

struct A2UISurface: View {
    let uiDefinition: UiDefinition
    @ObservedObject var dataContext: DataContext
    let onEvent: (UiEvent) -> Void
    let catalog: FinancialCatalog

    init(
        uiDefinition: UiDefinition,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void,
        catalog: FinancialCatalog = FinancialCatalog()
    ) {
        self.uiDefinition = uiDefinition
        self.dataContext = dataContext
        self.onEvent = onEvent
        self.catalog = catalog
    }

    var body: some View {
        buildComponentAnyView(id: uiDefinition.rootComponentId)
    }

    func buildComponentAnyView(id: String) -> AnyView {
        guard let component = uiDefinition.components[id] else {
            print("[A2UI.Render] ⚠️ Component '\(id)' not found in \(uiDefinition.components.keys.sorted())")
            return AnyView(EmptyView())
        }
        let widgetType = component.widgetType
        let props = component.props
        print("[A2UI.Render] rendering '\(id)' as '\(widgetType)'")

        if let renderer = catalog.renderer(for: widgetType) {
            let buildChild: (String) -> AnyView = { childId in
                self.buildComponentAnyView(id: childId)
            }
            return renderer(id, props, buildChild, dataContext, onEvent)
        }
        print("[A2UI.Render] ⚠️ No renderer for widgetType '\(widgetType)'")
        return AnyView(EmptyView())
    }
}
