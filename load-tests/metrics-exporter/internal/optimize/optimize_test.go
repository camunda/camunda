package optimize

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/testutil"
	"go.uber.org/zap"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/esutil"
	internaltestutil "github.com/camunda/camunda/load-tests/metrics-exporter/internal/testutil"
)

func Test_shouldQueryCorrectIndex(t *testing.T) {
	// given
	var gotPath string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		_, _ = w.Write([]byte(`{"hits":{"total":{"value":0}},"aggregations":{"completed":{"doc_count":0}}}`))
	}))
	t.Cleanup(srv.Close)

	// when
	c := New(esutil.NewClient(srv.URL), zap.NewNop(), prometheus.NewRegistry())
	if err := c.Collect(context.Background()); err != nil {
		t.Fatalf("Collect returned error: %v", err)
	}

	// then
	if !strings.HasPrefix(gotPath, "/optimize-process-instance-") {
		t.Errorf("request path = %q, want optimize-process-instance-* index", gotPath)
	}
}

func Test_shouldSetGauges(t *testing.T) {
	// given
	srv := internaltestutil.NewSearchServer(t, `{"hits":{"total":{"value":3}},"aggregations":{"completed":{"doc_count":1}}}`, http.StatusOK)

	// when
	c := New(esutil.NewClient(srv.URL), zap.NewNop(), prometheus.NewRegistry())
	if err := c.Collect(context.Background()); err != nil {
		t.Fatalf("Collect returned error: %v", err)
	}

	// then
	if got := testutil.ToFloat64(c.processInstances); got != 3 {
		t.Errorf("processInstances = %v, want 3", got)
	}
	if got := testutil.ToFloat64(c.processInstancesCompleted); got != 1 {
		t.Errorf("processInstancesCompleted = %v, want 1", got)
	}
}
