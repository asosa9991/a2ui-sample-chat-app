"""
Keyword-based intent classification for template routing.
Mirrors MockChatRepository.kt intent matching logic.
"""
import logging
import re
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

    Priority order (matches MockChatRepository.kt):
    1. "last" + "transaction" → transaction_history
    2. "account" + "balance" → account_balances
    3. Any word in BROKERAGE_TRIGGERS → brokerage_activity
    4. None → plain text response
    """
    normalized = message.lower().strip()
    words = set(re.split(r'\W+', normalized))

    # Priority 1: Transaction history (requires both keywords)
    if "last" in normalized and "transaction" in normalized:
        logger.info("Intent: transaction_history (exact: last+transaction)")
        return IntentMatch(template_id="transaction_history", data_id="transaction_history", confidence="exact")

    # Priority 2: Account balances (requires both keywords)
    if "account" in normalized and "balance" in normalized:
        logger.info("Intent: account_balances (exact: account+balance)")
        return IntentMatch(template_id="account_balances", data_id="account_balances", confidence="exact")

    # Priority 3: Generic brokerage (any trigger word)
    matched = words & BROKERAGE_TRIGGERS
    if matched:
        logger.info("Intent: brokerage_activity (keyword: %s)", matched)
        return IntentMatch(template_id="brokerage_activity", data_id="brokerage_activity", confidence="keyword")

    logger.info("No intent match for: %.60s", message)
    return None
