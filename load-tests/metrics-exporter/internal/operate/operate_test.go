package operate

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/testutil"
	"go.uber.org/zap"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/esutil"
)

func newSearchServer(t *testing.T, body string, status int) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		reqBody, _ := io.ReadAll(r.Body)
		if !json.Valid(reqBody) {
			t.Errorf("request body is not valid JSON: %s", reqBody)
		}
		w.WriteHeader(status)
		_, _ = w.Write([]byte(body))
	}))
	t.Cleanup(srv.Close)
	return srv
}

func TestCollector_QueriesCorrectIndex(t *testing.T) {
	var gotPath string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		_, _ = w.Write([]byte(`{"hits":{"total":{"value":0}},"aggregations":{"completed":{"doc_count":0}}}`))
	}))
	t.Cleanup(srv.Close)

	c := New(esutil.NewClient(srv.URL), zap.NewNop(), prometheus.NewRegistry())
	if err := c.Collect(context.Background()); err != nil {
		t.Fatalf("Collect returned error: %v", err)
	}
	if !strings.HasPrefix(gotPath, "/operate-list-view-") {
		t.Errorf("request path = %q, want operate-list-view-* index", gotPath)
	}
}

func TestCollector_SetsGauges(t *testing.T) {
	srv := newSearchServer(t, `{"hits":{"total":{"value":7}},"aggregations":{"completed":{"doc_count":4}}}`, http.StatusOK)
	c := New(esutil.NewClient(srv.URL), zap.NewNop(), prometheus.NewRegistry())
	if err := c.Collect(context.Background()); err != nil {
		t.Fatalf("Collect returned error: %v", err)
	}
	if got := testutil.ToFloat64(c.rootProcessInstances); got != 7 {
		t.Errorf("rootProcessInstances = %v, want 7", got)
	}
	if got := testutil.ToFloat64(c.rootProcessInstancesCompleted); got != 4 {
		t.Errorf("rootProcessInstancesCompleted = %v, want 4", got)
	}
}
