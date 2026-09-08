package main

import (
	"bytes"
	"encoding/base64"
	"encoding/csv"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"maps"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"slices"
	"strconv"
	"strings"
	"time"
	"unicode"

	"gopkg.in/yaml.v3"
)

var (
	namespacePattern = regexp.MustCompile(`^[a-z0-9]([-a-z0-9]*[a-z0-9])?$`)
	durationPattern  = regexp.MustCompile(`^[1-9][0-9]*(ms|s|m|h|d|w|y)$`)
	numberPattern    = regexp.MustCompile(`^-?([0-9]+([.][0-9]+)?|[.][0-9]+)([eE][-+]?[0-9]+)?$`)
	integerPattern   = regexp.MustCompile(`^-?[0-9]+$`)
)

const usage = `Usage: loadTestReport.sh <namespace> [options]

Arguments:
  namespace                 Exact load-test namespace, e.g. c8-ck-baseline-20260814.

Options:
  --duration-seconds <sec>  Query window duration. Default: 600.
  --rate-interval <dur>     Short rate interval for dashboard-style rollups. Default: 5m.
  --sample-step <dur>       Subquery sample resolution for window summaries. Default: 1m.
  --template <name>         Report template: camunda or stable-87. Default: camunda.
  --at <time>               Prometheus query time anchor, RFC3339 or Unix timestamp.
                            Queries cover (--duration-seconds) ending at this time.
  --start <time>            Start of the reporting window, RFC3339 or Unix timestamp.
  --end <time>              End of the reporting window, RFC3339 or Unix timestamp.
                            When --start/--end are set, duration is derived from them.
  --endpoint <url>          Prometheus base URL. Default: http://localhost:9090.
  --curl-opts <opts>        Supported curl-style auth/header options, split with shell-like
                            syntax, e.g. '--user u:p' or '--header "X-Test: yes"'.
  --format <format>         Output format: json, csv, or tsv. Default: json.
  --no-header               Omit the CSV/TSV header row.
  --missing-value <value>   CSV/TSV placeholder for missing metrics. Default: NaN.
  --queries-file <path>     Query definition file (YAML or JSON). Overrides --template.
  --output <path>           Write output to a file instead of stdout.
  -h, --help                Show this help message.

Examples:
  # Port-forwarded Prometheus, JSON:
  ./loadTestReport.sh c8-ck-baseline-20260814 --duration-seconds 1800

  # Historical window, spreadsheet-friendly TSV:
  ./loadTestReport.sh c8-ck-baseline-20260814 \
    --start 2026-08-14T10:00:00Z \
    --end 2026-08-14T10:30:00Z \
    --format tsv --no-header

  # CI monitor ingress with basic auth:
  ./loadTestReport.sh c8-ck-baseline-20260814 \
    --duration-seconds 1800 \
    --endpoint https://ci-monitor.benchmark.camunda.cloud \
    --curl-opts "--user $PROM_USER:$PROM_PASS" \
    --format csv > /tmp/load-test-report.csv
`

type options struct {
	namespace       string
	durationSeconds int
	rateInterval    string
	sampleStep      string
	reportTemplate  string
	endpoint        string
	curlOpts        string
	timeAnchor      string
	startLabel      string
	endLabel        string
	outputFormat    string
	includeHeader   bool
	missingValue    string
	queriesFile     string
	outputFile      string
	showHelp        bool
}

type prometheusAPI interface {
	runtimeInfo() (map[string]any, error)
	query(string) (map[string]any, error)
}

type prometheusClient struct {
	endpoint   string
	headers    http.Header
	httpClient *http.Client
	timeAnchor string
}

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

func main() {
	os.Exit(run(os.Args[1:]))
}

