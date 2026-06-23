package elasticsearch

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
)

func newCatServer(t *testing.T, body string) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(body))
	}))
	t.Cleanup(srv.Close)
	return srv
}

func Test_shouldSetGaugeForEachIndex(t *testing.T) {
	// given
	srv := newCatServer(t, `[{"index":"idx-a","pri":"3","rep":"1"},{"index":"idx-b","pri":"1","rep":"0"}]`)
	reg := prometheus.NewRegistry()
	c := New(esutil.NewClient(srv.URL), zap.NewNop(), reg)

	// when
	if err := c.Collect(context.Background()); err != nil {
		t.Fatalf("Collect returned error: %v", err)
	}

	// then
	if err := testutil.GatherAndCompare(reg, strings.NewReader(`
# HELP camunda_loadtest_elasticsearch_index_shards Number of shards per index. The shard_type label is either "primary" or "replica".
# TYPE camunda_loadtest_elasticsearch_index_shards gauge
camunda_loadtest_elasticsearch_index_shards{index="idx-a",shard_type="primary"} 3
camunda_loadtest_elasticsearch_index_shards{index="idx-a",shard_type="replica"} 1
camunda_loadtest_elasticsearch_index_shards{index="idx-b",shard_type="primary"} 1
camunda_loadtest_elasticsearch_index_shards{index="idx-b",shard_type="replica"} 0
`), "camunda_loadtest_elasticsearch_index_shards"); err != nil {
		t.Error(err)
	}
}

func Test_shouldProduceNoMetricsWhenNoIndicesExist(t *testing.T) {
	// given
	srv := newCatServer(t, `[]`)
	reg := prometheus.NewRegistry()
	c := New(esutil.NewClient(srv.URL), zap.NewNop(), reg)

	// when
	if err := c.Collect(context.Background()); err != nil {
		t.Fatalf("Collect returned error: %v", err)
	}

	// then: no gauge series should be emitted
	count, err := testutil.GatherAndCount(reg, "camunda_loadtest_elasticsearch_index_shards")
	if err != nil {
		t.Fatalf("GatherAndCount returned error: %v", err)
	}
	if count != 0 {
		t.Errorf("expected 0 metrics, got %d", count)
	}
}

func Test_shouldRemoveStaleSeriesOnSubsequentCollect(t *testing.T) {
	// given: first collect returns two indices; second collect returns only one
	// (simulating idx-b being deleted between scrapes).
	callCount := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if callCount == 0 {
			_, _ = w.Write([]byte(`[{"index":"idx-a","pri":"3","rep":"1"},{"index":"idx-b","pri":"1","rep":"0"}]`))
		} else {
			_, _ = w.Write([]byte(`[{"index":"idx-a","pri":"3","rep":"1"}]`))
		}
		callCount++
	}))
	t.Cleanup(srv.Close)

	reg := prometheus.NewRegistry()
	c := New(esutil.NewClient(srv.URL), zap.NewNop(), reg)

	// when
	if err := c.Collect(context.Background()); err != nil {
		t.Fatalf("first Collect returned error: %v", err)
	}
	if err := c.Collect(context.Background()); err != nil {
		t.Fatalf("second Collect returned error: %v", err)
	}

	// then: only idx-a remains; idx-b is gone
	if err := testutil.GatherAndCompare(reg, strings.NewReader(`
# HELP camunda_loadtest_elasticsearch_index_shards Number of shards per index. The shard_type label is either "primary" or "replica".
# TYPE camunda_loadtest_elasticsearch_index_shards gauge
camunda_loadtest_elasticsearch_index_shards{index="idx-a",shard_type="primary"} 3
camunda_loadtest_elasticsearch_index_shards{index="idx-a",shard_type="replica"} 1
`), "camunda_loadtest_elasticsearch_index_shards"); err != nil {
		t.Error(err)
	}
}

func Test_shouldReturnErrorWhenElasticsearchFails(t *testing.T) {
	// given
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	t.Cleanup(srv.Close)

	c := New(esutil.NewClient(srv.URL), zap.NewNop(), prometheus.NewRegistry())

	// when
	err := c.Collect(context.Background())

	// then
	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

func Test_shouldReportModuleName(t *testing.T) {
	c := New(esutil.NewClient("http://localhost:9200"), zap.NewNop(), prometheus.NewRegistry())
	if got := c.Name(); got != "index" {
		t.Errorf("Name() = %q, want %q", got, "index")
	}
}
