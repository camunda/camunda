// Package elasticsearch exposes internal Elasticsearch metrics
package elasticsearch

import (
	"context"
	"fmt"
	"strconv"

	"github.com/prometheus/client_golang/prometheus"
	"go.uber.org/zap"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/common"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/esutil"
)

const namespace = common.Namespace + "_elasticsearch"

// Collector gathers per-index shard configuration metrics from Elasticsearch.
type Collector struct {
	esClient *esutil.Client
	logger   *zap.Logger

	shards *prometheus.GaugeVec
}

func New(esClient *esutil.Client, logger *zap.Logger, reg prometheus.Registerer) *Collector {
	c := &Collector{
		esClient: esClient,
		logger:   logger,
		shards: prometheus.NewGaugeVec(prometheus.GaugeOpts{
			Namespace: namespace,
			Name:      "index_shards",
			Help:      "Number of shards per index. The shard_type label is either \"primary\" or \"replica\".",
		}, []string{"index", "shard_type"}),
	}
	reg.MustRegister(c.shards)
	return c
}

func (c *Collector) Name() string { return "index" }

func (c *Collector) Collect(ctx context.Context) error {
	c.logger.Debug("Collecting index shard configuration")

	indices, err := c.esClient.CatIndices(ctx)
	if err != nil {
		return fmt.Errorf("fetch index shard info: %w", err)
	}

	// Reset before setting to drop stale series for indices deleted since the last scrape.
	c.shards.Reset()
	for _, idx := range indices {
		primary, err := strconv.ParseFloat(idx.Primary, 64)
		if err != nil {
			c.logger.Warn("failed to parse primary shard count", zap.String("index", idx.Index), zap.String("value", idx.Primary), zap.Error(err))
			continue
		}
		replica, err := strconv.ParseFloat(idx.Replicas, 64)
		if err != nil {
			c.logger.Warn("failed to parse replica shard count", zap.String("index", idx.Index), zap.String("value", idx.Replicas), zap.Error(err))
			continue
		}
		c.shards.WithLabelValues(idx.Index, "primary").Set(primary)
		c.shards.WithLabelValues(idx.Index, "replica").Set(replica)
	}

	return nil
}