func run(args []string) int {
	scriptDir, err := scriptDirectory()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}

	opts, err := parseArgs(args, scriptDir)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}
	if opts.showHelp {
		fmt.Print(usage)
		return 0
	}

	client, err := newPrometheusClient(opts.endpoint, opts.curlOpts, opts.timeAnchor)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}
	if err := checkEndpoint(client, opts.endpoint); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}

	queryDocument, err := loadQueryDocument(opts.queriesFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}

	report, err := buildReport(opts, queryDocument, client, "", warn)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}
	output, err := renderReport(report, opts.outputFormat, opts.includeHeader, opts.missingValue)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}

	if opts.outputFile != "" {
		if err := os.WriteFile(opts.outputFile, []byte(output+"\n"), 0644); err != nil {
			fmt.Fprintf(os.Stderr, "Error: %v\n", err)
			return 1
		}
		return 0
	}

	fmt.Println(output)
	return 0
}

func scriptDirectory() (string, error) {
	if scriptDir := os.Getenv("LOAD_TEST_REPORT_SCRIPT_DIR"); scriptDir != "" {
		return scriptDir, nil
	}
	_, sourceFile, _, ok := runtime.Caller(0)
	if !ok {
		return "", errors.New("could not resolve script directory")
	}
	return filepath.Dir(sourceFile), nil
}

func parseArgs(args []string, scriptDir string) (options, error) {
	opts := options{
		durationSeconds: 600,
		rateInterval:    "5m",
		sampleStep:      "1m",
		reportTemplate:  "camunda",
		endpoint:        "http://localhost:9090",
		outputFormat:    "json",
		includeHeader:   true,
		missingValue:    "NaN",
	}

	for index := 0; index < len(args); index++ {
		arg := args[index]
		switch arg {
		case "-h", "--help":
			opts.showHelp = true
			return opts, nil
		case "--namespace":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.namespace = value
		case "--duration-seconds":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			durationSeconds, err := parsePositiveInt(value, "duration-seconds")
			if err != nil {
				return opts, err
			}
			opts.durationSeconds = durationSeconds
		case "--rate-interval":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.rateInterval = value
		case "--sample-step":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.sampleStep = value
		case "--template":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.reportTemplate = value
		case "--at":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.timeAnchor = value
		case "--start":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.startLabel = value
		case "--end":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.endLabel = value
		case "--endpoint":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.endpoint = value
		case "--curl-opts":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.curlOpts = value
		case "--format":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.outputFormat = value
		case "--no-header":
			opts.includeHeader = false
		case "--missing-value":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.missingValue = value
		case "--queries-file":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.queriesFile = value
		case "--output":
			value, err := nextArg(args, &index, arg)
			if err != nil {
				return opts, err
			}
			opts.outputFile = value
		default:
			if strings.HasPrefix(arg, "-") {
				return opts, fmt.Errorf("Unknown argument '%s'. Run with --help for usage.", arg)
			}
			if opts.namespace != "" {
				return opts, fmt.Errorf("Unknown argument '%s'. Run with --help for usage.", arg)
			}
			opts.namespace = arg
		}
	}

	if opts.showHelp {
		return opts, nil
	}
	if opts.namespace == "" {
		return opts, errors.New("Missing <namespace>.")
	}
	if len(opts.namespace) > 63 || !namespacePattern.MatchString(opts.namespace) {
		return opts, fmt.Errorf("namespace '%s' must be a valid Kubernetes DNS label (max 63 characters; lowercase alphanumeric or '-', and must start and end with an alphanumeric character).", opts.namespace)
	}
	if !durationPattern.MatchString(opts.rateInterval) {
		return opts, fmt.Errorf("rate-interval '%s' must be a Prometheus duration like 30s, 5m, or 1h.", opts.rateInterval)
	}
	if !durationPattern.MatchString(opts.sampleStep) {
		return opts, fmt.Errorf("sample-step '%s' must be a Prometheus duration like 30s, 1m, or 5m.", opts.sampleStep)
	}
	if err := resolveTimeWindow(&opts); err != nil {
		return opts, err
	}
	switch opts.outputFormat {
	case "json", "csv", "tsv":
	default:
		return opts, fmt.Errorf("Unsupported --format '%s'. Expected json, csv, or tsv.", opts.outputFormat)
	}
	if opts.queriesFile == "" {
		queriesFile, err := templateFile(scriptDir, opts.reportTemplate)
		if err != nil {
			return opts, err
		}
		opts.queriesFile = queriesFile
	}
	fileInfo, err := os.Stat(opts.queriesFile)
	if err != nil || fileInfo.IsDir() {
		return opts, fmt.Errorf("queries file not found at %s.", opts.queriesFile)
	}

	return opts, nil
}

