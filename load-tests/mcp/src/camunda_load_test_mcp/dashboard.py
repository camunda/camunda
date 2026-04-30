from datetime import datetime, timedelta, timezone

GRAFANA_BASE = (
    "https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe"
)


def grafana_url(namespace: str) -> str:
    return f"{GRAFANA_BASE}?var-namespace={namespace}"


def ttl_expiry_str(started_at: str, ttl_days: int) -> str:
    dt = datetime.fromisoformat(started_at.replace("Z", "+00:00"))
    expiry = dt + timedelta(days=ttl_days)
    return expiry.strftime("%Y-%m-%d")
