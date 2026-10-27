package exporter

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/collectors"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.uber.org/zap"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/esutil"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/operate"
	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/optimize"
)

type ElasticsearchConfig struct {
	Address string
}

// metricsPath is the HTTP path under which Prometheus metrics are exposed.
const metricsPath = "/metrics"

// Config holds the runtime configuration for the exporter.
type Config struct {
	ListenAddr          string
	ElasticsearchConfig ElasticsearchConfig
	ScrapeInterval      time.Duration
}

// Run starts the exporter and blocks until ctx is cancelled or the HTTP server fails.
func Run(ctx context.Context, cfg Config, logger *zap.Logger) error {
	if cfg.ScrapeInterval <= 0 {
		return fmt.Errorf("scrape interval must be positive, got %s", cfg.ScrapeInterval)
	}

	esClient := esutil.NewClient(cfg.ElasticsearchConfig.Address)

	reg := prometheus.NewRegistry()
	reg.MustRegister(
		collectors.NewProcessCollector(collectors.ProcessCollectorOpts{}),
	)

	modules := []Module{
		optimize.New(esClient, logger.Named("optimize"), reg),
		operate.New(esClient, logger.Named("operate"), reg),
	}

	collector := NewCollector(modules, cfg.ScrapeInterval, logger, reg)
	go collector.Run(ctx)

	mux := http.NewServeMux()
	mux.Handle(metricsPath, promhttp.HandlerFor(reg, promhttp.HandlerOpts{Registry: reg}))
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`<html><body><h1>Exporter</h1><a href="` + metricsPath + `">Metrics</a></body></html>`))
	})

	srv := &http.Server{
		Addr:    cfg.ListenAddr,
		Handler: mux,
	}

	serverErr := make(chan error, 1)
	go func() {
		logger.Info("starting http server", zap.String("addr", cfg.ListenAddr), zap.String("metrics_path", metricsPath))
		serverErr <- srv.ListenAndServe()
	}()

	select {
	case <-ctx.Done():
		logger.Info("shutdown signal received")
	case err := <-serverErr:
		if !errors.Is(err, http.ErrServerClosed) {
			return fmt.Errorf("http server: %w", err)
		}
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		return fmt.Errorf("http server shutdown: %w", err)
	}
	return nil
}
