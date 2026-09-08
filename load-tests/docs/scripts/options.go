package main

import (
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
)

var (
	namespacePattern = regexp.MustCompile(`^[a-z0-9]([-a-z0-9]*[a-z0-9])?$`)
	durationPattern  = regexp.MustCompile(`^[1-9][0-9]*(ms|s|m|h|d|w|y)$`)
	integerPattern   = regexp.MustCompile(`^-?[0-9]+$`)
)

type options struct {
	namespace       string
	durationSeconds int
	rateInterval    string
	sampleStep      string
	reportTemplate  string
	endpoint        string
	bearerToken     string
	basicAuthUser   string
	basicAuthPass   string
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

	namespaceArg := ""
	flagArgs := args
	if len(args) > 0 && !strings.HasPrefix(args[0], "-") {
		namespaceArg = args[0]
		flagArgs = args[1:]
	}

	durationSeconds := strconv.Itoa(opts.durationSeconds)
	showHelp := false
	showShortHelp := false
	flagSet := flag.NewFlagSet("loadTestReport.sh", flag.ContinueOnError)
	flagSet.SetOutput(io.Discard)
	flagSet.StringVar(&opts.namespace, "namespace", "", "")
	flagSet.StringVar(&durationSeconds, "duration-seconds", durationSeconds, "")
	flagSet.StringVar(&opts.rateInterval, "rate-interval", opts.rateInterval, "")
	flagSet.StringVar(&opts.sampleStep, "sample-step", opts.sampleStep, "")
	flagSet.StringVar(&opts.reportTemplate, "template", opts.reportTemplate, "")
	flagSet.StringVar(&opts.timeAnchor, "at", "", "")
	flagSet.StringVar(&opts.startLabel, "start", "", "")
	flagSet.StringVar(&opts.endLabel, "end", "", "")
	flagSet.StringVar(&opts.endpoint, "endpoint", opts.endpoint, "")
	flagSet.StringVar(&opts.bearerToken, "token", "", "")
	flagSet.StringVar(&opts.basicAuthUser, "user", "", "")
	flagSet.StringVar(&opts.basicAuthPass, "password", "", "")
	flagSet.StringVar(&opts.outputFormat, "format", opts.outputFormat, "")
	flagSet.BoolVar(&showHelp, "help", false, "")
	flagSet.BoolVar(&showShortHelp, "h", false, "")
	flagSet.BoolFunc("no-header", "", func(string) error {
		opts.includeHeader = false
		return nil
	})
	flagSet.StringVar(&opts.missingValue, "missing-value", opts.missingValue, "")
	flagSet.StringVar(&opts.queriesFile, "queries-file", "", "")
	flagSet.StringVar(&opts.outputFile, "output", "", "")

	if err := flagSet.Parse(flagArgs); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			opts.showHelp = true
			return opts, nil
		}
		return opts, err
	}
	if showHelp || showShortHelp {
		opts.showHelp = true
		return opts, nil
	}

	positionalArgs := flagSet.Args()
	if opts.namespace == "" {
		opts.namespace = namespaceArg
	}
	if opts.namespace == "" && len(positionalArgs) > 0 {
		opts.namespace = positionalArgs[0]
		positionalArgs = positionalArgs[1:]
	}
	if len(positionalArgs) > 0 {
		return opts, fmt.Errorf("Unknown argument '%s'. Run with --help for usage.", positionalArgs[0])
	}

	return validateOptions(opts, durationSeconds, scriptDir)
}

func validateOptions(opts options, durationSeconds string, scriptDir string) (options, error) {
	parsedDurationSeconds, err := parsePositiveInt(durationSeconds, "duration-seconds")
	if err != nil {
		return opts, err
	}
	opts.durationSeconds = parsedDurationSeconds
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
