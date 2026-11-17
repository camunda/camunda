package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/exporter"
)

func main() {
	var (
		cfg      exporter.Config
		logLevel string
	)
	flag.StringVar(&cfg.ListenAddr, "web.listen-address", ":9600", "Address to listen on for HTTP requests.")
	flag.StringVar(&cfg.ElasticsearchConfig.Address, "es.addresses", "http://localhost:9200", "Elasticsearch endpoint.")
	flag.DurationVar(&cfg.ScrapeInterval, "scrape.interval", 30*time.Second, "Interval between Elasticsearch scrapes.")
	flag.StringVar(&logLevel, "log.level", "info", "Log level: debug, info, warn, error.")
	flag.Parse()

	logger, err := newLogger(logLevel)
	if err != nil {
		fmt.Fprintf(os.Stderr, "failed to build logger: %v\n", err)
		os.Exit(1)
	}
	defer func() { _ = logger.Sync() }()

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	if err := exporter.Run(ctx, cfg, logger); err != nil {
		logger.Error("exporter exited with error", zap.Error(err))
		os.Exit(1)
	}
}

func newLogger(level string) (*zap.Logger, error) {
	var lvl zapcore.Level
	if err := lvl.UnmarshalText([]byte(level)); err != nil {
		lvl = zapcore.InfoLevel
	}
	// Default to JSON logging
	cfg := zap.NewProductionConfig()
	cfg.Level = zap.NewAtomicLevelAt(lvl)
	cfg.EncoderConfig.TimeKey = "timestamp"
	cfg.EncoderConfig.EncodeTime = zapcore.ISO8601TimeEncoder
	return cfg.Build()
}
