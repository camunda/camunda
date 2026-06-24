// goldenfiles.go
package golden

import (
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// goldenDir is the directory that holds committed golden YAML files,
// relative to this file's location.
const goldenDir = "golden"

// normalizePatterns are regexp → replacement pairs applied in order.
// Each entry strips a non-deterministic field from helm template output.
var normalizePatterns = []struct {
	re          *regexp.Regexp
	replacement string
}{
	// helm.sh/chart label embeds chart semver — changes on every chart bump.
	{regexp.MustCompile(`(?m)^\s*helm\.sh/chart:.*$`), ""},
	// deadline-date is a date-stamped namespace label computed at scaffold time.
	{regexp.MustCompile(`(?m)^(\s*deadline-date:).*$`), "${1} TEST_DATE"},
	// Image tags rotate per build; keep the image name, normalize the tag to TEST.
	{regexp.MustCompile(`(image:\s*[^:\s]+:)[^\s"]+`), "${1}TEST"},
	// checksum/config is derived from ConfigMap content; its diff is captured
	// by the ConfigMap's own golden file.
	{regexp.MustCompile(`(?m)^\s*checksum/config:.*$`), ""},
}

// normalize strips non-deterministic fields from raw helm template output.
func normalize(input string) string {
	result := input
	for _, p := range normalizePatterns {
		result = p.re.ReplaceAllString(result, p.replacement)
	}
	// Collapse multiple blank lines that normalization may leave behind.
	result = regexp.MustCompile(`\n{3,}`).ReplaceAllString(result, "\n\n")
	return strings.TrimSpace(result) + "\n"
}

// collectManifests walks a helm --output-dir tree and returns each file's
// normalized content keyed by its path relative to the tree root.
func collectManifests(t *testing.T, root string) map[string]string {
	t.Helper()
	manifests := map[string]string{}
	require.NoError(t, filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		rel, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		data, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		manifests[filepath.ToSlash(rel)] = normalize(string(data))
		return nil
	}))
	return manifests
}

// assertGoldenDir compares the rendered manifest tree at srcDir against the
// committed golden directory at golden/<version>/<scenario>/<chartName>/, file
// for file. When update is true the directory is rewritten from the rendered
// tree instead of compared.
func assertGoldenDir(t *testing.T, version, scenario, chartName, srcDir string, update bool) {
	t.Helper()

	dir := filepath.Join(goldenDir, version, scenario, chartName)
	rendered := collectManifests(t, srcDir)

	if update {
		require.NoError(t, os.RemoveAll(dir))
		for rel, content := range rendered {
			dst := filepath.Join(dir, filepath.FromSlash(rel))
			require.NoError(t, os.MkdirAll(filepath.Dir(dst), 0o755))
			require.NoError(t, os.WriteFile(dst, []byte(content), 0o644),
				"failed to write golden file %s", dst)
		}
		t.Logf("updated %d golden manifest(s) in %s", len(rendered), dir)
		return
	}

	committed := map[string]string{}
	err := filepath.WalkDir(dir, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		rel, err := filepath.Rel(dir, path)
		if err != nil {
			return err
		}
		committed[filepath.ToSlash(rel)] = path
		return nil
	})
	require.NoError(t, err,
		"golden dir %s not found — run: make update-golden", dir)

	assert.Equal(t, sortedKeys(committed), sortedKeys(rendered),
		"manifest file set mismatch in %s\nrun 'make update-golden' to regenerate, then review 'git diff %s'",
		dir, dir)

	for rel, content := range rendered {
		path, ok := committed[rel]
		if !ok {
			continue // missing-file case already reported by the set comparison
		}
		got, err := os.ReadFile(path)
		require.NoError(t, err)
		assert.Equal(t, string(got), content,
			"golden file mismatch for %s\nrun 'make update-golden' to regenerate,\nthen review 'git diff %s' before committing",
			path, path)
	}
}

// sortedKeys returns the keys of m in sorted order.
func sortedKeys[V any](m map[string]V) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}
