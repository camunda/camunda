package optimize

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
	if !strings.HasPrefix(gotPath, "/optimize-process-instance-") {
		t.Errorf("request path = %q, want optimize-process-instance-* index", gotPath)
	}
}

func TestCollector_SetsGauges(t *testing.T) {
	srv := newSearchServer(t, `{"hits":{"total":{"value":3}},"aggregations":{"completed":{"doc_count":1}}}`, http.StatusOK)
	c := New(esutil.NewClient(srv.URL), zap.NewNop(), prometheus.NewRegistry())
	if err := c.Collect(context.Background()); err != nil {
		t.Fatalf("Collect returned error: %v", err)
	}
	if got := testutil.ToFloat64(c.processInstances); got != 3 {
		t.Errorf("processInstances = %v, want 3", got)
	}
	if got := testutil.ToFloat64(c.processInstancesCompleted); got != 1 {
		t.Errorf("processInstancesCompleted = %v, want 1", got)
	}
}
