package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

type queryDocument struct {
	Queries []queryDefinition `json:"queries" yaml:"queries"`
}

type queryDefinition struct {
	Key         string `json:"key" yaml:"key"`
	Header      string `json:"header" yaml:"header"`
	Description string `json:"description" yaml:"description"`
	Value       string `json:"value" yaml:"value"`
	Query       string `json:"query" yaml:"query"`
	ValueLabel  string `json:"valueLabel" yaml:"valueLabel"`
}

func loadQueryDocument(queriesFile string) (queryDocument, error) {
	data, err := os.ReadFile(queriesFile)
	if err != nil {
		return queryDocument{}, fmt.Errorf("failed to read queries file %s: %w", queriesFile, err)
	}

	var document queryDocument
	if strings.EqualFold(filepath.Ext(queriesFile), ".json") {
		err = json.Unmarshal(data, &document)
	} else {
		err = yaml.Unmarshal(data, &document)
	}
	if err != nil {
		return queryDocument{}, fmt.Errorf("failed to read queries file %s: %w", queriesFile, err)
	}
	if err := validateQueryDocument(document); err != nil {
		return queryDocument{}, err
	}
	return document, nil
}

func validateQueryDocument(document queryDocument) error {
	if len(document.Queries) == 0 {
		return fmt.Errorf("invalid queries file:\nqueries must be a non-empty array")
	}

	seenKeys := map[string]bool{}
	var validationErrors []string
	for _, query := range document.Queries {
		if query.Key == "" {
			validationErrors = append(validationErrors, "each query entry must have a non-empty string key")
		} else if seenKeys[query.Key] {
			validationErrors = append(validationErrors, "duplicate query key: "+query.Key)
		} else {
			seenKeys[query.Key] = true
		}

		hasQuery := query.Query != ""
		hasValue := query.Value != ""
		if hasQuery == hasValue {
			key := query.Key
			if key == "" {
				key = "<missing key>"
			}
			validationErrors = append(validationErrors, fmt.Sprintf("query entry %s must set exactly one of query or value", key))
		}

		if query.ValueLabel != "" && !hasQuery {
			validationErrors = append(validationErrors, fmt.Sprintf("query entry %s sets valueLabel without query", query.Key))
		}
	}
	if len(validationErrors) > 0 {
		return fmt.Errorf("invalid queries file:\n%s", strings.Join(validationErrors, "\n"))
	}
	return nil
}

func substituteTemplates(document queryDocument, substitutions map[string]string) queryDocument {
	rendered := queryDocument{Queries: make([]queryDefinition, 0, len(document.Queries))}
	for _, query := range document.Queries {
		query.Key = substituteString(query.Key, substitutions)
		query.Header = substituteString(query.Header, substitutions)
		query.Description = substituteString(query.Description, substitutions)
		query.Value = substituteString(query.Value, substitutions)
		query.Query = substituteString(query.Query, substitutions)
		query.ValueLabel = substituteString(query.ValueLabel, substitutions)
		rendered.Queries = append(rendered.Queries, query)
	}
	return rendered
}

func substituteString(value string, substitutions map[string]string) string {
	rendered := value
	for placeholder, replacement := range substitutions {
		rendered = strings.ReplaceAll(rendered, placeholder, replacement)
	}
	return rendered
}
