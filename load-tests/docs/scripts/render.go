package main

import (
	"bytes"
	"encoding/csv"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

func renderReport(report report, outputFormat string, includeHeader bool, missingValue string) (string, error) {
	if outputFormat == "json" {
		rendered, err := json.MarshalIndent(report, "", "  ")
		if err != nil {
			return "", err
		}
		return string(rendered), nil
	}

	var output bytes.Buffer
	writer := csv.NewWriter(&output)
	if outputFormat == "tsv" {
		writer.Comma = '\t'
	}
	writer.UseCRLF = false

	if includeHeader {
		if err := writer.Write(report.Headers); err != nil {
			return "", err
		}
	}

	row := make([]string, 0, len(report.Columns))
	for _, column := range report.Columns {
		row = append(row, formatMetricValue(report.Metrics.get(column), missingValue))
	}
	if err := writer.Write(row); err != nil {
		return "", err
	}
	writer.Flush()
	if err := writer.Error(); err != nil {
		return "", err
	}
	return strings.TrimSuffix(output.String(), "\n"), nil
}

func formatMetricValue(value any, missingValue string) string {
	switch typedValue := value.(type) {
	case nil:
		return missingValue
	case string:
		return typedValue
	case int:
		return strconv.Itoa(typedValue)
	case float64:
		return strconv.FormatFloat(typedValue, 'f', -1, 64)
	default:
		return fmt.Sprint(typedValue)
	}
}

func (metrics orderedMetrics) get(key string) any {
	for _, entry := range metrics {
		if entry.key == key {
			return entry.value
		}
	}
	return nil
}

func (metrics orderedMetrics) MarshalJSON() ([]byte, error) {
	var output bytes.Buffer
	output.WriteByte('{')
	for index, entry := range metrics {
		if index > 0 {
			output.WriteByte(',')
		}
		key, err := json.Marshal(entry.key)
		if err != nil {
			return nil, err
		}
		value, err := json.Marshal(entry.value)
		if err != nil {
			return nil, err
		}
		output.Write(key)
		output.WriteByte(':')
		output.Write(value)
	}
	output.WriteByte('}')
	return output.Bytes(), nil
}
