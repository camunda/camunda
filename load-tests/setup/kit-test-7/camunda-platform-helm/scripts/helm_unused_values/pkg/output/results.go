package output

import (
	"encoding/json"
	"fmt"
	"sort"
	"strings"
	"time"

	"camunda.com/helmunusedvalues/pkg/keys"
	"github.com/fatih/color"
)

// ResultSummary represents the analysis results summary
type ResultSummary struct {
	TotalKeys  int `json:"total_keys"`
	UsedKeys   int `json:"used_keys"`
	UnusedKeys int `json:"unused_keys"`
}

// JSONResult represents the JSON output format
type JSONResult struct {
	Timestamp  string        `json:"timestamp"`
	Summary    ResultSummary `json:"summary"`
	UnusedKeys []string      `json:"unused_keys"`
}

// Reporter handles reporting the analysis results
type Reporter struct {
	Display     *Display
	JSONOutput  bool
	ShowAllKeys bool
}

// NewReporter creates a new reporter
func NewReporter(display *Display, jsonOutput bool, showAllKeys bool) *Reporter {
	return &Reporter{
		Display:     display,
		JSONOutput:  jsonOutput,
		ShowAllKeys: showAllKeys,
	}
}

// ReportResults reports the analysis results based on the configured format
func (r *Reporter) ReportResults(usages []keys.KeyUsage) error {
	// Calculate summary
	summary := r.calculateSummary(usages)

	// Get keys by usage type
	unusedKeys := filterByUsageType(usages, "unused")
	directKeys := filterByUsageType(usages, "direct")
	patternKeys := filterByUsageType(usages, "pattern")

	var err error
	if r.JSONOutput {
		if r.ShowAllKeys {
			return r.reportJSONResultsWithAllDetails(summary, directKeys, patternKeys, unusedKeys)
		}
		return r.reportJSONResultsWithOnlyUnusedDetails(summary, unusedKeys)
	}

	if r.ShowAllKeys {
		err = r.reportUsedKeys(directKeys, patternKeys, usages)
		if err != nil {
			return fmt.Errorf("report used keys: %w", err)
		}
	}

	err = r.reportUnusedKeys(summary, unusedKeys, usages)
	if err != nil {
		return fmt.Errorf("report text results: %w", err)
	}

	r.printSummary(summary)
	return nil
}

// calculateSummary calculates the result summary
func (r *Reporter) calculateSummary(usages []keys.KeyUsage) ResultSummary {
	var summary ResultSummary

	summary.TotalKeys = len(usages)

	// Count keys by usage type
	for _, usage := range usages {
		switch usage.UsageType {
		case "direct", "pattern":
			summary.UsedKeys++
		case "unused":
			summary.UnusedKeys++
		}
	}

	return summary
}

func filterByUsageType(usages []keys.KeyUsage, usageType string) []string {
	var keys []string

	for _, usage := range usages {
		if usage.UsageType == usageType {
			keys = append(keys, usage.Key)
		}
	}

	return keys
}

func FilterByUsageType(usages []keys.KeyUsage, usageType string) []string {
	var keys []string

	for _, usage := range usages {
		if usage.UsageType == usageType {
			keys = append(keys, usage.Key)
		}
	}

	return keys
}

func (r *Reporter) reportJSONResultsWithOnlyUnusedDetails(summary ResultSummary, unusedKeys []string) error {
	jsonOutput := JSONResult{
		Timestamp:  time.Now().UTC().Format(time.RFC3339),
		Summary:    summary,
		UnusedKeys: unusedKeys,
	}

	jsonData, err := json.MarshalIndent(jsonOutput, "", "  ")
	if err != nil {
		return fmt.Errorf("error generating JSON: %w", err)
	}

	r.Display.PrintJson(string(jsonData))

	return nil
}

func (r *Reporter) reportJSONResultsWithAllDetails(summary ResultSummary, directKeys, patternKeys, unusedKeys []string) error {
	type AllKeysJSONResult struct {
		Timestamp            string        `json:"timestamp"`
		Summary              ResultSummary `json:"summary"`
		DirectlyUsedKeys     []string      `json:"directly_used_keys"`
		PatternUsedKeys      []string      `json:"pattern_used_keys"`
		UnusedCompletelyKeys []string      `json:"unused_completely_keys"`
	}

	jsonOutput := AllKeysJSONResult{
		Timestamp:            time.Now().UTC().Format(time.RFC3339),
		Summary:              summary,
		DirectlyUsedKeys:     directKeys,
		PatternUsedKeys:      patternKeys,
		UnusedCompletelyKeys: unusedKeys,
	}

	jsonData, err := json.MarshalIndent(jsonOutput, "", "  ")
	if err != nil {
		return fmt.Errorf("error generating JSON: %w", err)
	}

	r.Display.PrintJson(string(jsonData))

	return nil
}

