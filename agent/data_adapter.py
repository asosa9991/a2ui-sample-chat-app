"""
DataAdapter abstraction — pluggable data fetching for template rendering.

Provides:
  DataAdapter        — abstract base class
  MockDataAdapter    — reads from data/{template_id}.json files (default behaviour)
  ApiDataAdapter     — stub that logs intent to call a real API, falls back to mock

Usage:
    from data_adapter import MockDataAdapter
    adapter = MockDataAdapter(data_dir="/path/to/agent/data")
    data = adapter.fetch("transaction_history", user_id="u123")
"""
import json
import logging
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Optional

logger = logging.getLogger("data_adapter")


class DataAdapter(ABC):
    """Abstract base class for template data fetching."""

    @abstractmethod
    def fetch(self, template_id: str, user_id: Optional[str] = None) -> Optional[dict]:
        """
        Fetch data for *template_id*.

        Parameters
        ----------
        template_id:
            The template whose data is needed (matches the stem of the data file).
        user_id:
            Optional user identifier — used by real API adapters to scope the
            request to the authenticated user.  Ignored by MockDataAdapter.

        Returns
        -------
        dict | None
            Parsed data dict, or None if not found / unavailable.
        """
        ...


class MockDataAdapter(DataAdapter):
    """
    Reads pre-built mock data from ``data_dir/{template_id}.json``.

    This is the default adapter used in development and for the pre-approved
    demo templates.  It replicates the historical ``self.data.get(data_id)``
    behaviour in TemplateRenderer so that all existing tests keep passing.

    Parameters
    ----------
    data_dir:
        Absolute or relative path to the directory containing mock JSON files.
    """

    def __init__(self, data_dir: str) -> None:
        self._data_dir = Path(data_dir)
        self._cache: dict[str, dict] = {}
        self._load_all()

    def _load_all(self) -> None:
        if not self._data_dir.exists():
            logger.error("MockDataAdapter: data dir not found: %s", self._data_dir.absolute())
            return
        for f in sorted(self._data_dir.glob("*.json")):
            try:
                with open(f) as fh:
                    self._cache[f.stem] = json.load(fh)
                logger.debug("MockDataAdapter: loaded %s", f.name)
            except (json.JSONDecodeError, OSError) as exc:
                logger.error("MockDataAdapter: failed to load %s: %s", f, exc)

    def reload(self) -> None:
        """Discard cache and re-read all files (called after hot-reload)."""
        self._cache.clear()
        self._load_all()
        logger.info("MockDataAdapter: reloaded %d data sets", len(self._cache))

    def fetch(self, template_id: str, user_id: Optional[str] = None) -> Optional[dict]:
        data = self._cache.get(template_id)
        if data is None:
            logger.warning(
                "MockDataAdapter: data not found for template_id=%s (available: %s)",
                template_id,
                list(self._cache.keys()),
            )
        return data


class ApiDataAdapter(DataAdapter):
    """
    Stub adapter for future real-API integration.

    Currently falls back to MockDataAdapter for all requests while logging
    the intent so that the wiring can be verified without a live backend.

    Parameters
    ----------
    templates_dir:
        Path to template JSON files (used to read ``dataSchema.source``).
    data_dir:
        Path to mock data files (fallback).
    """

    def __init__(self, templates_dir: str, data_dir: str) -> None:
        self._templates_dir = Path(templates_dir)
        self._mock = MockDataAdapter(data_dir=data_dir)

    def _get_template_source(self, template_id: str) -> str:
        """Read dataSchema.source from the template file, defaulting to 'mock'."""
        tfile = self._templates_dir / f"{template_id}.json"
        try:
            with open(tfile) as fh:
                tmpl = json.load(fh)
            return tmpl.get("dataSchema", {}).get("source", "mock")
        except (OSError, json.JSONDecodeError):
            return "mock"

    def fetch(self, template_id: str, user_id: Optional[str] = None) -> Optional[dict]:
        source = self._get_template_source(template_id)
        logger.info(
            "ApiDataAdapter: would call API for template_id=%s user_id=%s (source=%s) — falling back to mock",
            template_id,
            user_id,
            source,
        )
        return self._mock.fetch(template_id, user_id)
