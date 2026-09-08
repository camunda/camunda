package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

type fakePrometheusClient struct {
	responses []any
	queries   []string
}

func (c *fakePrometheusClient) runtimeInfo() (prometheusResponse, error) {
	return prometheusResponse{Status: "success"}, nil
}

func (c *fakePrometheusClient) query(query string) (prometheusResponse, error) {
	c.queries = append(c.queries, query)
	response := c.responses[0]
	c.responses = c.responses[1:]
	if err, ok := response.(error); ok {
		return prometheusResponse{}, err
	}
	return response.(prometheusResponse), nil
}

func mustPrometheusResponse(t *testing.T, raw string) prometheusResponse {
	t.Helper()
	var response prometheusResponse
	if err := json.Unmarshal([]byte(raw), &response); err != nil {
		t.Fatalf("failed to parse Prometheus response fixture: %v", err)
	}
	return response
}

func TestExtractMetricValueShouldExtractNumericSample(t *testing.T) {
	response := mustPrometheusResponse(t, `{"status":"success","data":{"result":[{"value":[123,"42.5"]}]}}`)

	value, err := extractMetricValue(response, "", "throughput", func(string) {})

	if err != nil {
		t.Fatalf("expected metric value, got error %v", err)
	}
	if value != 42.5 {
		t.Fatalf("expected 42.5, got %#v", value)
	}
}

func TestExtractMetricValueShouldExtractSortedUniqueLabelValues(t *testing.T) {
	response := mustPrometheusResponse(t, `{"status":"success","data":{"result":[{"metric":{"image":"registry/camunda:2"}},{"metric":{"image":"registry/camunda:1"}},{"metric":{"image":"registry/camunda:2"}}]}}`)

	value, err := extractMetricValue(response, "image", "image", func(string) {})

	if err != nil {
		t.Fatalf("expected metric value, got error %v", err)
	}
	if value != "registry/camunda:1, registry/camunda:2" {
		t.Fatalf("unexpected label value: %#v", value)
	}
}

func TestExtractMetricValueShouldWarnWhenNumericSampleIsMissing(t *testing.T) {
	response := mustPrometheusResponse(t, `{"status":"success","data":{"result":[]}}`)
	var warnings []string

	_, err := extractMetricValue(response, "", "throughput", func(message string) {
		warnings = append(warnings, message)
	})

	var missingMetric missingMetricError
	if !errors.As(err, &missingMetric) {
		t.Fatalf("expected missing metric error, got %v", err)
	}
	if !slices.Equal(warnings, []string{"throughput: no numeric sample"}) {
		t.Fatalf("unexpected warnings: %#v", warnings)
	}
}

func TestExtractMetricValueShouldWarnWhenLabelSampleIsMissing(t *testing.T) {
	response := mustPrometheusResponse(t, `{"status":"success","data":{"result":[]}}`)
	var warnings []string

	_, err := extractMetricValue(response, "image", "image", func(message string) {
		warnings = append(warnings, message)
	})

	var missingMetric missingMetricError
	if !errors.As(err, &missingMetric) {
		t.Fatalf("expected missing metric error, got %v", err)
	}
	if !slices.Equal(warnings, []string{"image: no label sample"}) {
		t.Fatalf("unexpected warnings: %#v", warnings)
	}
}

func TestExtractMetricValueShouldWarnWhenPrometheusStatusIsNotSuccess(t *testing.T) {
	response := mustPrometheusResponse(t, `{"status":"error","data":{"result":[]}}`)
	var warnings []string

	_, err := extractMetricValue(response, "", "throughput", func(message string) {
		warnings = append(warnings, message)
	})

	var missingMetric missingMetricError
	if !errors.As(err, &missingMetric) {
		t.Fatalf("expected missing metric error, got %v", err)
	}
	if !slices.Equal(warnings, []string{"throughput: Prometheus returned non-success status"}) {
		t.Fatalf("unexpected warnings: %#v", warnings)
	}
}