func nextArg(args []string, index *int, flag string) (string, error) {
	*index = *index + 1
	if *index >= len(args) {
		return "", fmt.Errorf("Missing value for %s.", flag)
	}
	return args[*index], nil
}

func parsePositiveInt(value string, name string) (int, error) {
	if !integerPattern.MatchString(value) || strings.HasPrefix(value, "0") {
		return 0, fmt.Errorf("%s '%s' must be a positive integer.", name, value)
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		return 0, fmt.Errorf("%s '%s' must be a positive integer.", name, value)
	}
	return parsed, nil
}

func resolveTimeWindow(opts *options) error {
	if opts.startLabel != "" || opts.endLabel != "" {
		if opts.startLabel == "" || opts.endLabel == "" {
			return errors.New("--start and --end must be provided together.")
		}
		if opts.timeAnchor != "" {
			return errors.New("--at cannot be combined with --start/--end.")
		}

		startEpoch, err := parseEpoch(opts.startLabel, "--start")
		if err != nil {
			return err
		}
		endEpoch, err := parseEpoch(opts.endLabel, "--end")
		if err != nil {
			return err
		}
		if endEpoch <= startEpoch {
			return errors.New("--end must be after --start.")
		}

		opts.durationSeconds = int(endEpoch - startEpoch)
		opts.timeAnchor = opts.endLabel
		opts.startLabel = formatEpoch(startEpoch)
		opts.endLabel = formatEpoch(endEpoch)
		return nil
	}

	if opts.timeAnchor == "" {
		return nil
	}

	anchorEpoch, err := parseEpoch(opts.timeAnchor, "--at")
	if err != nil {
		return err
	}
	opts.startLabel = formatEpoch(anchorEpoch - int64(opts.durationSeconds))
	opts.endLabel = formatEpoch(anchorEpoch)
	return nil
}

func parseEpoch(value string, flag string) (int64, error) {
	if integerPattern.MatchString(value) && !strings.HasPrefix(value, "-") {
		parsed, err := strconv.ParseInt(value, 10, 64)
		if err != nil {
			return 0, fmt.Errorf("Could not parse %s '%s'.", flag, value)
		}
		return parsed, nil
	}

	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil {
		return 0, fmt.Errorf("Could not parse %s '%s'.", flag, value)
	}
	return parsed.Unix(), nil
}

func formatEpoch(epoch int64) string {
	return time.Unix(epoch, 0).UTC().Format("2006-01-02T15:04:05Z")
}

func templateFile(scriptDir string, reportTemplate string) (string, error) {
	switch reportTemplate {
	case "camunda":
		return filepath.Join(scriptDir, "report-queries.yaml"), nil
	case "stable-87":
		return filepath.Join(scriptDir, "report-queries-stable-87.yaml"), nil
	default:
		return "", fmt.Errorf("Unsupported --template '%s'. Expected camunda or stable-87.", reportTemplate)
	}
}

func newPrometheusClient(endpoint string, curlOpts string, timeAnchor string) (prometheusClient, error) {
	headers, err := parseHTTPHeaders(curlOpts)
	if err != nil {
		return prometheusClient{}, err
	}
	return prometheusClient{
		endpoint:   strings.TrimRight(endpoint, "/"),
		headers:    headers,
		httpClient: &http.Client{},
		timeAnchor: timeAnchor,
	}, nil
}

