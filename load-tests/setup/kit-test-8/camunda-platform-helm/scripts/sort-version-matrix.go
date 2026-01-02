package main

import (
	"encoding/json"
	"fmt"
	"os"
	"sort"
	"strconv"
	"strings"
)

// CamundaVersion represents a Camunda version with its charts
type CamundaVersion struct {
	App    string   `json:"app"`
	Charts []string `json:"charts"`
}

// parseVersion splits a version string into comparable parts
// e.g., "8.3.10" -> [8, 3, 10], "13.0.0-alpha4.2" -> [13, 0, 0, -1, 4, 2]
func parseVersion(version string) []int {
	parts := []int{}
	
	// Split on dots and dashes
	version = strings.ReplaceAll(version, "-", ".")
	segments := strings.Split(version, ".")
	
	for _, segment := range segments {
		// Handle alpha/beta/rc releases
		if strings.HasPrefix(segment, "alpha") {
			parts = append(parts, -3) // alpha comes before beta
			// Extract number after "alpha"
			if num := strings.TrimPrefix(segment, "alpha"); num != "" {
				if n, err := strconv.Atoi(num); err == nil {
					parts = append(parts, n)
				}
			}
		} else if strings.HasPrefix(segment, "beta") {
			parts = append(parts, -2)
			if num := strings.TrimPrefix(segment, "beta"); num != "" {
				if n, err := strconv.Atoi(num); err == nil {
					parts = append(parts, n)
				}
			}
		} else if strings.HasPrefix(segment, "rc") {
			parts = append(parts, -1)
			if num := strings.TrimPrefix(segment, "rc"); num != "" {
				if n, err := strconv.Atoi(num); err == nil {
					parts = append(parts, n)
				}
			}
		} else {
			// Regular numeric segment
			if n, err := strconv.Atoi(segment); err == nil {
				parts = append(parts, n)
			}
		}
	}
	
	return parts
}

// compareVersions returns -1 if v1 < v2, 0 if v1 == v2, 1 if v1 > v2
func compareVersions(v1, v2 string) int {
	p1 := parseVersion(v1)
	p2 := parseVersion(v2)
	
	maxLen := len(p1)
	if len(p2) > maxLen {
		maxLen = len(p2)
	}
	
	for i := 0; i < maxLen; i++ {
		var n1, n2 int
		if i < len(p1) {
			n1 = p1[i]
		} else {
			n1 = 0
		}
		if i < len(p2) {
			n2 = p2[i]
		} else {
			n2 = 0
		}
		
		if n1 < n2 {
			return -1
		}
		if n1 > n2 {
			return 1
		}
	}
	
	return 0
}

func main() {
	// Read JSON from stdin
	var versions []CamundaVersion
	decoder := json.NewDecoder(os.Stdin)
	if err := decoder.Decode(&versions); err != nil {
		fmt.Fprintf(os.Stderr, "Error decoding JSON: %v\n", err)
		os.Exit(1)
	}
	
	// Sort by app version (descending - newest first)
	sort.Slice(versions, func(i, j int) bool {
		return compareVersions(versions[i].App, versions[j].App) > 0
	})
	
	// Sort charts within each version (descending - newest first)
	for i := range versions {
		charts := versions[i].Charts
		sort.Slice(charts, func(a, b int) bool {
			return compareVersions(charts[a], charts[b]) > 0
		})
		versions[i].Charts = charts
	}
	
	// Output sorted JSON
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(versions); err != nil {
		fmt.Fprintf(os.Stderr, "Error encoding JSON: %v\n", err)
		os.Exit(1)
	}
}