func TestBuildReportShouldBuildReportWithoutNetworkSideEffects(t *testing.T) {
	opts := options{
		namespace:       "c8-ck-test",
		durationSeconds: 900,
		rateInterval:    "30s",
		sampleStep:      "15s",
		reportTemplate:  "camunda",
		endpoint:        "http://prometheus.example",
		outputFormat:    "json",
		includeHeader:   true,
		missingValue:    "NaN",
		queriesFile:     "queries.json",
	}
	queryDocument := queryDocument{
		Queries: []queryDefinition{
			{Key: "namespace", Header: "Namespace", Value: "c8-ck-test"},
			{Key: "throughput", Header: "Throughput", Query: `rate(total{namespace="c8-ck-test"}[30s])`},
			{Key: "image", Header: "Image", Query: "image_query[900s:15s]", ValueLabel: "image"},
			{Key: "missing", Header: "Missing", Query: "missing_query"},
		},
	}
	client := &fakePrometheusClient{
		responses: []any{
			mustPrometheusResponse(t, `{"status":"success","data":{"result":[{"value":[123,"10"]}]}}`),
			mustPrometheusResponse(t, `{"status":"success","data":{"result":[{"metric":{"image":"camunda:SNAPSHOT"}}]}}`),
			mustPrometheusResponse(t, `{"status":"success","data":{"result":[]}}`),
		},
	}
	var warnings []string

	report, err := buildReport(opts, queryDocument, client, "2026-09-07T18:00:00Z", func(message string) {
		warnings = append(warnings, message)
	})

	if err != nil {
		t.Fatalf("expected report, got error %v", err)
	}
	expectedQueries := []string{`rate(total{namespace="c8-ck-test"}[30s])`, "image_query[900s:15s]", "missing_query"}
	if !slices.Equal(client.queries, expectedQueries) {
		t.Fatalf("unexpected queries: %#v", client.queries)
	}
	if report.Metrics.get("namespace") != "c8-ck-test" {
		t.Fatalf("unexpected namespace metric: %#v", report.Metrics.get("namespace"))
	}
	if report.Metrics.get("throughput") != 10 {
		t.Fatalf("unexpected throughput metric: %#v", report.Metrics.get("throughput"))
	}
	if report.Metrics.get("image") != "camunda:SNAPSHOT" {
		t.Fatalf("unexpected image metric: %#v", report.Metrics.get("image"))
	}
	if report.Metrics.get("missing") != nil {
		t.Fatalf("expected missing metric to be nil, got %#v", report.Metrics.get("missing"))
	}
	if !slices.Equal(warnings, []string{"missing: no numeric sample"}) {
		t.Fatalf("unexpected warnings: %#v", warnings)
	}
}

func TestBuildReportShouldWarnWhenQueryFails(t *testing.T) {
	opts := options{namespace: "c8-ck-test", durationSeconds: 900, rateInterval: "30s", sampleStep: "15s", endpoint: "http://prometheus.example"}
	queryDocument := queryDocument{Queries: []queryDefinition{{Key: "throughput", Query: "throughput_query"}}}
	client := &fakePrometheusClient{responses: []any{errors.New("query failed")}}
	var warnings []string

	report, err := buildReport(opts, queryDocument, client, "", func(message string) {
		warnings = append(warnings, message)
	})

	if err != nil {
		t.Fatalf("expected report, got error %v", err)
	}
	if report.Metrics.get("throughput") != nil {
		t.Fatalf("expected nil metric, got %#v", report.Metrics.get("throughput"))
	}
	if !slices.Equal(warnings, []string{"throughput: query failed"}) {
		t.Fatalf("unexpected warnings: %#v", warnings)
	}
}

func TestAuthHeadersShouldSetBasicAuth(t *testing.T) {
	headers, err := authHeaders(options{basicAuthUser: "user", basicAuthPass: "pass"})

	if err != nil {
		t.Fatalf("expected headers, got error %v", err)
	}
	if headers.Get("Authorization") != "Basic dXNlcjpwYXNz" {
		t.Fatalf("unexpected authorization header: %s", headers.Get("Authorization"))
	}
}

func TestAuthHeadersShouldSetBearerToken(t *testing.T) {
	headers, err := authHeaders(options{bearerToken: "abc123"})

	if err != nil {
		t.Fatalf("expected headers, got error %v", err)
	}
	if headers.Get("Authorization") != "Bearer abc123" {
		t.Fatalf("unexpected authorization header: %s", headers.Get("Authorization"))
	}
}

func TestAuthHeadersShouldRejectMixedAuth(t *testing.T) {
	_, err := authHeaders(options{bearerToken: "abc123", basicAuthUser: "user", basicAuthPass: "pass"})

	if err == nil || !strings.Contains(err.Error(), "--token cannot be combined") {
		t.Fatalf("expected mixed auth error, got %v", err)
	}
}

func TestAuthHeadersShouldRequireUserAndPasswordTogether(t *testing.T) {
	_, err := authHeaders(options{basicAuthUser: "user"})

	if err == nil || !strings.Contains(err.Error(), "--user and --password") {
		t.Fatalf("expected incomplete basic auth error, got %v", err)
	}
}

