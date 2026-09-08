package main

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
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
  --token <token>           Bearer auth value for Prometheus.
  --user <user>             Basic auth user for Prometheus.
  --password <password>     Basic auth password for Prometheus.
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
    --user "$PROM_USER" \
    --password "$PROM_PASS" \
    --format csv > /tmp/load-test-report.csv
`

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

	client, err := newPrometheusClient(opts)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}
	if err := checkEndpoint(client, opts.endpoint); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return 1
	}

	queryDocument, err := loadQueryDocument(opts.queriesFile, querySubstitutions(opts))
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