func (c prometheusClient) runtimeInfo() (map[string]any, error) {
	return c.getJSON(c.endpoint+"/api/v1/status/runtimeinfo", 15*time.Second)
}

func (c prometheusClient) query(query string) (map[string]any, error) {
	values := url.Values{"query": []string{query}}
	if c.timeAnchor != "" {
		values.Set("time", c.timeAnchor)
	}
	return c.getJSON(c.endpoint+"/api/v1/query?"+values.Encode(), 30*time.Second)
}

func (c prometheusClient) getJSON(rawURL string, timeout time.Duration) (map[string]any, error) {
	request, err := http.NewRequest(http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	request.Header = c.headers.Clone()

	httpClient := *c.httpClient
	httpClient.Timeout = timeout
	response, err := httpClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()

	body, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, fmt.Errorf("Prometheus returned HTTP %d.", response.StatusCode)
	}

	var parsed map[string]any
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, fmt.Errorf("Prometheus returned invalid JSON: %w", err)
	}
	return parsed, nil
}

func parseHTTPHeaders(curlOpts string) (http.Header, error) {
	tokens, err := splitShellFields(curlOpts)
	if err != nil {
		return nil, fmt.Errorf("Could not parse --curl-opts: %w", err)
	}

	headers := http.Header{}
	for index := 0; index < len(tokens); index++ {
		token := tokens[index]
		switch {
		case token == "--user" || token == "-u":
			index++
			if index >= len(tokens) {
				return nil, fmt.Errorf("Missing value for %s in --curl-opts.", token)
			}
			headers.Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte(tokens[index])))
		case strings.HasPrefix(token, "--user="):
			headers.Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte(strings.TrimPrefix(token, "--user="))))
		case token == "--header" || token == "-H":
			index++
			if index >= len(tokens) {
				return nil, fmt.Errorf("Missing value for %s in --curl-opts.", token)
			}
			if err := addHeader(headers, tokens[index]); err != nil {
				return nil, err
			}
		case strings.HasPrefix(token, "--header="):
			if err := addHeader(headers, strings.TrimPrefix(token, "--header=")); err != nil {
				return nil, err
			}
		default:
			return nil, fmt.Errorf("Unsupported --curl-opts argument '%s'. Supported options: --user/-u and --header/-H.", token)
		}
	}
	return headers, nil
}

func addHeader(headers http.Header, header string) error {
	name, value, ok := strings.Cut(header, ":")
	if !ok || strings.TrimSpace(name) == "" {
		return fmt.Errorf("Invalid header in --curl-opts: '%s'. Expected 'Name: value'.", header)
	}
	headers.Set(strings.TrimSpace(name), strings.TrimSpace(value))
	return nil
}

func splitShellFields(input string) ([]string, error) {
	var fields []string
	var current strings.Builder
	var quote rune
	inToken := false
	escaped := false

	for _, char := range input {
		if escaped {
			current.WriteRune(char)
			inToken = true
			escaped = false
			continue
		}
		if char == '\\' {
			escaped = true
			inToken = true
			continue
		}
		if quote != 0 {
			if char == quote {
				quote = 0
			} else {
				current.WriteRune(char)
			}
			continue
		}
		if char == '\'' || char == '"' {
			quote = char
			inToken = true
			continue
		}
		if unicode.IsSpace(char) {
			if inToken {
				fields = append(fields, current.String())
				current.Reset()
				inToken = false
			}
			continue
		}
		current.WriteRune(char)
		inToken = true
	}

	if escaped {
		return nil, errors.New("unterminated escape")
	}
	if quote != 0 {
		return nil, errors.New("unterminated quote")
	}
	if inToken {
		fields = append(fields, current.String())
	}
	return fields, nil
}

func loadQueryDocument(queriesFile string) (map[string]any, error) {
	data, err := os.ReadFile(queriesFile)
	if err != nil {
		return nil, fmt.Errorf("failed to read queries file %s: %w", queriesFile, err)
	}

	var document map[string]any
	if strings.EqualFold(filepath.Ext(queriesFile), ".json") {
		err = json.Unmarshal(data, &document)
	} else {
		err = yaml.Unmarshal(data, &document)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to read queries file %s: %w", queriesFile, err)
	}
	if err := validateQueryDocument(document); err != nil {
		return nil, err
	}
	return document, nil
}

