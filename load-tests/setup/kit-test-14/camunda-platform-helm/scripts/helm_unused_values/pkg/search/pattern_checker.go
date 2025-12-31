package search

import (
	"fmt"
	"strings"
)

// SearchForDirectUsageOfKeyAcrossAllTemplates searches for a key in the template files
func (f *Finder) SearchForDirectUsageOfKeyAcrossAllTemplates(key string) (bool, []string) {
	// Escape dots in the key for regex pattern
	escapedKey := strings.ReplaceAll(key, ".", "\\.")
	pattern := fmt.Sprintf("\\.Values\\.%s", escapedKey)

	f.Display.DebugLog(fmt.Sprintln("SearchKeyInTemplates debug: Key:", key))
	f.Display.DebugLog(fmt.Sprintln("SearchKeyInTemplates debug: Search pattern:", pattern))
	f.Display.DebugLog(fmt.Sprintln("SearchKeyInTemplates debug: Templates directory:", f.TemplatesDir))
	f.Display.DebugLog(fmt.Sprintln("SearchKeyInTemplates debug: Escaped key:", escapedKey))

	// Search for the pattern in template files
	matches := f.searchFiles(pattern, f.TemplatesDir)

	f.Display.DebugLog(fmt.Sprintf("SearchKeyInTemplates debug: Key '%s' found: %v, matches: %d\n",
		key, len(matches) > 0, len(matches)))

	return len(matches) > 0, matches
}

// adjustRegexPatterns customizes the regex pattern based on pattern name and key
func (f *Finder) adjustRegexPatterns(patternName string, key string) string {
	var regexPattern string
	switch {
	case strings.Contains(patternName, "with_context"):
		regexPattern = f.Registry.Regexes[patternName] + strings.ReplaceAll(key, ".", "\\.") + `\s+}`
	case strings.Contains(patternName, "toyaml"):
		regexPattern = f.Registry.Regexes[patternName] + strings.ReplaceAll(key, ".", "\\.") + `\s+\|\s+nindent`
	case strings.Contains(patternName, "imageByParams"):
		regexPattern = f.Registry.Regexes[patternName] + strings.ReplaceAll(key, ".", "\\.") + `\s*`
	case strings.Contains(patternName, "subChartImagePullSecrets"):
		regexPattern = f.Registry.Regexes[patternName] + strings.ReplaceAll(key, ".", "\\.") + "\\)\\)\\s+}"
	case strings.Contains(patternName, "security_context"):
		regexPattern = f.Registry.Regexes[patternName] + strings.ReplaceAll(key, ".", `\.`) + `.*"context"`
	case strings.Contains(patternName, "include_context"):
		regexPattern = f.Registry.Regexes[patternName] + strings.ReplaceAll(key, ".", `\.`)
	default:
		panic("Unknown pattern name: " + patternName)
	}
	return `"` + regexPattern + `"` // escaping so terminal support is better
}

// adjustKeysForHelpers transforms keys based on known helper patterns
func (f *Finder) adjustKeysForHelpers(key string) string {
	var localKey string
	if strings.Contains(key, "identityKeycloak.postgresql") ||
		strings.Contains(key, "identityKeycloak.resources") ||
		strings.Contains(key, "identityKeycloak.containerSecurityContext") ||
		strings.Contains(key, "identityKeycloak.podSecurityContext") ||
		strings.Contains(key, "identityKeycloak.ingress") {
		localKey = strings.ReplaceAll(key, "identityKeycloak.", "identity.")
	} else if strings.Contains(key, "zeebe-gateway") {
		localKey = strings.ReplaceAll(key, "zeebe-gateway.", "zeebeGateway.")
	} else if strings.Contains(key, "serviceAccount.name") {
		localKey = strings.ReplaceAll(key, "serviceAccount.name", "serviceAccountName")
	} else {
		localKey = key
	}
	return localKey
}

// IsKeyUsedWithPattern checks if a key is used with a specific pattern
func (f *Finder) IsKeyUsedWithPattern(key, patternName string) (bool, string, []string) {
	f.Display.DebugLog(fmt.Sprintln("IsKeyUsedWithPattern debug: Key:", key))
	if patternName == "imageByParams" {
		if !strings.Contains(key, "image") {
			return false, "", nil
		}
	}
	if patternName == "include_context" {
		if !strings.Contains(key, "name") {
			return false, "", nil
		}
	}
	if patternName == "security_context" {
		if !strings.Contains(key, "SecurityContext") {
			return false, "", nil
		}
	}
	localKey := f.adjustKeysForHelpers(key)
	if patternName == "imageByParams" {
		if !strings.Contains(localKey, "image") {
			return false, "", nil
		}
	}
	parts := strings.Split(localKey, ".")
	regexPattern := f.adjustRegexPatterns(patternName, localKey)
	matches := f.searchFiles(regexPattern, f.TemplatesDir)

	for len(parts) > 1 && len(matches) == 0 {
		parts = parts[:len(parts)-1]
		regexPattern := f.adjustRegexPatterns(patternName, strings.Join(parts, "."))
		matches = f.searchFiles(regexPattern, f.TemplatesDir)

		f.Display.DebugLog(fmt.Sprintln("IsKeyUsedWithPattern debug: Key:", key))
		f.Display.DebugLog(fmt.Sprintln("IsKeyUsedWithPattern debug: Local Key:", localKey))
		f.Display.DebugLog(fmt.Sprintln("IsKeyUsedWithPattern debug: PatternName:", patternName))
		f.Display.DebugLog(fmt.Sprintln("IsKeyUsedWithPattern debug: Parts:", parts))
		f.Display.DebugLog(fmt.Sprintln("IsKeyUsedWithPattern debug: Trying pattern:", regexPattern))
		f.Display.DebugLog(fmt.Sprintln("IsKeyUsedWithPattern debug: Matches:", matches))
		if len(matches) > 0 { // Fixed: Consistent with loop condition
			break
		}
	}

	return len(matches) != 0, patternName, matches
}
