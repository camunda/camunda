"""Shared user-facing error types."""


class ReportError(Exception):
    """User-facing script error."""


class MissingMetric(Exception):
    pass