func TestPrometheusClientShouldQueryPrometheusWithURLEncodedQueryAndHeaders(t *testing.T) {
	var seenRequests []*http.Request
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		seenRequests = append(seenRequests, request)
		writer.WriteHeader(http.StatusOK)
		_, _ = writer.Write([]byte(`{"status":"success","data":{"result":[]}}`))
	}))
	defer server.Close()

	client, err := newPrometheusClient(options{
		endpoint:      server.URL + "/",
		basicAuthUser: "user",
		basicAuthPass: "pass",
		timeAnchor:    "2026-09-07T18:00:00Z",
	})
	if err != nil {
		t.Fatalf("expected client, got error %v", err)
	}
	response, err := client.query(`rate(total{namespace="c8-ck-test"}[5m])`)

	if err != nil {
		t.Fatalf("expected response, got error %v", err)
	}
	if response.Status != "success" {
		t.Fatalf("unexpected response: %#v", response)
	}
	request := seenRequests[0]
	query, err := url.QueryUnescape(request.URL.RawQuery)
	if err != nil {
		t.Fatalf("could not decode query: %v", err)
	}
	if !strings.Contains(query, `query=rate(total{namespace="c8-ck-test"}[5m])`) {
		t.Fatalf("query was not encoded correctly: %s", request.URL.RawQuery)
	}
	if !strings.Contains(query, "time=2026-09-07T18:00:00Z") {
		t.Fatalf("time anchor missing: %s", request.URL.RawQuery)
	}
	if request.Header.Get("Authorization") != "Basic dXNlcjpwYXNz" {
		t.Fatalf("unexpected authorization header: %s", request.Header.Get("Authorization"))
	}
}

func TestLoadQueryDocumentShouldLoadYAMLQueryFile(t *testing.T) {
	tempDir := t.TempDir()
	queriesFile := filepath.Join(tempDir, "queries.yaml")
	if err := os.WriteFile(queriesFile, []byte("queries:\n- key: namespace\n  value: $NAMESPACE\n"), 0644); err != nil {
		t.Fatal(err)
	}

	document, err := loadQueryDocument(queriesFile, map[string]string{"$NAMESPACE": "c8-ck-test"})

	if err != nil {
		t.Fatalf("expected document, got error %v", err)
	}
	if document.Queries[0].Key != "namespace" {
		t.Fatalf("unexpected key: %#v", document.Queries[0].Key)
	}
	if document.Queries[0].Value != "c8-ck-test" {
		t.Fatalf("unexpected substituted value: %#v", document.Queries[0].Value)
	}
}

func TestLoadQueryDocumentShouldSubstituteTemplateVariablesBeforeUnmarshalling(t *testing.T) {
	tempDir := t.TempDir()
	queriesFile := filepath.Join(tempDir, "queries.json")
	if err := os.WriteFile(queriesFile, []byte(`{"queries":[{"key":"namespace","value":"$NAMESPACE"},{"key":"window","query":"metric{namespace=\"$NAMESPACE\"}[$DURATION_S:$SAMPLE_STEP] offset $RATE_INTERVAL"}]}`), 0644); err != nil {
		t.Fatal(err)
	}

	document, err := loadQueryDocument(queriesFile, querySubstitutions(options{
		namespace:       "c8-ck-test",
		durationSeconds: 900,
		rateInterval:    "30s",
		sampleStep:      "15s",
	}))

	if err != nil {
		t.Fatalf("expected document, got error %v", err)
	}
	if document.Queries[0].Value != "c8-ck-test" {
		t.Fatalf("unexpected namespace value: %#v", document.Queries[0].Value)
	}
	if document.Queries[1].Query != `metric{namespace="c8-ck-test"}[900s:15s] offset 30s` {
		t.Fatalf("unexpected query: %#v", document.Queries[1].Query)
	}
}

func TestLoadQueryDocumentShouldLoadDefaultTemplates(t *testing.T) {
	camundaDocument, err := loadQueryDocument("report-queries.yaml", map[string]string{})
	if err != nil {
		t.Fatalf("expected camunda query document, got error %v", err)
	}
	stableDocument, err := loadQueryDocument("report-queries-stable-87.yaml", map[string]string{})
	if err != nil {
		t.Fatalf("expected stable-87 query document, got error %v", err)
	}

	if len(camundaDocument.Queries) != 45 {
		t.Fatalf("unexpected camunda template size: %d", len(camundaDocument.Queries))
	}
	if len(stableDocument.Queries) != 53 {
		t.Fatalf("unexpected stable-87 template size: %d", len(stableDocument.Queries))
	}
}

