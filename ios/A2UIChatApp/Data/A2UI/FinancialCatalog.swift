import SwiftUI

typealias WidgetRenderer = (
    _ componentId: String,
    _ data: [String: Any],
    _ buildChild: @escaping (String) -> AnyView,
    _ dataContext: DataContext,
    _ onEvent: @escaping (UiEvent) -> Void
) -> AnyView

struct FinancialCatalog {
    private var renderers: [String: WidgetRenderer] = [:]

    init() {
        renderers["Text"] = Self.renderText
        renderers["Row"] = Self.renderRow
        renderers["Column"] = Self.renderColumn
        renderers["Card"] = Self.renderCard
        renderers["Divider"] = Self.renderDivider
        renderers["Button"] = Self.renderButton
        renderers["TextField"] = Self.renderTextField
        renderers["List"] = Self.renderList
        renderers["DonutChart"] = Self.renderDonutChart
        renderers["BarChart"] = Self.renderBarChart
    }

    func renderer(for widgetType: String) -> WidgetRenderer? {
        renderers[widgetType]
    }

    // MARK: - Text
    static func renderText(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(FinancialTextView(componentId: componentId, data: data, dataContext: dataContext))
    }

    // MARK: - Row
    static func renderRow(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(FinancialRowView(componentId: componentId, data: data, buildChild: buildChild, dataContext: dataContext, onEvent: onEvent))
    }

    // MARK: - Column
    static func renderColumn(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(FinancialColumnView(componentId: componentId, data: data, buildChild: buildChild, dataContext: dataContext, onEvent: onEvent))
    }

    // MARK: - Card
    static func renderCard(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(FinancialCardView(componentId: componentId, data: data, buildChild: buildChild, dataContext: dataContext, onEvent: onEvent))
    }

    // MARK: - Divider
    static func renderDivider(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(Divider().background(AppColors.cardBorderSubtle))
    }

    // MARK: - Button
    static func renderButton(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(FinancialButtonView(componentId: componentId, data: data, dataContext: dataContext, onEvent: onEvent))
    }

    // MARK: - TextField
    static func renderTextField(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(FinancialTextFieldView(componentId: componentId, data: data, dataContext: dataContext, onEvent: onEvent))
    }

    // MARK: - List
    static func renderList(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(FinancialListView(componentId: componentId, data: data, buildChild: buildChild, dataContext: dataContext, onEvent: onEvent))
    }

    // MARK: - DonutChart
    static func renderDonutChart(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(FinancialDonutChartView(data: data, dataContext: dataContext))
    }

    // MARK: - BarChart
    static func renderBarChart(
        componentId: String,
        data: [String: Any],
        buildChild: @escaping (String) -> AnyView,
        dataContext: DataContext,
        onEvent: @escaping (UiEvent) -> Void
    ) -> AnyView {
        AnyView(FinancialBarChartView(data: data, dataContext: dataContext))
    }
}

// MARK: - ISO Date helper

private func formatISODate(_ raw: String) -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    if let date = formatter.date(from: raw) {
        let out = DateFormatter()
        out.dateFormat = "MMM d"
        return out.string(from: date)
    }
    return raw
}

// MARK: - Monetary sign helper

/// Returns +1 for positive monetary amounts, -1 for negative, 0 otherwise.
private func monetarySign(_ text: String) -> Int {
    let trimmed = text.trimmingCharacters(in: .whitespaces)
    if trimmed.contains("$") {
        if trimmed.hasPrefix("+") { return 1 }
        if trimmed.hasPrefix("-") { return -1 }
    }
    return 0
}

// MARK: - String regex helper

extension String {
    func matches(pattern: String) -> Bool {
        (try? NSRegularExpression(pattern: pattern).firstMatch(in: self, range: NSRange(self.startIndex..., in: self))) != nil
    }
}

// MARK: - FinancialTextView

struct FinancialTextView: View {
    let componentId: String
    let data: [String: Any]
    @ObservedObject var dataContext: DataContext

    private var resolvedText: String {
        let raw = DataReferenceParser.resolveString(data["text"], dataContext: dataContext)
        if raw.matches(pattern: "^\\d{4}-\\d{2}-\\d{2}$") {
            return formatISODate(raw)
        }
        return raw
    }

    private var usageHint: String {
        data["usageHint"] as? String ?? "body"
    }

