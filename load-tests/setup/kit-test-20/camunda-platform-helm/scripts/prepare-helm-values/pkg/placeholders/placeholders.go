package placeholders

import (
	"os"
	"sort"
)

// Find returns a sorted, unique list of placeholder variable names present in s.
//
// It detects placeholders using Go's standard library os.Expand parsing rules:
//   - $VAR      where VAR matches [A-Za-z_][A-Za-z0-9_]*
//   - ${VAR}    same variable name rules, with braces
//
// Notes:
//   - Only variable names recognized by os.Expand are reported.
//   - The function does not modify the input string.
func Find(s string) []string {
	seen := make(map[string]struct{})
	// os.Expand calls mapping for each variable occurrence of $var or ${var}
	os.Expand(s, func(name string) string {
		if name == "" {
			return ""
		}
		if _, ok := seen[name]; !ok {
			seen[name] = struct{}{}
		}
		// Return empty string since we don't care about the expanded value
		// and we are not using the expanded output anyway.
		return ""
	})

	out := make([]string, 0, len(seen))
	for k := range seen {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}
