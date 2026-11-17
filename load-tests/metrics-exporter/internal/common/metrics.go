// Package common holds shared constants used across all collector modules.
package common

// Namespace is the top-level Prometheus namespace for all metrics exposed by
// this exporter. Using "camunda_loadtest" to avoid clashing with metrics from
// the production Camunda exporter (zeebe/exporters).
const Namespace = "camunda_loadtest"