    private var textColor: Color {
        let sign = monetarySign(resolvedText)
        if sign > 0 { return AppColors.positiveText }
        if sign < 0 { return AppColors.negativeText }
        if usageHint == "caption" { return AppColors.onSurfaceMuted }
        return AppColors.onSurface
    }

    private var resolvedFont: Font {
        switch usageHint {
        case "h1": return .largeTitle
        case "h2": return .title
        case "h3": return .title2
        case "h4": return .headline
        case "h5": return .subheadline
        case "caption": return .caption
        default: return .body
        }
    }

    private var isMoney: Bool { monetarySign(resolvedText) != 0 }

    var body: some View {
        Text(resolvedText)
            .font(resolvedFont)
            .foregroundColor(textColor)
            .fontWeight(isMoney ? .semibold : nil)
            .multilineTextAlignment(isMoney ? .trailing : .leading)
            .frame(maxWidth: isMoney ? nil : .infinity, alignment: isMoney ? .trailing : .leading)
            .preference(key: AccentColorPreferenceKey.self, value: isMoney ? textColor : .clear)
    }
}

// MARK: - FinancialRowView

struct FinancialRowView: View {
    let componentId: String
    let data: [String: Any]
    let buildChild: (String) -> AnyView
    @ObservedObject var dataContext: DataContext
    let onEvent: (UiEvent) -> Void

    @State private var accentColor: Color = .clear

    private var distribution: String {
        data["distribution"] as? String ?? ""
    }

    private var childIds: [String] {
        switch ChildrenRefParser.parse(data["children"]) {
        case .explicitList(let ids): return ids
        case .componentRef(let id): return [id]
        default: return []
        }
    }

    var body: some View {
        if distribution == "spaceBetween" {
            transactionRow
        } else {
            genericRow
        }
    }

    private var transactionRow: some View {
        HStack(spacing: 0) {
            Rectangle()
                .fill(accentColor)
                .frame(width: 3)

            HStack(alignment: .center, spacing: 0) {
                if childIds.count > 1 {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(Array(childIds.dropLast().enumerated()), id: \.offset) { _, id in
                            buildChild(id)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Spacer(minLength: 8)

                    if let lastId = childIds.last {
                        amountView(for: lastId)
                    }
                } else {
                    ForEach(Array(childIds.enumerated()), id: \.offset) { _, id in
                        buildChild(id)
                    }
                }
            }
            .padding(.vertical, 13)
            .padding(.leading, 12)
            .padding(.trailing, 16)
        }
        .fixedSize(horizontal: false, vertical: true)
        .onPreferenceChange(AccentColorPreferenceKey.self) { color in
            accentColor = color
        }
    }

    private var genericRow: some View {
        HStack(spacing: 8) {
            ForEach(Array(childIds.enumerated()), id: \.offset) { _, id in
                buildChild(id)
            }
        }
    }

    @ViewBuilder
    private func amountView(for id: String) -> some View {
        buildChild(id)
    }
}

// MARK: - AccentColor preference key (for row ↔ amount signaling)

struct AccentColorPreferenceKey: PreferenceKey {
    static var defaultValue: Color = .clear
    static func reduce(value: inout Color, nextValue: () -> Color) {
        let next = nextValue()
        if next != .clear { value = next }
    }
}

// MARK: - FinancialColumnView

struct FinancialColumnView: View {
    let componentId: String
    let data: [String: Any]
    let buildChild: (String) -> AnyView
    @ObservedObject var dataContext: DataContext
    let onEvent: (UiEvent) -> Void

    private var spacing: CGFloat {
        switch data["spacing"] as? String {
        case "form": return 16
        case "fieldGroup": return 4
        default: return 2
        }
    }

    private var alignment: HorizontalAlignment {
        switch data["alignment"] as? String {
        case "center": return .center
        case "end": return .trailing
        default: return .leading
        }
    }

    private var childIds: [String] {
        switch ChildrenRefParser.parse(data["children"]) {
        case .explicitList(let ids): return ids
        case .componentRef(let id): return [id]
        default: return []
        }
    }