func validateQueryDocument(document map[string]any) error {
	rawQueries, ok := document["queries"].([]any)
	if !ok || len(rawQueries) == 0 {
		return fmt.Errorf("invalid queries file:\nqueries must be a non-empty array")
	}

	seenKeys := map[string]bool{}
	var validationErrors []string
	for _, rawQuery := range rawQueries {
		query, ok := rawQuery.(map[string]any)
		if !ok {
			validationErrors = append(validationErrors, "each query entry must be an object")
			continue
		}

		key, hasKey := nonEmptyString(query, "key")
		if !hasKey {
			validationErrors = append(validationErrors, "each query entry must have a non-empty string key")
		} else if seenKeys[key] {
			validationErrors = append(validationErrors, "duplicate query key: "+key)
		} else {
			seenKeys[key] = true
		}

		_, hasQuery := nonEmptyString(query, "query")
		_, hasValue := nonEmptyString(query, "value")
		if hasQuery == hasValue {
			if hasKey {
				validationErrors = append(validationErrors, fmt.Sprintf("query entry %s must set exactly one of query or value", key))
			} else {
				validationErrors = append(validationErrors, "query entry <missing key> must set exactly one of query or value")
			}
		}

		if _, hasValueLabel := nonEmptyString(query, "valueLabel"); hasValueLabel && !hasQuery {
			validationErrors = append(validationErrors, fmt.Sprintf("query entry %s sets valueLabel without query", key))
		}
	}
	if len(validationErrors) > 0 {
		return fmt.Errorf("invalid queries file:\n%s", strings.Join(validationErrors, "\n"))
	}
	return nil
}

func nonEmptyString(values map[string]any, key string) (string, bool) {
	value, ok := values[key].(string)
	if !ok || value == "" {
		return "", false
	}
	return value, true
}

func substituteTemplates(value any, substitutions map[string]string) any {
	switch typedValue := value.(type) {
	case string:
		rendered := typedValue
		for placeholder, replacement := range substitutions {
			rendered = strings.ReplaceAll(rendered, placeholder, replacement)
		}
		return rendered
	case []any:
		rendered := make([]any, 0, len(typedValue))
		for _, item := range typedValue {
			rendered = append(rendered, substituteTemplates(item, substitutions))
		}
		return rendered
	case map[string]any:
		rendered := make(map[string]any, len(typedValue))
		for key, item := range typedValue {
			rendered[key] = substituteTemplates(item, substitutions)
		}
		return rendered
	default:
		return typedValue
	}
}

func checkEndpoint(client prometheusAPI, endpoint string) error {
	response, err := client.runtimeInfo()
	if err != nil {
		return errors.New(prometheusEndpointHelp(endpoint))
	}
	if response["status"] != "success" {
		return fmt.Errorf("Endpoint '%s' is reachable, but it did not return a Prometheus API success response.\n\nIf you are running locally, make sure the port-forward points at Prometheus:\n\n  kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090", endpoint)
	}
	return nil
}

func prometheusEndpointHelp(endpoint string) string {
	return fmt.Sprintf(`Could not reach Prometheus endpoint '%s'.

If you are running locally, start the port-forward in another terminal:

  kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090

Then rerun this script with:

  --endpoint http://localhost:9090

If you are using the CI monitor ingress, verify the URL and pass credentials with
--curl-opts, for example:

  --endpoint https://ci-monitor.benchmark.camunda.cloud --curl-opts "--user $PROM_USER:$PROM_PASS"`, endpoint)
}

func warn(message string) {
	fmt.Fprintln(os.Stderr, message)
}

func missingMetric(key string, reason string, warningSink func(string)) error {
	warningSink(fmt.Sprintf("%s: %s", key, reason))
	return missingMetricError{reason: reason}
}

