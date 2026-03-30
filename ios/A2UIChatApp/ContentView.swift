import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = ChatViewModel()

    var body: some View {
        ChatScreen(viewModel: viewModel)
    }
}