    var body: some View {
        VStack(alignment: alignment, spacing: spacing) {
            ForEach(Array(childIds.enumerated()), id: \.offset) { _, id in
                buildChild(id)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - FinancialCardView

struct FinancialCardView: View {
    let componentId: String
    let data: [String: Any]
    let buildChild: (String) -> AnyView
    @ObservedObject var dataContext: DataContext
    let onEvent: (UiEvent) -> Void

    private var childId: String? {
        if let child = data["child"] as? [String: Any] {
            return child["componentId"] as? String
        }
        return nil
    }

    var body: some View {
        if let id = childId {
            buildChild(id)
                .padding(12)
                .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - FinancialButtonView

struct FinancialButtonView: View {
    let componentId: String
    let data: [String: Any]
    @ObservedObject var dataContext: DataContext
    let onEvent: (UiEvent) -> Void

    private var label: String {
        DataReferenceParser.resolveString(data["label"], dataContext: dataContext)
    }

    private var style: String {
        data["style"] as? String ?? "outlined"
    }

    private var isFilled: Bool { style == "filled" }

    private var actions: [[String: Any]] {
        data["actions"] as? [[String: Any]] ?? []
    }

    var body: some View {
        Button(action: handleTap) {
            Text(label)
                .font(.body.weight(.semibold))
                .foregroundColor(isFilled ? .white : AppColors.positiveGreen)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 52)
                .background(isFilled ? AppColors.positiveGreen : Color.clear)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isFilled ? Color.clear : AppColors.positiveGreen, lineWidth: isFilled ? 0 : 1.5)
                )
        }
        .buttonStyle(.plain)
    }

    private func handleTap() {
        guard let firstAction = actions.first else { return }
        let actionName = firstAction["name"] as? String ?? ""
        let contextArr = firstAction["context"] as? [[String: Any]] ?? []
        var contextMapped: [[String: String]] = []
        for item in contextArr {
            var entry: [String: String] = [:]
            if let k = item["key"] as? String { entry["key"] = k }
            if let p = item["path"] as? String {
                entry["path"] = p
                entry["value"] = dataContext.getString(path: p) ?? ""
            }
            contextMapped.append(entry)
        }
        let event = UiEvent(
            surfaceId: "",
            eventType: "action",
            name: actionName,
            sourceComponentId: componentId,
            path: nil,
            value: nil,
            context: contextMapped
        )
        onEvent(event)
    }
}

// MARK: - FinancialTextFieldView

struct FinancialTextFieldView: View {
    let componentId: String
    let data: [String: Any]
    @ObservedObject var dataContext: DataContext
    let onEvent: (UiEvent) -> Void

    @State private var text: String = ""
    @State private var isFocused: Bool = false
    @State private var errorMessage: String? = nil
    @FocusState private var focused: Bool

    private var placeholder: String {
        DataReferenceParser.resolveString(data["placeholder"], dataContext: dataContext)
    }

    private var textFieldType: String {
        data["textFieldType"] as? String ?? "text"
    }

    private var storagePath: String {
        if let textDict = data["text"] as? [String: Any], let path = textDict["path"] as? String {
            return path
        }
        return "/\(componentId)/value"
    }

    private var checks: [[String: Any]] {
        data["checks"] as? [[String: Any]] ?? []
    }

    private var borderColor: Color {
        if errorMessage != nil { return AppColors.negativeRed }
        if isFocused { return AppColors.positiveGreen }
        return AppColors.formFieldBorder
    }

    private var backgroundColor: Color {
        isFocused ? .white : AppColors.formFieldBackground
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Group {
                if textFieldType == "obscured" {
                    SecureField(placeholder, text: $text)
                } else if textFieldType == "longtext" {
                    TextEditor(text: $text)
                        .frame(minHeight: 80)
                } else {
                    TextField(placeholder, text: $text)
                        .keyboardType(textFieldType == "number" ? .decimalPad : .default)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(backgroundColor)
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(borderColor, lineWidth: isFocused ? 2 : 1)
            )
            .focused($focused)
            .onChange(of: focused) { _, newVal in
                isFocused = newVal
                if !newVal { validate() }
            }
            .onChange(of: text) { _, newVal in
                dataContext.update(path: storagePath, value: newVal)
                fireChangeEvent(value: newVal)
            }

            if let err = errorMessage {
                Text(err)
                    .font(.caption)
                    .foregroundColor(AppColors.negativeRed)
            }
        }
        .onAppear {
            let existing = dataContext.getString(path: storagePath) ?? ""
            text = existing
            if existing.isEmpty { dataContext.update(path: storagePath, value: "") }
        }
    }

    private func validate() {
        errorMessage = nil
        for check in checks {
            guard let call = check["call"] as? String else { continue }
            let args = check["args"] as? [String: Any] ?? [:]
            switch call {
            case "required":
                if text.trimmingCharacters(in: .whitespaces).isEmpty {
                    errorMessage = "This field is required"
                    return
                }
            case "numeric":
                if let num = Double(text) {
                    if let min = args["min"] as? Double, num < min {
                        errorMessage = "Minimum value is \(min)"
                        return
                    }
                    if let max = args["max"] as? Double, num > max {
                        errorMessage = "Maximum value is \(max)"
                        return
                    }
                } else if !text.isEmpty {
                    errorMessage = "Must be a number"
                    return
                }
            case "regex":
                if let pattern = args["pattern"] as? String, !text.isEmpty {
                    if !text.matches(pattern: pattern) {
                        let msg = args["message"] as? String ?? "Invalid format"
                        errorMessage = msg
                        return
                    }
                }
            default:
                break
            }
        }
    }

    private func fireChangeEvent(value: String) {
        let event = UiEvent(
            surfaceId: "",
            eventType: "dataChange",
            name: "textChanged",
            sourceComponentId: componentId,
            path: storagePath,
            value: value,
            context: nil
        )
        onEvent(event)
    }
}

// MARK: - FinancialListView

struct FinancialListView: View {
    let componentId: String
    let data: [String: Any]
    let buildChild: (String) -> AnyView
    @ObservedObject var dataContext: DataContext
    let onEvent: (UiEvent) -> Void

    private var childIds: [String] {
        switch ChildrenRefParser.parse(data["children"]) {
        case .explicitList(let ids): return ids
        case .componentRef(let id): return [id]
        default: return []
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(childIds.enumerated()), id: \.offset) { _, id in
                buildChild(id)
            }
        }
    }
}

// MARK: - FinancialDonutChartView

struct FinancialDonutChartView: View {
    let data: [String: Any]
    @ObservedObject var dataContext: DataContext

    private var title: String {
        DataReferenceParser.resolveString(data["title"], dataContext: dataContext)
    }
    private var centerLabel: String {
        DataReferenceParser.resolveString(data["centerLabel"], dataContext: dataContext)
    }
    private var centerSublabel: String {
        DataReferenceParser.resolveString(data["centerSublabel"], dataContext: dataContext)
    }
    private var showLegend: Bool {
        data["showLegend"] as? Bool ?? true
    }

    private struct Segment {
        let label: String
        let pct: Double
        let pctDisplay: String
        let color: Color
    }

    private func hintToColor(_ hint: String) -> Color {
        switch hint.lowercased() {
        case "blue":   return AppColors.primary
        case "teal":   return Color(hex: "#0D9488")
        case "green":  return AppColors.positiveText
        case "indigo": return Color(hex: "#4F46E5")
        case "amber":  return Color(hex: "#D97706")
        case "slate":  return AppColors.onSurfaceVariant
        case "rose":   return AppColors.negativeText
        case "cyan":   return Color(hex: "#0891B2")
        case "violet": return Color(hex: "#7C3AED")
        case "orange": return Color(hex: "#EA580C")
        case "lime":   return Color(hex: "#65A30D")
        default:       return AppColors.primary
        }
    }

    private var segments: [Segment] {
        guard let arr = data["segments"] as? [[String: Any]] else { return [] }
        return arr.compactMap { obj in
            guard let label = obj["label"] as? String else { return nil }
            let pct    = obj["pct"] as? Double ?? 0
            let pctD   = obj["pctDisplay"] as? String ?? "\(Int(pct))%"
            let hint   = obj["colorHint"] as? String ?? "blue"
            return Segment(label: label, pct: pct, pctDisplay: pctD, color: hintToColor(hint))
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !title.isEmpty {
                Text(title)
                    .font(.headline)
                    .foregroundColor(AppColors.onSurface)
            }

            ZStack {
                Canvas { context, size in
                    let strokeW: CGFloat = 38
                    let center = CGPoint(x: size.width / 2, y: size.height / 2)
                    let radius = min(size.width, size.height) / 2 - strokeW / 2
                    let gap = 2.0
                    var startDeg = -90.0
                    for seg in segments {
                        let sweep = seg.pct / 100.0 * 360.0 - gap
                        guard sweep > 0 else { continue }
                        let path = Path { p in
                            p.addArc(
                                center: center,
                                radius: radius,
                                startAngle: .degrees(startDeg),
                                endAngle: .degrees(startDeg + sweep),
                                clockwise: false
                            )
                        }
                        context.stroke(
                            path,
                            with: .color(seg.color),
                            style: StrokeStyle(lineWidth: strokeW, lineCap: .butt)
                        )
                        startDeg += sweep + gap
                    }
                }
                .frame(width: 180, height: 180)

                VStack(spacing: 2) {
                    if !centerLabel.isEmpty {
                        Text(centerLabel)
                            .font(.title3.weight(.bold))
                            .foregroundColor(AppColors.onSurface)
                    }
                    if !centerSublabel.isEmpty {
                        Text(centerSublabel)
                            .font(.caption)
                            .foregroundColor(AppColors.onSurfaceMuted)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 200)

            if showLegend && !segments.isEmpty {
                let cols = 2
                let rows = (segments.count + cols - 1) / cols
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(0..<rows, id: \.self) { r in
                        HStack(spacing: 0) {
                            ForEach(0..<cols, id: \.self) { c in
                                let idx = r * cols + c
                                if idx < segments.count {
                                    let seg = segments[idx]
                                    HStack(spacing: 6) {
                                        RoundedRectangle(cornerRadius: 2)
                                            .fill(seg.color)
                                            .frame(width: 8, height: 8)
                                        Text("\(seg.label)  \(seg.pctDisplay)")
                                            .font(.caption)
                                            .foregroundColor(AppColors.onSurfaceVariant)
                                            .lineLimit(1)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                } else {
                                    Spacer().frame(maxWidth: .infinity)
                                }
                            }
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - FinancialBarChartView

struct FinancialBarChartView: View {
    let data: [String: Any]
    @ObservedObject var dataContext: DataContext

    private var title: String {
        DataReferenceParser.resolveString(data["title"], dataContext: dataContext)
    }
    private var subtitle: String {
        DataReferenceParser.resolveString(data["subtitle"], dataContext: dataContext)
    }
    private var showValues: Bool {
        data["showValues"] as? Bool ?? true
    }

    private struct Bar {
        let label: String
        let valueDisplay: String
        let value: Double
        let direction: String
        var color: Color {
            switch direction.lowercased() {
            case "positive": return AppColors.positiveText
            case "negative": return AppColors.negativeText
            default:         return AppColors.primary
            }
        }
    }

    private var bars: [Bar] {
        guard let arr = data["bars"] as? [[String: Any]] else { return [] }
        return arr.compactMap { obj in
            guard let label = obj["label"] as? String else { return nil }
            let valD  = obj["valueDisplay"] as? String ?? ""
            let valN  = obj["value"] as? Double ?? 0
            let dir   = obj["direction"] as? String ?? "neutral"
            return Bar(label: label, valueDisplay: valD, value: valN, direction: dir)
        }
    }

    private var maxAbs: Double {
        bars.map { abs($0.value) }.max().flatMap { $0 > 0 ? $0 : nil } ?? 1
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !title.isEmpty {
                Text(title)
                    .font(.headline)
                    .foregroundColor(AppColors.onSurface)
            }
            if !subtitle.isEmpty {
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(AppColors.onSurfaceMuted)
            }
            Spacer().frame(height: 4)

            ForEach(Array(bars.enumerated()), id: \.offset) { _, bar in
                let fraction = CGFloat(min(abs(bar.value) / maxAbs, 1.0))
                HStack(spacing: 8) {
                    Text(bar.label)
                        .font(.caption)
                        .foregroundColor(AppColors.onSurface)
                        .frame(width: 52, alignment: .leading)
                        .lineLimit(1)

                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(bar.color.opacity(0.10))
                                .frame(maxWidth: .infinity)
                            RoundedRectangle(cornerRadius: 4)
                                .fill(bar.color.opacity(0.75))
                                .frame(width: geo.size.width * fraction)
                        }
                    }
                    .frame(height: 22)

                    if showValues {
                        Text(bar.valueDisplay)
                            .font(.caption.weight(.semibold))
                            .foregroundColor(bar.color)
                            .frame(width: 88, alignment: .trailing)
                            .lineLimit(1)
                    }
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