func extractMetricValue(response map[string]any, valueLabel string, key string, warningSink func(string)) (any, error) {
	if response["status"] != "success" {
		return nil, missingMetric(key, "Prometheus returned non-success status", warningSink)
	}

	result := queryResult(response)
	if valueLabel != "" {
		valuesByLabel := map[string]bool{}
		for _, rawSeries := range result {
			series, ok := rawSeries.(map[string]any)
			if !ok {
				continue
			}
			metric, ok := series["metric"].(map[string]any)
			if !ok || metric[valueLabel] == nil {
				continue
			}
			valuesByLabel[fmt.Sprint(metric[valueLabel])] = true
		}
		if len(valuesByLabel) == 0 {
			return nil, missingMetric(key, "no label sample", warningSink)
		}
		values := slices.Sorted(maps.Keys(valuesByLabel))
		return strings.Join(values, ", "), nil
	}

	rawValue := ""
	if len(result) > 0 {
		if series, ok := result[0].(map[string]any); ok {
			if sample, ok := series["value"].([]any); ok && len(sample) > 1 {
				rawValue = fmt.Sprint(sample[1])
			}
		}
	}
	if !numberPattern.MatchString(rawValue) {
		return nil, missingMetric(key, "no numeric sample", warningSink)
	}
	return parseNumber(rawValue)
}

func queryResult(response map[string]any) []any {
	data, ok := response["data"].(map[string]any)
	if !ok {
		return nil
	}
	result, ok := data["result"].([]any)
	if !ok {
		return nil
	}
	return result
}

func parseNumber(rawValue string) (any, error) {
	if integerPattern.MatchString(rawValue) {
		return strconv.Atoi(rawValue)
	}
	return strconv.ParseFloat(rawValue, 64)
}

func buildReport(opts options, queryDocument map[string]any, client prometheusAPI, generatedAt string, warningSink func(string)) (report, error) {
	substitutions := map[string]string{
		"$NAMESPACE":     opts.namespace,
		"$DURATION_S":    fmt.Sprintf("%ds", opts.durationSeconds),
		"$RATE_INTERVAL": opts.rateInterval,
		"$SAMPLE_STEP":   opts.sampleStep,
	}
	renderedDocument := substituteTemplates(queryDocument, substitutions).(map[string]any)
	queries := renderedDocument["queries"].([]any)

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

	for _, rawQuery := range queries {
		query := rawQuery.(map[string]any)
		key := query["key"].(string)
		report.Columns = append(report.Columns, key)
		report.Headers = append(report.Headers, stringValueOrDefault(query, "header", key))

		if value, hasValue := nonEmptyString(query, "value"); hasValue {
			report.Metrics = append(report.Metrics, metricEntry{key: key, value: value})
			continue
		}

		metricValue, err := queryMetricValue(client, query, key, warningSink)
		if err != nil {
			var missingMetricErr missingMetricError
			if errors.As(err, &missingMetricErr) {
				report.Metrics = append(report.Metrics, metricEntry{key: key, value: nil})
				continue
			}
			warningSink(fmt.Sprintf("%s: query failed", key))
			report.Metrics = append(report.Metrics, metricEntry{key: key, value: nil})
			continue
		}
		report.Metrics = append(report.Metrics, metricEntry{key: key, value: metricValue})
	}
	return report, nil
}

func queryMetricValue(client prometheusAPI, query map[string]any, key string, warningSink func(string)) (any, error) {
	response, err := client.query(query["query"].(string))
	if err != nil {
		return nil, err
	}
	return extractMetricValue(response, stringValueOrDefault(query, "valueLabel", ""), key, warningSink)
}

func nullableString(value string) *string {
	if value == "" {
		return nil
	}
	return &value
}

func stringValueOrDefault(values map[string]any, key string, fallback string) string {
	if value, ok := values[key].(string); ok {
		return value
	}
	return fallback
}

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