func TestLoadQueryDocumentShouldRejectInvalidQueryFileSchema(t *testing.T) {
	tempDir := t.TempDir()
	queriesFile := filepath.Join(tempDir, "queries.yaml")
	if err := os.WriteFile(queriesFile, []byte("queries:\n- key: duplicate\n  value: one\n- key: duplicate\n  query: two\n  value: three\n"), 0644); err != nil {
		t.Fatal(err)
	}

	_, err := loadQueryDocument(queriesFile, map[string]string{})

	if err == nil {
		t.Fatal("expected schema error")
	}
	if !strings.Contains(err.Error(), "duplicate query key: duplicate") {
		t.Fatalf("expected duplicate key error, got %v", err)
	}
	if !strings.Contains(err.Error(), "query entry duplicate must set exactly one of query or value") {
		t.Fatalf("expected query/value exclusivity error, got %v", err)
	}
}

func TestLoadTestReportEntrypointShouldBuildJSONReport(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		switch request.URL.Path {
		case "/api/v1/status/runtimeinfo":
			_, _ = writer.Write([]byte(`{"status":"success","data":{}}`))
		case "/api/v1/query":
			query := request.URL.Query().Get("query")
			switch {
			case strings.Contains(query, "numeric_metric"):
				_, _ = writer.Write([]byte(`{"status":"success","data":{"result":[{"metric":{},"value":[1700000000,"42.5"]}]}}`))
			case strings.Contains(query, "label_metric"):
				_, _ = writer.Write([]byte(`{"status":"success","data":{"result":[{"metric":{"image":"b"}},{"metric":{"image":"a"}},{"metric":{"image":"a"}}]}}`))
			case strings.Contains(query, "no_data_metric"):
				_, _ = writer.Write([]byte(`{"status":"success","data":{"result":[]}}`))
			default:
				http.Error(writer, "unexpected query: "+query, http.StatusBadRequest)
			}
		default:
			http.NotFound(writer, request)
		}
	}))
	defer server.Close()

	tempDir := t.TempDir()
	queriesFile := filepath.Join(tempDir, "queries.yaml")
	if err := os.WriteFile(queriesFile, []byte(`queries:
- key: namespace
  header: Namespace
  value: $NAMESPACE
- key: numeric_metric
  header: Numeric
  query: numeric_metric{namespace="$NAMESPACE"}
- key: label_metric
  header: Label
  query: label_metric{namespace="$NAMESPACE"}
  valueLabel: image
- key: no_data_metric
  header: NoData
  query: no_data_metric{namespace="$NAMESPACE"}
`), 0644); err != nil {
		t.Fatal(err)
	}

	command := exec.Command("./loadTestReport.sh", "c8-test-ns", "--endpoint", server.URL, "--queries-file", queriesFile, "--format", "json")
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	command.Stdout = &stdout
	command.Stderr = &stderr

	if err := command.Run(); err != nil {
		t.Fatalf("expected command to pass, got %v\nstdout:\n%s\nstderr:\n%s", err, stdout.String(), stderr.String())
	}
	if !strings.Contains(stderr.String(), "no_data_metric: no numeric sample") {
		t.Fatalf("expected missing metric warning, got %s", stderr.String())
	}

	var report map[string]any
	if err := json.Unmarshal(stdout.Bytes(), &report); err != nil {
		t.Fatalf("expected JSON output, got %v\n%s", err, stdout.String())
	}
	metrics := report["metrics"].(map[string]any)
	if metrics["namespace"] != "c8-test-ns" {
		t.Fatalf("unexpected namespace metric: %#v", metrics["namespace"])
	}
	if metrics["numeric_metric"] != 42.5 {
		t.Fatalf("unexpected numeric metric: %#v", metrics["numeric_metric"])
	}
	if metrics["label_metric"] != "a, b" {
		t.Fatalf("unexpected label metric: %#v", metrics["label_metric"])
	}
	if metrics["no_data_metric"] != nil {
		t.Fatalf("expected no data metric to be null, got %#v", metrics["no_data_metric"])
	}
}

func TestRenderReportShouldRenderTSVWithoutHeader(t *testing.T) {
	report := report{
		Columns: []string{"namespace", "throughput", "missing"},
		Headers: []string{"Namespace", "Throughput", "Missing"},
		Metrics: orderedMetrics{
			{key: "namespace", value: "c8-ck-test"},
			{key: "throughput", value: 10},
			{key: "missing", value: nil},
		},
	}

	rendered, err := renderReport(report, "tsv", false, "NaN")

	if err != nil {
		t.Fatalf("expected rendered report, got error %v", err)
	}
	if rendered != "c8-ck-test\t10\tNaN" {
		t.Fatalf("unexpected TSV: %s", rendered)
	}
}

