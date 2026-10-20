"""Shared user-facing error types."""


class PrometheusError(Exception):
    """Raised when failing to reach Prometheus endpoint."""


class ReportError(Exception):
    """User-facing script error."""


class MissingMetric(Exception):
    pass
