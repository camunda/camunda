package main

import (
	"errors"
	"fmt"
	"maps"
	"os"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"
)

var numberPattern = regexp.MustCompile(`^-?([0-9]+([.][0-9]+)?|[.][0-9]+)([eE][-+]?[0-9]+)?$`)

type metricEntry struct {
	key   string
	value any
}

type orderedMetrics []metricEntry

type report struct {
	Namespace       string         `json:"namespace"`
	DurationSeconds int            `json:"durationSeconds"`
	Start           *string        `json:"start"`
	End             *string        `json:"end"`
	Endpoint        string         `json:"endpoint"`
	GeneratedAt     string         `json:"generatedAt"`
	Columns         []string       `json:"columns"`
	Headers         []string       `json:"headers"`
	Metrics         orderedMetrics `json:"metrics"`
}

type missingMetricError struct {
	reason string
}

func (e missingMetricError) Error() string {
	return e.reason
}

func buildReport(opts options, document queryDocument, client prometheusAPI, generatedAt string, warningSink func(string)) (report, error) {
	substitutions := map[string]string{
		"$NAMESPACE":     opts.namespace,
		"$DURATION_S":    fmt.Sprintf("%ds", opts.durationSeconds),
		"$RATE_INTERVAL": opts.rateInterval,
		"$SAMPLE_STEP":   opts.sampleStep,
	}
	renderedDocument := substituteTemplates(document, substitutions)

	report := report{
		Namespace:       opts.namespace,
		DurationSeconds: opts.durationSeconds,
		Start:           nullableString(opts.startLabel),
		End:             nullableString(opts.endLabel),
		Endpoint:        opts.endpoint,
		GeneratedAt:     generatedAt,
		Columns:         []string{},
		Headers:         []string{},
		Metrics:         orderedMetrics{},
	}
	if report.GeneratedAt == "" {
		report.GeneratedAt = time.Now().UTC().Format("2006-01-02T15:04:05Z")
	}

	for _, query := range renderedDocument.Queries {
		report.Columns = append(report.Columns, query.Key)
		report.Headers = append(report.Headers, stringValueOrDefault(query.Header, query.Key))

		if query.Value != "" {
			report.Metrics = append(report.Metrics, metricEntry{key: query.Key, value: query.Value})
			continue
		}

		metricValue, err := queryMetricValue(client, query, warningSink)
		if err != nil {
			var missingMetricErr missingMetricError
			if errors.As(err, &missingMetricErr) {
				report.Metrics = append(report.Metrics, metricEntry{key: query.Key, value: nil})
				continue
			}
			warningSink(fmt.Sprintf("%s: query failed", query.Key))
			report.Metrics = append(report.Metrics, metricEntry{key: query.Key, value: nil})
			continue
		}
		report.Metrics = append(report.Metrics, metricEntry{key: query.Key, value: metricValue})
	}
	return report, nil
}

func queryMetricValue(client prometheusAPI, query queryDefinition, warningSink func(string)) (any, error) {
	response, err := client.query(query.Query)
	if err != nil {
		return nil, err
	}
	return extractMetricValue(response, query.ValueLabel, query.Key, warningSink)
}

func extractMetricValue(response prometheusResponse, valueLabel string, key string, warningSink func(string)) (any, error) {
	if response.Status != "success" {
		return nil, missingMetric(key, "Prometheus returned non-success status", warningSink)
	}

	if valueLabel != "" {
		valuesByLabel := map[string]bool{}
		for _, series := range response.Data.Result {
			if value := series.Metric[valueLabel]; value != "" {
				valuesByLabel[value] = true
			}
		}
		if len(valuesByLabel) == 0 {
			return nil, missingMetric(key, "no label sample", warningSink)
		}
		values := slices.Sorted(maps.Keys(valuesByLabel))
		return strings.Join(values, ", "), nil
	}

	rawValue := ""
	if len(response.Data.Result) > 0 && len(response.Data.Result[0].Value) > 1 {
		rawValue = sampleString(response.Data.Result[0].Value[1])
	}
	if !numberPattern.MatchString(rawValue) {
		return nil, missingMetric(key, "no numeric sample", warningSink)
	}
	return parseNumber(rawValue)
}

func missingMetric(key string, reason string, warningSink func(string)) error {
	warningSink(fmt.Sprintf("%s: %s", key, reason))
	return missingMetricError{reason: reason}
}

func warn(message string) {
	fmt.Fprintln(os.Stderr, message)
}

func parseNumber(rawValue string) (any, error) {
	if integerPattern.MatchString(rawValue) {
		return strconv.Atoi(rawValue)
	}
	return strconv.ParseFloat(rawValue, 64)
}

func nullableString(value string) *string {
	if value == "" {
		return nil
	}
	return &value
}

func stringValueOrDefault(value string, fallback string) string {
	if value != "" {
		return value
	}
	return fallback
}
