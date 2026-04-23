from camunda_load_test_mcp.dashboard import grafana_url, ttl_expiry_str


def test_grafana_url_contains_namespace():
    url = grafana_url("c8-my-test-20260416")
    assert "c8-my-test-20260416" in url
    assert url.startswith("https://dashboard.benchmark.camunda.cloud")


def test_ttl_expiry_str():
    started = "2026-04-16T10:00:00Z"
    result = ttl_expiry_str(started, ttl_days=1)
    assert result == "2026-04-17"


def test_ttl_expiry_str_multi_day():
    started = "2026-04-16T10:00:00Z"
    result = ttl_expiry_str(started, ttl_days=28)
    assert result == "2026-05-14"
