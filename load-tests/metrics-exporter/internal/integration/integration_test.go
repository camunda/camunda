//go:build integration

package integration_test

import (
	"context"
	"strings"
	"testing"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/testutil"
	"go.uber.org/zap"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/esutil"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/indices"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/operate"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/optimize"
)

var backends = []searchBackend{
	{
		name:  "Elasticsearch",
		image: "docker.elastic.co/elasticsearch/elasticsearch:8.16.0",
		env: map[string]string{
			"discovery.type":         "single-node",
			"xpack.security.enabled": "false",
			"ES_JAVA_OPTS":           "-Xms512m -Xmx512m",
		},
	},
	{
		name:  "OpenSearch",
		image: "opensearchproject/opensearch:2.19.0",
		env: map[string]string{
			"discovery.type":          "single-node",
			"DISABLE_SECURITY_PLUGIN": "true",
			"OPENSEARCH_JAVA_OPTS":    "-Xms512m -Xmx512m",
		},
	},
}

func TestOperateCollector_shouldCountOnlyRootProcessInstances(t *testing.T) {
	for _, backend := range backends {
		backend := backend
		t.Run(backend.name, func(t *testing.T) {
			// given: a search instance with an Operate list-view index containing both
			// processInstance and task documents, some of the former with endDate set.
			// "operate-list-view-8.3.0_" matches the wildcard index "operate-list-view-*".
			// 2 running process instances (no endDate) + 3 completed (endDate present) = 5 total.
			// 2 task documents (joinRelation != "processInstance") must not be counted at all.
			baseURL := startSearchContainer(t, backend)
			// Create the index with joinRelation as keyword so that the term query
			// matches exactly. Without explicit mapping, dynamic mapping would assign it
			// the "text" type, which breaks term queries on camelCase values.
			createIndex(t, baseURL, "operate-list-view-8.3.0_", `{
				"mappings": {
					"properties": {
						"joinRelation": {"type": "keyword"}
					}
				}
			}`)
			loadFixtures(t, baseURL, "operate-list-view-8.3.0_", "fixtures/operate.ndjson")

			reg := prometheus.NewRegistry()
			c := operate.New(esutil.NewClient(baseURL), zap.NewNop(), reg)

			// when
			if err := c.Collect(context.Background()); err != nil {
				t.Fatalf("Collect returned error: %v", err)
			}

			// then: the 2 task documents are excluded; only 5 root process instances are
			// counted, 3 of which are completed.
			if err := testutil.GatherAndCompare(reg, strings.NewReader(`
# HELP camunda_loadtest_operate_root_process_instances Number of Operate root process instance documents.
# TYPE camunda_loadtest_operate_root_process_instances gauge
camunda_loadtest_operate_root_process_instances 5
# HELP camunda_loadtest_operate_root_process_instances_completed Number of completed Operate root process instance documents.
# TYPE camunda_loadtest_operate_root_process_instances_completed gauge
camunda_loadtest_operate_root_process_instances_completed 3
`),
				"camunda_loadtest_operate_root_process_instances",
				"camunda_loadtest_operate_root_process_instances_completed",
			); err != nil {
				t.Error(err)
			}
		})
	}
}

func TestIndicesCollector_shouldExposeShardConfiguration(t *testing.T) {
	for _, backend := range backends {
		backend := backend
		t.Run(backend.name, func(t *testing.T) {
			// given: a fresh cluster with two test indices with distinct shard configs.
			// replica=0 avoids yellow health on single-node clusters.
			baseURL := startSearchContainer(t, backend)
			createIndex(t, baseURL, "test-shards-alpha", `{
				"settings": {
					"number_of_shards": 3,
					"number_of_replicas": 0
				}
			}`)
			createIndex(t, baseURL, "test-shards-beta", `{
				"settings": {
					"number_of_shards": 5,
					"number_of_replicas": 2
				}
			}`)

			reg := prometheus.NewRegistry()
			c := indices.New(esutil.NewClient(baseURL), zap.NewNop(), reg)

			// when
			if err := c.Collect(context.Background()); err != nil {
				t.Fatalf("Collect returned error: %v", err)
			}

			// then: scan gathered metrics for our test indices; other system indices may
			// also be present so we do a partial check rather than exact comparison.
			gathered, err := reg.Gather()
			if err != nil {
				t.Fatalf("Gather returned error: %v", err)
			}

			type shardInfo struct{ primary, replica string }
			found := map[string]shardInfo{}
			for _, mf := range gathered {
				if mf.GetName() != "camunda_loadtest_index_shards" {
					continue
				}
				for _, m := range mf.GetMetric() {
					var idx, pri, rep string
					for _, lp := range m.GetLabel() {
						switch lp.GetName() {
						case "index":
							idx = lp.GetValue()
						case "primary":
							pri = lp.GetValue()
						case "replica":
							rep = lp.GetValue()
						}
					}
					found[idx] = shardInfo{pri, rep}
				}
			}

			want := map[string]shardInfo{
				"test-shards-alpha": {"3", "0"},
				"test-shards-beta":  {"5", "2"},
			}
			for name, cfg := range want {
				got, ok := found[name]
				if !ok {
					t.Errorf("index %q not found in metrics; got: %v", name, found)
					continue
				}
				if got.primary != cfg.primary {
					t.Errorf("index %q: primary = %q, want %q", name, got.primary, cfg.primary)
				}
				if got.replica != cfg.replica {
					t.Errorf("index %q: replica = %q, want %q", name, got.replica, cfg.replica)
				}
			}
		})
	}
}

func TestOptimizeCollector_shouldCountProcessInstancesByEndDate(t *testing.T) {
	for _, backend := range backends {
		backend := backend
		t.Run(backend.name, func(t *testing.T) {
			// given: a search instance with an Optimize process-instance index containing
			// documents with and without an endDate field.
			// "optimize-process-instance-TestProcess" matches the wildcard "optimize-process-instance-*".
			// 2 running (no endDate) + 4 completed (endDate present) = 6 total.
			baseURL := startSearchContainer(t, backend)
			loadFixtures(t, baseURL, "optimize-process-instance-testprocess", "fixtures/optimize.ndjson")

			reg := prometheus.NewRegistry()
			c := optimize.New(esutil.NewClient(baseURL), zap.NewNop(), reg)

			// when
			if err := c.Collect(context.Background()); err != nil {
				t.Fatalf("Collect returned error: %v", err)
			}

			// then: all 6 documents are counted; 4 have endDate and are considered completed.
			if err := testutil.GatherAndCompare(reg, strings.NewReader(`
# HELP camunda_loadtest_optimize_process_instances Number of all Optimize process instance documents.
# TYPE camunda_loadtest_optimize_process_instances gauge
camunda_loadtest_optimize_process_instances 6
# HELP camunda_loadtest_optimize_process_instances_completed Number of completed Optimize process instance documents (endDate field present).
# TYPE camunda_loadtest_optimize_process_instances_completed gauge
camunda_loadtest_optimize_process_instances_completed 4
`),
				"camunda_loadtest_optimize_process_instances",
				"camunda_loadtest_optimize_process_instances_completed",
			); err != nil {
				t.Error(err)
			}
		})
	}
}