func (r *Reporter) reportUnusedKeys(summary ResultSummary, unusedKeys []string, usages []keys.KeyUsage) error {
	usageMap := make(map[string]keys.KeyUsage)
	for _, usage := range usages {
		usageMap[usage.Key] = usage
	}

	if summary.UnusedKeys == 0 {
		r.Display.PrintSuccess("No unused keys found in values.yaml.")
		return nil
	}

	r.Display.PrintError("Unused keys found in values.yaml:")
	fmt.Println()

	usedKeys := []string{}
	for _, usage := range usages {
		if usage.UsageType == "direct" || usage.UsageType == "pattern" {
			usedKeys = append(usedKeys, usage.Key)
		}
	}
	sort.Strings(usedKeys)

	if summary.UnusedKeys > 0 {
		r.Display.PrintBold(fmt.Sprintf("Completely unused keys (%d):", summary.UnusedKeys))
		for _, key := range unusedKeys {
			r.Display.PrintError(fmt.Sprintf("  .Values.%s", key))
		}
		fmt.Println()
	}

	return nil
}

func (r *Reporter) reportUsedKeys(directKeys, patternKeys []string, usages []keys.KeyUsage) error {
	usageMap := make(map[string]keys.KeyUsage)
	for _, usage := range usages {
		usageMap[usage.Key] = usage
	}

	fmt.Println()
	r.Display.PrintInfo("All keys in values.yaml:")
	fmt.Println()

	if len(directKeys) > 0 {
		r.Display.PrintBold(fmt.Sprintf("Directly used keys (%d):", len(directKeys)))
		for _, key := range directKeys {
			// Format the locations inline if available
			if usage, ok := usageMap[key]; ok && len(usage.Locations) > 0 {
				// Show only the first location for brevity
				location := usage.Locations[0]
				parts := strings.Split(location, ":")
				if len(parts) >= 2 {
					fmt.Printf("  ")
					green := color.New(color.FgGreen)
					green.Printf(".Values.%s ", key)

					cyan := color.New(color.FgCyan)
					cyan.Printf("→ %s", parts[0])

					bold := color.New(color.Bold)
					bold.Printf(":%s", parts[1])

					// Show location count if more than one
					if len(usage.Locations) > 1 {
						fmt.Printf(" (+%d more)", len(usage.Locations)-1)
					}
					fmt.Println()
				} else {
					r.Display.PrintSuccess(fmt.Sprintf("  .Values.%s → %s", key, location))
				}
			} else {
				r.Display.PrintSuccess(fmt.Sprintf("  .Values.%s", key))
			}
		}
		fmt.Println()
	}

	if len(patternKeys) > 0 {
		r.Display.PrintBold(fmt.Sprintf("Keys used via patterns (%d):", len(patternKeys)))
		for _, key := range patternKeys {
			if usage, ok := usageMap[key]; ok {
				fmt.Printf("  ")
				green := color.New(color.FgGreen)
				green.Printf(".Values.%s ", key)

				cyan := color.New(color.FgCyan)
				cyan.Printf("(via %s)", usage.PatternName)

				// Show the first location if available
				if len(usage.Locations) > 0 {
					location := usage.Locations[0]
					parts := strings.Split(location, ":")
					if len(parts) >= 2 {
						fmt.Printf(" → ")
						// Handle placeholder values that might not be real file:line
						if strings.HasPrefix(parts[0], "[PATTERN MATCH]") {
							// This is a placeholder from a regex match
							magenta := color.New(color.FgMagenta)
							magenta.Printf("Pattern match detected")
						} else {
							// Regular file:line match
							cyan.Printf("%s", parts[0])

							bold := color.New(color.Bold)
							bold.Printf(":%s", parts[1])
						}

						// Show location count if more than one
						if len(usage.Locations) > 1 {
							fmt.Printf(" (+%d more)", len(usage.Locations)-1)
						}
					} else {
						fmt.Printf(" → %s", location)
					}
				}
				fmt.Println()
			} else {
				r.Display.PrintSuccess(fmt.Sprintf("  .Values.%s", key))
			}
		}
		fmt.Println()
	}
	return nil
}

func (r *Reporter) printSummary(summary ResultSummary) {
	r.Display.PrintBold("Usage summary:")
	fmt.Printf("  ")
	cyan := color.New(color.FgCyan)
	cyan.Printf("Total keys: %d", summary.TotalKeys)

	fmt.Printf("  |  ")
	green := color.New(color.FgGreen)
	green.Printf("Used: %d", summary.UsedKeys)

	fmt.Printf("  |  ")
	red := color.New(color.FgRed)
	red.Printf("Unused: %d", summary.UnusedKeys)
	fmt.Println()
	fmt.Println()
}
