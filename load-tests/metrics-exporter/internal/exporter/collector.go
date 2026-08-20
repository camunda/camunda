package exporter

import (
	"context"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"go.uber.org/zap"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/common"
)

// Module is a subsystem (Optimize, Operate, ...) that the exporter scrapes on
// a fixed interval. Modules own their business metrics; the orchestrator only
// tracks cross-cutting concerns (errors, last-scrape timestamp).
type Module interface {
	Name() string
	Collect(ctx context.Context) error
}

// Collector drives the per-module scrape loop.
type Collector struct {
	modules  []Module
	interval time.Duration
	logger   *zap.Logger

	scrapeErrors *prometheus.CounterVec
	lastScrape   *prometheus.GaugeVec
}

func NewCollector(modules []Module, interval time.Duration, logger *zap.Logger, reg prometheus.Registerer) *Collector {
	c := &Collector{
		modules:  modules,
		interval: interval,
		logger:   logger,
		scrapeErrors: prometheus.NewCounterVec(prometheus.CounterOpts{
			Namespace: common.Namespace,
			Name:      "scrape_errors_total",
			Help:      "Total number of failed Elasticsearch scrapes, by module.",
		}, []string{"module"}),
		lastScrape: prometheus.NewGaugeVec(prometheus.GaugeOpts{
			Namespace: common.Namespace,
			Name:      "last_scrape_timestamp_seconds",
			Help:      "Unix timestamp of the last successful scrape, by module.",
		}, []string{"module"}),
	}
	reg.MustRegister(c.scrapeErrors, c.lastScrape)
	return c
}

// Run blocks until ctx is cancelled, scraping every module on every tick.
func (c *Collector) Run(ctx context.Context) {
	c.scrape(ctx)

	ticker := time.NewTicker(c.interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			c.scrape(ctx)
		}
	}
}

func (c *Collector) scrape(ctx context.Context) {
	for _, m := range c.modules {
		name := m.Name()
		start := time.Now()

		if err := m.Collect(ctx); err != nil {
			c.scrapeErrors.WithLabelValues(name).Inc()
			c.logger.Error("module scrape failed", zap.String("module", name), zap.Error(err))
			continue
		}

		c.lastScrape.WithLabelValues(name).SetToCurrentTime()
		c.logger.Debug("module scrape complete", zap.String("module", name), zap.Duration("duration", time.Since(start)))
	}
}
