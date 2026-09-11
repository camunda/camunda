"""Shared user-facing error types."""


class ReportError(Exception):
    """User-facing script error."""


class MissingMetric(Exception):
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason
