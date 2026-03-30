enum StreamEvent {
    case textContent(String)
    case a2uiOp(String)
    case token(String)
    case done(Message?)
    case error(Error)
}
