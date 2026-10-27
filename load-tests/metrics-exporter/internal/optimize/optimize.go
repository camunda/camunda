package optimize

import (
	"context"
	"fmt"

	"github.com/prometheus/client_golang/prometheus"
	"go.uber.org/zap"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/common"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/esutil"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/utils"
)

const subsystem = "optimize"

// Collector gathers metrics for Camunda Optimize from Elasticsearch.
type Collector struct {
	esClient *esutil.Client
	logger   *zap.Logger

	processInstancesCompleted prometheus.Gauge
	processInstances          prometheus.Gauge
}

func New(esClient *esutil.Client, logger *zap.Logger, reg prometheus.Registerer) *Collector {
	c := &Collector{
		esClient: esClient,
		logger:   logger,
		processInstancesCompleted: prometheus.NewGauge(prometheus.GaugeOpts{
			Namespace: common.Namespace,
			Subsystem: subsystem,
			Name:      "process_instances_completed",
			Help:      "Number of completed Optimize process instance documents (endDate field present).",
		}),
		processInstances: prometheus.NewGauge(prometheus.GaugeOpts{
			Namespace: common.Namespace,
			Subsystem: subsystem,
			Name:      "process_instances",
			Help:      "Number of all Optimize process instance documents.",
		}),
	}
	reg.MustRegister(c.processInstancesCompleted, c.processInstances)
	return c
}

func (c *Collector) Name() string { return "optimize" }

func (c *Collector) Collect(ctx context.Context) error {
	const (
		// Optimize stores process instances in per-process definition indices following the
		// pattern "optimize-process-instance-<process definition name>".
		// The wildcard covers all of them.
		index = "optimize-process-instance-*"
		// The query to match all interesting documents.
		query = `{"match_all": {}}`
	)

	c.logger.Debug("Collecting Optimize process instances")
	completed, err := utils.CountCompleted(ctx, c.esClient, index, query)
	if err != nil {
		return fmt.Errorf("error while counting completed Optimize process instances: %w", err)
	}

	c.processInstancesCompleted.Set(float64(completed.Completed))
	c.processInstances.Set(float64(completed.Total))
	return nil
}