func TestRenderReportShouldQuoteCSVCells(t *testing.T) {
	report := report{
		Columns: []string{"namespace", "image"},
		Headers: []string{"Namespace", "Image"},
		Metrics: orderedMetrics{
			{key: "namespace", value: "c8-ck-test"},
			{key: "image", value: "camunda:1, camunda:2"},
		},
	}

	rendered, err := renderReport(report, "csv", true, "NaN")

	if err != nil {
		t.Fatalf("expected rendered report, got error %v", err)
	}
	if rendered != "Namespace,Image\nc8-ck-test,\"camunda:1, camunda:2\"" {
		t.Fatalf("unexpected CSV: %s", rendered)
	}
}

func TestParseArgsShouldDeriveDurationFromStartAndEnd(t *testing.T) {
	tempDir := t.TempDir()
	queriesFile := filepath.Join(tempDir, "queries.json")
	if err := os.WriteFile(queriesFile, []byte(`{"queries":[{"key":"namespace","value":"$NAMESPACE"}]}`), 0644); err != nil {
		t.Fatal(err)
	}

	opts, err := parseArgs([]string{"c8-ck-test", "--start", "2026-08-14T10:00:00Z", "--end", "2026-08-14T10:30:00Z", "--queries-file", queriesFile}, tempDir)

	if err != nil {
		t.Fatalf("expected options, got error %v", err)
	}
	if opts.durationSeconds != 1800 {
		t.Fatalf("unexpected duration: %d", opts.durationSeconds)
	}
	if opts.timeAnchor != "2026-08-14T10:30:00Z" {
		t.Fatalf("unexpected time anchor: %s", opts.timeAnchor)
	}
	if opts.startLabel != "2026-08-14T10:00:00Z" {
		t.Fatalf("unexpected start: %s", opts.startLabel)
	}
	if opts.endLabel != "2026-08-14T10:30:00Z" {
		t.Fatalf("unexpected end: %s", opts.endLabel)
	}
}

func TestParseArgsShouldSupportPositionalNamespaceBeforeFlags(t *testing.T) {
	tempDir := t.TempDir()
	queriesFile := filepath.Join(tempDir, "queries.json")
	if err := os.WriteFile(queriesFile, []byte(`{"queries":[{"key":"namespace","value":"$NAMESPACE"}]}`), 0644); err != nil {
		t.Fatal(err)
	}

	opts, err := parseArgs([]string{"c8-ck-test", "--duration-seconds", "1800", "--format", "tsv", "--no-header", "--queries-file", queriesFile}, tempDir)

	if err != nil {
		t.Fatalf("expected options, got error %v", err)
	}
	if opts.namespace != "c8-ck-test" {
		t.Fatalf("unexpected namespace: %s", opts.namespace)
	}
	if opts.durationSeconds != 1800 {
		t.Fatalf("unexpected duration: %d", opts.durationSeconds)
	}
	if opts.outputFormat != "tsv" {
		t.Fatalf("unexpected format: %s", opts.outputFormat)
	}
	if opts.includeHeader {
		t.Fatal("expected header to be disabled")
	}
}

func TestRenderReportShouldKeepMetricsInColumnOrderForJSON(t *testing.T) {
	report := report{
		Namespace:       "c8-ck-test",
		DurationSeconds: 600,
		Endpoint:        "http://prometheus.example",
		GeneratedAt:     "2026-09-07T18:00:00Z",
		Columns:         []string{"namespace", "image"},
		Headers:         []string{"Namespace", "Image"},
		Metrics: orderedMetrics{
			{key: "namespace", value: "c8-ck-test"},
			{key: "image", value: "camunda:SNAPSHOT"},
		},
	}

	rendered, err := renderReport(report, "json", true, "NaN")

	if err != nil {
		t.Fatalf("expected rendered report, got error %v", err)
	}
	metricsIndex := strings.Index(rendered, `"metrics": {`)
	namespaceIndex := strings.LastIndex(rendered, `"namespace": "c8-ck-test"`)
	imageIndex := strings.Index(rendered, `"image": "camunda:SNAPSHOT"`)
	if metricsIndex == -1 || namespaceIndex < metricsIndex || imageIndex == -1 || namespaceIndex > imageIndex {
		t.Fatalf("metrics are not in column order:\n%s", rendered)
	}

	var parsed map[string]any
	if err := json.Unmarshal([]byte(rendered), &parsed); err != nil {
		t.Fatalf("rendered JSON is invalid: %v", err)
	}
}
