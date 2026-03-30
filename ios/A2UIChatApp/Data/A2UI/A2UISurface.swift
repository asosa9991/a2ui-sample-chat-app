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
            return AnyView(EmptyView())
        }
        let widgetType = component.widgetType
        let props = component.props

        if let renderer = catalog.renderer(for: widgetType) {
            let buildChild: (String) -> AnyView = { childId in
                self.buildComponentAnyView(id: childId)
            }
            return renderer(id, props, buildChild, dataContext, onEvent)
        }
        return AnyView(EmptyView())
    }
}
