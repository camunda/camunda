package deployer

import (
	"fmt"
	"os"
	"path/filepath"
	"scripts/camunda-core/pkg/logging"
	"sort"
	"strings"
)

// CommonValuesFiles defines the ordered list of common values files to include.
// These are applied as base layers before scenario-specific values.
var CommonValuesFiles = []string{
	"values-integration-test.yaml",
	"values-integration-test-pull-secrets.yaml",
}

// BuildValuesList composes the values file list in precedence order:
// 1) Common values (pre-processed files passed via commonFiles, or discovered from ../common/)
// 2) Auth scenario values (if auth is provided, layered before main scenario)
// 3) Scenario values (must exist)
// 4) Optional overlays (enterprise/digest) when present
// 5) User-provided values (last overrides earlier)
//
// If commonFiles is provided (non-nil), those files are used directly as the common base layer.
// If commonFiles is nil, the function will attempt to discover common files from ../common/ relative to scenarioDir.
func BuildValuesList(scenarioDir string, scenarios []string, auth string, includeEnterprise, includeDigest bool, userValues []string, commonFiles []string) ([]string, error) {
	var files []string

	logging.Logger.Debug().
		Str("scenarioDir", scenarioDir).
		Strs("scenarios", scenarios).
		Str("auth", auth).
		Bool("includeEnterprise", includeEnterprise).
		Bool("includeDigest", includeDigest).
		Strs("userValues", userValues).
		Strs("commonFiles", commonFiles).
		Msg("📋 [BuildValuesList] ENTRY")

	// Add common values first (lowest precedence base layer)
	// If commonFiles is provided, use them directly; otherwise discover from ../common/
	var resolvedCommonFiles []string
	if commonFiles != nil {
		resolvedCommonFiles = commonFiles
		logging.Logger.Debug().
			Strs("commonFiles", resolvedCommonFiles).
			Msg("📋 [BuildValuesList] using pre-processed common files")
	} else {
		resolvedCommonFiles = resolveCommonValues(scenarioDir)
		if len(resolvedCommonFiles) > 0 {
			logging.Logger.Debug().
				Strs("commonFiles", resolvedCommonFiles).
				Msg("📋 [BuildValuesList] discovered common values from sibling directory")
		} else {
			logging.Logger.Debug().
				Str("scenarioDir", scenarioDir).
				Msg("📋 [BuildValuesList] no common values directory found")
		}
	}
	files = append(files, resolvedCommonFiles...)

	// Add auth scenario if provided
	if strings.TrimSpace(auth) != "" {
		logging.Logger.Debug().
			Str("auth", auth).
			Msg("📋 [BuildValuesList] resolving auth scenario")
		authFiles, err := ResolveScenarioFiles(scenarioDir, []string{auth})
		if err != nil {
			logging.Logger.Debug().
				Err(err).
				Str("auth", auth).
				Msg("❌ [BuildValuesList] failed to resolve auth scenario")
			return nil, fmt.Errorf("failed to resolve auth scenario %q: %w", auth, err)
		}
		logging.Logger.Debug().
			Strs("authFiles", authFiles).
			Msg("📋 [BuildValuesList] adding auth scenario values")
		files = append(files, authFiles...)
	}

	// Add main scenario values
	logging.Logger.Debug().
		Strs("scenarios", scenarios).
		Msg("📋 [BuildValuesList] resolving main scenario(s)")
	scenarioFiles, err := ResolveScenarioFiles(scenarioDir, scenarios)
	if err != nil {
		logging.Logger.Debug().
			Err(err).
			Strs("scenarios", scenarios).
			Msg("❌ [BuildValuesList] failed to resolve scenario files")
		return nil, err
	}
	logging.Logger.Debug().
		Strs("scenarioFiles", scenarioFiles).
		Msg("📋 [BuildValuesList] adding main scenario values")
	files = append(files, scenarioFiles...)

	// overlays
	if includeEnterprise {
		if f := overlayIfExists(scenarioDir, "values-enterprise.yaml"); f != "" {
			logging.Logger.Debug().
				Str("file", f).
				Msg("📋 [BuildValuesList] adding enterprise overlay")
			files = append(files, f)
		}
	}
	if includeDigest {
		if f := overlayIfExists(scenarioDir, "values-digest.yaml"); f != "" {
			logging.Logger.Debug().
				Str("file", f).
				Msg("📋 [BuildValuesList] adding digest overlay")
			files = append(files, f)
		}
	}

	// user last (highest precedence)
	if len(userValues) > 0 {
		logging.Logger.Debug().
			Strs("userValues", userValues).
			Msg("📋 [BuildValuesList] adding user values (highest precedence)")
	}
	files = append(files, userValues...)

	logging.Logger.Debug().
		Strs("finalValuesList", files).
		Int("totalFiles", len(files)).
		Msg("✅ [BuildValuesList] EXIT - values list built")

	return files, nil
}

// resolveCommonValues finds and returns common values files from the common/ sibling directory.
// It looks for ../common/ relative to scenarioDir and returns any matching common values files.
func resolveCommonValues(scenarioDir string) []string {
	// Common directory is a sibling to the scenario directory
	// e.g., if scenarioDir is "scenarios/chart-full-setup", common is "scenarios/common"
	commonDir := filepath.Join(filepath.Dir(scenarioDir), "common")

	logging.Logger.Debug().
		Str("scenarioDir", scenarioDir).
		Str("commonDir", commonDir).
		Msg("🔍 [resolveCommonValues] looking for common values directory")

	info, err := os.Stat(commonDir)
	if err != nil || !info.IsDir() {
		logging.Logger.Debug().
			Str("commonDir", commonDir).
			Msg("🔍 [resolveCommonValues] common directory not found or not a directory")
		return nil
	}

	var files []string

	// First, add predefined common files in order (if they exist)
	for _, fileName := range CommonValuesFiles {
		p := filepath.Join(commonDir, fileName)
		if _, err := os.Stat(p); err == nil {
			logging.Logger.Debug().
				Str("file", p).
				Msg("🔍 [resolveCommonValues] found predefined common values file")
			files = append(files, p)
		}
	}

	// Then, discover any additional values-*.yaml files not in the predefined list
	entries, err := os.ReadDir(commonDir)
	if err != nil {
		logging.Logger.Debug().
			Err(err).
			Str("commonDir", commonDir).
			Msg("⚠️ [resolveCommonValues] failed to read common directory")
		return files
	}

	predefinedSet := make(map[string]bool)
	for _, f := range CommonValuesFiles {
		predefinedSet[f] = true
	}

	var additionalFiles []string
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		// Skip if already in predefined list
		if predefinedSet[name] {
			continue
		}
		// Include any other values-*.yaml files
		if strings.HasPrefix(name, "values-") && strings.HasSuffix(name, ".yaml") {
			p := filepath.Join(commonDir, name)
			logging.Logger.Debug().
				Str("file", p).
				Msg("🔍 [resolveCommonValues] found additional common values file")
			additionalFiles = append(additionalFiles, p)
		}
	}

	// Sort additional files for deterministic ordering
	sort.Strings(additionalFiles)
	files = append(files, additionalFiles...)

	logging.Logger.Debug().
		Strs("commonFiles", files).
		Int("count", len(files)).
		Msg("✅ [resolveCommonValues] resolved common values files")

	return files
}

func overlayIfExists(scenarioDir, fileName string) string {
	p := filepath.Join(scenarioDir, fileName)
	if _, err := os.Stat(p); err == nil {
		return p
	}
	return ""
}