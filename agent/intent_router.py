"""
Keyword-based intent classification for template routing.
Mirrors MockChatRepository.kt intent matching logic.
"""
import logging
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger("intent_router")

@dataclass
class IntentMatch:
    """Result of intent classification."""
    template_id: str
    data_id: str
    confidence: str  # "exact" | "keyword"

BROKERAGE_TRIGGERS = {
    "account", "transaction", "transactions", "activity", "portfolio",
    "balance", "brokerage", "trades", "holdings", "stocks"
}

def classify(message: str) -> Optional[IntentMatch]:
    """
    Classify user message into a template intent.
    Returns None if no intent matches (plain text fallback).

    Priority order:
    1. Exact: "last" + "transaction"/"transac" → transaction_history
    2. Exact: "account" + "balance"            → account_balances
    3. Keyword: "transaction"/"transac" in msg  → transaction_history
    4. Keyword: "balance" in msg                → account_balances
    5. Keyword: any brokerage trigger substring  → brokerage_activity
    6. None → plain text response
    """
    normalized = message.lower().strip()

    # Priority 1: Transaction history (exact — both keywords present)
    if "last" in normalized and ("transaction" in normalized or "transa" in normalized):
        logger.info("Intent: transaction_history (exact: last+transaction)")
        return IntentMatch(template_id="transaction_history", data_id="transaction_history", confidence="exact")

    # Priority 2: Account balances (exact — both keywords present)
    if "account" in normalized and "balance" in normalized:
        logger.info("Intent: account_balances (exact: account+balance)")
        return IntentMatch(template_id="account_balances", data_id="account_balances", confidence="exact")

    # Priority 3: Transaction keyword (substring handles plurals & typos like "transansactions")
    if "transaction" in normalized or "transa" in normalized:
        logger.info("Intent: transaction_history (keyword: transaction)")
        return IntentMatch(template_id="transaction_history", data_id="transaction_history", confidence="keyword")

    # Priority 4: Balance keyword (substring handles "balance", "balances")
    if "balance" in normalized:
        logger.info("Intent: account_balances (keyword: balance)")
        return IntentMatch(template_id="account_balances", data_id="account_balances", confidence="keyword")

    # Priority 5: Generic brokerage (substring match against trigger words)
    matched = [t for t in BROKERAGE_TRIGGERS if t in normalized]
    if matched:
        logger.info("Intent: brokerage_activity (keyword: %s)", matched)
        return IntentMatch(template_id="brokerage_activity", data_id="brokerage_activity", confidence="keyword")

    logger.info("No intent match for: %.60s", message)
    return None
