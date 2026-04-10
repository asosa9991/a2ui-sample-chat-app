"""
Keyword-based intent classification for template routing.
Mirrors MockChatRepository.kt intent matching logic.

Triggers are loaded dynamically from template JSON files at import time via
``_load_rules()``. Each template declares ``intentTriggers.exact`` (list of
keyword-pairs) and ``intentTriggers.keywords`` (list of substring tokens).

Priority order (preserved from original implementation):
  1. Exact matches — all keywords in the phrase must appear in the message.
     Ties broken by specificity (more keywords = higher priority, checked first).
  2. Keyword matches — any single keyword is a substring of the message.
     Ordered by template file name (deterministic).
  3. None → plain text fallback.
"""
import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

logger = logging.getLogger("intent_router")


@dataclass
class IntentMatch:
    """Result of intent classification."""
    template_id: str
    data_id: str
    confidence: str  # "exact" | "keyword"


# ── Internal rule representation ─────────────────────────────────────────────

@dataclass
class _ExactRule:
    keywords: list[str]   # ALL must appear in normalised message
    template_id: str

@dataclass
class _KeywordRule:
    token: str            # single substring that must appear in normalised message
    template_id: str


# ── Rule loading ──────────────────────────────────────────────────────────────

def _load_rules(
    templates_dir: str | None = None,
) -> tuple[list[_ExactRule], list[_KeywordRule]]:
    """
    Scan *templates_dir* for *.json files and build intent rules from
    ``intentTriggers`` blocks.  Falls back to empty lists on any error.

    Returns ``(exact_rules, keyword_rules)`` where exact_rules are sorted
    descending by keyword-count (most-specific first).
    """
    if templates_dir is None:
        templates_dir = str(Path(__file__).parent / "templates")

    exact_rules: list[_ExactRule] = []
    keyword_rules: list[_KeywordRule] = []

    tdir = Path(templates_dir)
    if not tdir.exists():
        logger.error("IntentRouter: templates dir not found: %s", tdir.absolute())
        return exact_rules, keyword_rules

    for f in sorted(tdir.glob("*.json")):
        try:
            with open(f) as fh:
                template = json.load(fh)

            # Validate top-level structure — must be a dict, not a list or scalar
            if not isinstance(template, dict):
                logger.warning(
                    "IntentRouter: skipping %s — expected dict, got %s",
                    f.name, type(template).__name__,
                )
                continue

            tid = template.get("templateId", f.stem)
            triggers = template.get("intentTriggers", {})

            if triggers is None or not isinstance(triggers, dict):
                logger.warning(
                    "IntentRouter: skipping intentTriggers in %s — expected dict, got %s",
                    f.name, type(triggers).__name__,
                )
                continue

            # Exact phrase rules (each entry is a list of strings, ALL must appear in message)
            for phrase in triggers.get("exact", []):
                if not isinstance(phrase, list) or not phrase:
                    continue
                # Validate every keyword in the phrase is a string
                bad_kws = [kw for kw in phrase if not isinstance(kw, str)]
                if bad_kws:
                    logger.warning(
                        "IntentRouter: skipping exact entry in %s — non-string keyword(s): %r",
                        f.name, bad_kws,
                    )
                    continue
                exact_rules.append(_ExactRule(keywords=[kw.lower() for kw in phrase], template_id=tid))

            # Keyword / substring rules
            for token in triggers.get("keywords", []):
                if not isinstance(token, str):
                    logger.warning(
                        "IntentRouter: skipping keyword entry in %s — expected str, got %r",
                        f.name, token,
                    )
                    continue
                if token:
                    keyword_rules.append(_KeywordRule(token=token.lower(), template_id=tid))

        except Exception as exc:
            logger.warning("IntentRouter: error processing %s: %s", f.name, exc)
            continue

    # Sort exact rules by descending specificity (more keywords checked first)
    exact_rules.sort(key=lambda r: len(r.keywords), reverse=True)

    logger.info(
        "IntentRouter loaded %d exact rules, %d keyword rules from %s",
        len(exact_rules), len(keyword_rules), tdir,
    )
    return exact_rules, keyword_rules


# Module-level rules — built once at import time from the bundled templates dir.
_EXACT_RULES, _KEYWORD_RULES = _load_rules()


def reload(templates_dir: str | None = None) -> None:
    """Hot-reload intent rules (e.g. after a designer template save)."""
    global _EXACT_RULES, _KEYWORD_RULES
    _EXACT_RULES, _KEYWORD_RULES = _load_rules(templates_dir)


# ── Classification ────────────────────────────────────────────────────────────

def classify(message: str) -> Optional[IntentMatch]:
    """
    Classify *message* into a template intent.
    Returns ``None`` if no intent matches (plain text fallback).
    """
    normalized = message.lower().strip()

    # 1. Exact matches (most-specific-first — sorted by keyword count desc)
    for rule in _EXACT_RULES:
        if all(kw in normalized for kw in rule.keywords):
            logger.info(
                "Intent: %s (exact: %s)", rule.template_id, "+".join(rule.keywords)
            )
            return IntentMatch(
                template_id=rule.template_id,
                data_id=rule.template_id,
                confidence="exact",
            )

    # 2. Keyword / substring matches (order follows template file sort order)
    for rule in _KEYWORD_RULES:
        if rule.token in normalized:
            logger.info("Intent: %s (keyword: %s)", rule.template_id, rule.token)
            return IntentMatch(
                template_id=rule.template_id,
                data_id=rule.template_id,
                confidence="keyword",
            )

    logger.info("No intent match for: %.60s", message)
    return None
