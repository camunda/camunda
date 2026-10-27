package operate

import (
	"context"
	"fmt"

	"github.com/prometheus/client_golang/prometheus"
	"go.uber.org/zap"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/common"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/esutil"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/utils"
)

const subsystem = "operate"

// Collector gathers metrics for Camunda Operate from Elasticsearch.
type Collector struct {
	esClient *esutil.Client
	logger   *zap.Logger

	rootProcessInstances          prometheus.Gauge
	rootProcessInstancesCompleted prometheus.Gauge
}

func New(esClient *esutil.Client, logger *zap.Logger, reg prometheus.Registerer) *Collector {
	c := &Collector{
		esClient: esClient,
		logger:   logger,
		rootProcessInstances: prometheus.NewGauge(prometheus.GaugeOpts{
			Namespace: common.Namespace,
			Subsystem: subsystem,
			Name:      "root_process_instances",
			Help:      "Number of Operate root process instance documents.",
		}),
		rootProcessInstancesCompleted: prometheus.NewGauge(prometheus.GaugeOpts{
			Namespace: common.Namespace,
			Subsystem: subsystem,
			Name:      "root_process_instances_completed",
			Help:      "Number of completed Operate root process instance documents.",
		}),
	}
	reg.MustRegister(c.rootProcessInstances, c.rootProcessInstancesCompleted)
	return c
}

func (c *Collector) Name() string { return "operate" }

func (c *Collector) Collect(ctx context.Context) error {
	const (
		index = "operate-list-view-*"
		// joinRelation=processInstance selects only root-level process instances.
		// Child instances (e.g. called sub-processes) have a different joinRelation value
		// and are excluded so we count each started process exactly once.
		query = `{"term": {"joinRelation": "processInstance"}}`
	)

	c.logger.Debug("Collecting Operate root process instances")
	completed, err := utils.CountCompleted(ctx, c.esClient, index, query)
	if err != nil {
		return fmt.Errorf("error while collecting Operate root process instances: %w", err)
	}

	c.rootProcessInstancesCompleted.Set(float64(completed.Completed))
	c.rootProcessInstances.Set(float64(completed.Total))

	return nil
}
