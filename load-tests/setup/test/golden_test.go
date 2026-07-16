// golden_test.go
package golden

import (
	"flag"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"github.com/stretchr/testify/require"
)

var update = flag.Bool("update-golden", false,
	"regenerate golden files instead of comparing against them")

// scenario defines one rendering configuration.
// Name is used as the golden subdirectory and Go sub-test name.
// Storage, Optimize, and Stable mirror the Makefile variables
// secondary_storage, enable_optimize, and the install vs install-stable target.
//
// Workload, when set, selects a Makefile workload profile (scenario=<workload>).
// Workload scenarios render the combined load-test-setup chart — workload flags
// pass through the parent chart to the load-tester subchart.
//
// SetupTarget, when set, names an alternative Makefile target for rendering the
// load-test-setup chart only — used to test opt-in chart features that have a
// dedicated make target (e.g. template-load-test-setup-chaos). This avoids
// duplicating the full storage matrix for features that are orthogonal to storage.
//
// PhysicalTenants, when true, passes physical_tenants=true to the platform
// template target. Only supported for rdbms secondary_storage (main version only).
//
// PlatformOnly, when true, renders only the platform chart. Use it for
// scenarios whose assertions are scoped to platform-only templates.
//
// PathFilter, when set, restricts golden comparison (and, under
// -update-golden, what gets committed) to rendered manifest paths with one of
// these prefixes, relative to each chart's own output tree — e.g.
// "templates/orchestration" keeps templates/orchestration/statefulset.yaml
// but drops everything else. Use this for scenarios whose purpose is
// verifying one narrow area (a specific bugfix's blast radius): committing
// and diffing the full rendered tree for every combination isn't worth the
// review noise when only a handful of files are actually relevant. Leave
// empty (the default) to keep comparing every rendered file, unchanged from
// today's behavior.
type scenario struct {
	Name            string
	Storage         string // elasticsearch | opensearch | postgresql | none
	Optimize        bool
	Stable          bool
	Workload        string // "" = default profile; e.g. "max", "realistic"
	SetupTarget     string // named make target for template-load-test-setup variants
	PhysicalTenants bool
	PlatformOnly    bool
	PathFilter      []string
}

// versionedScenario defines a scenario with a specific version
type versionedScenario struct {
	scenario
	Version string
}

// scenarios covers every unique rendering path produced by the Makefile.
// postgresql is used as the representative rdbms backend (all rdbms types share
// the same rdbms.yaml base; only the storage-specific file differs).
//
// The workload scenarios (max, realistic) are the profiles we run most often.
// They only change load-tester flags, so they use elasticsearch as a fixed
// backend and render the combined load-test-setup chart. "realistic" pulls a
// values file from the camunda-load-tests-helm repo at render time — the same
// live-latest policy as the load-tester chart itself, so its golden is
// regenerated when that file changes.
var defaultScenarios = []scenario{
	{Name: "elasticsearch", Storage: "elasticsearch", Optimize: true, Stable: false},
	// no-optimize scenarios only exist to catch the ES/OS exporter/Optimize
	// interaction bug (see PR #57771) — scoped to the orchestration templates
	// where that config actually appears, not the full rendered tree.
	// stable-87 uses the pre-orchestration zeebe template path.
	{Name: "elasticsearch-no-optimize", Storage: "elasticsearch", Optimize: false, Stable: false,
		PlatformOnly: true, PathFilter: []string{"templates/orchestration", "templates/zeebe"}},
	{Name: "opensearch", Storage: "opensearch", Optimize: true, Stable: false},
	{Name: "opensearch-no-optimize", Storage: "opensearch", Optimize: false, Stable: false,
		PlatformOnly: true, PathFilter: []string{"templates/orchestration", "templates/zeebe"}},
	{Name: "rdbms", Storage: "postgresql", Optimize: false, Stable: false},
	{Name: "rdbms-optimize", Storage: "postgresql", Optimize: true, Stable: false},
	{Name: "none", Storage: "none", Optimize: false, Stable: false},
	// Optimize cannot run without a secondary storage backend, so this covers
	// that enable_optimize=true is correctly ignored (rather than crashing or
	// silently misconfiguring the ES/OS exporter) when storage is disabled.
	{Name: "none-optimize", Storage: "none", Optimize: true, Stable: false},
	{Name: "elasticsearch-stable", Storage: "elasticsearch", Optimize: true, Stable: true},
	{Name: "opensearch-stable", Storage: "opensearch", Optimize: true, Stable: true},
	{Name: "rdbms-stable", Storage: "postgresql", Optimize: false, Stable: true},
	{Name: "max", Storage: "elasticsearch", Optimize: true, Stable: false, Workload: "max"},
	{Name: "realistic", Storage: "elasticsearch", Optimize: true, Stable: false, Workload: "realistic"},
	// SetupTarget scenarios render only the load-test-setup chart via a named
	// Makefile target, verifying opt-in chart features without duplicating the
	// full storage matrix.
	{Name: "chaos-killer", Storage: "elasticsearch", Optimize: false, SetupTarget: "template-load-test-setup-chaos"},
	// physical_tenants=true deploys a second tenant alongside the default on a
	// shared RDBMS (table-prefix isolation). Only supported for rdbms storage.
	{Name: "rdbms-physical-tenants", Storage: "postgresql", Optimize: false, PhysicalTenants: true},
}

// versions lists the setup directories under test, each the name of a directory
// under load-tests/setup/ that contains a newLoadTest.sh.
//
// To add a new version, append its directory name here and run:
//
//	make update-golden PATTERN=<version>
//
// to generate its golden files, then commit them. No other code change is needed.
var versions = []string{
	"main",
	"stable-89",
	"stable-88",
	"stable-87",
}

// generateScenarios generates the list of possible scenarios for each
// scenarios, from the list of default scenarios.
// On some versions, a specific scenario may not always be supported ; or we
// may want to drop specific scenarios which are not useful to test.
func generateScenarios(versions []string, scenarios []scenario) []versionedScenario {
	result := make([]versionedScenario, 0)

	for _, v := range versions {
		for _, s := range scenarios {

			// Filter out known invalid scenarios.
			if v == "stable-88" {
				if s.Storage == "postgresql" {
					continue
				}
			}

			if v == "stable-87" {
				if s.Storage != "elasticsearch" {
					// Only elasticsearch is supported on 8.7
					continue
				}
			}

			// physical_tenants=true is only implemented in the main Makefile.
			if s.PhysicalTenants && v != "main" {
				continue
			}

			scenario := versionedScenario{
				Version:  v,
				scenario: s,
			}

			result = append(result, scenario)
		}
	}

	return result
}

func TestGoldenFiles(t *testing.T) {
	scenarios := generateScenarios(versions, defaultScenarios)

	t.Logf("Testing %d setup version(s): %v (%d total scenarios)", len(versions), versions, len(scenarios))

	for _, s := range scenarios {
		// Namespace is unique per (version, scenario) to avoid directory
		// collisions when all sub-tests run in parallel.
		namespace := "c8-golden-" + s.Version + "-" + s.Name

		t.Run(namespace, func(t *testing.T) {
			t.Parallel()

			ns := Scaffold(t, s.Version, namespace, s.Storage, strconv.FormatBool(s.Optimize))
			defer ns.Cleanup()

			if s.Workload != "" {
				renderAndAssert(t, s.Version, s.Name, "load-test-setup", ns, "template-load-test-setup", s.Workload, s.PathFilter)
				return
			}

			// SetupTarget scenarios verify opt-in load-test-setup features
			// via their dedicated Makefile target. They render only that
			// chart to avoid duplicating the full platform/load-tester matrix.
			if s.SetupTarget != "" {
				renderAndAssert(t, s.Version, s.Name, "load-test-setup", ns, s.SetupTarget, "", s.PathFilter)
				return
			}

			// The platform chart has separate make targets for the
			// install vs install-stable values composition.
			platformTarget := "template"
			if s.Stable {
				platformTarget = "template-stable"
			}

			var extraVars []string
			if s.PhysicalTenants {
				extraVars = append(extraVars, "physical_tenants=true")
			}

			renderAndAssert(t, s.Version, s.Name, "platform", ns, platformTarget, "", s.PathFilter, extraVars...)
			if s.PlatformOnly {
				return
			}
			renderAndAssert(t, s.Version, s.Name, "load-test-setup", ns, "template-load-test-setup", "", s.PathFilter, extraVars...)
		})
	}
}

// TestInstallLoadTestSetupKeepsMakefileFlagsWhenAdditionalConfigurationIsProvided
// simulates the real CI invocation (camunda-load-test.yml), which always sets
// additional_load_test_setup_configuration on the make command line. In GNU
// Make, a command-line-origin variable silently suppresses any later `+=` to
// it inside the Makefile, so a makefile-computed default routed through that
// variable would be dropped in real CI while a plain `make` invocation still
// looks fine. Runs across every versioned setup dir so a version-specific
// regression of this pattern (as happened in stable-88/stable-89) fails here
// instead of only surfacing in a manual review pass.
func TestInstallLoadTestSetupKeepsMakefileFlagsWhenAdditionalConfigurationIsProvided(t *testing.T) {
	for _, version := range versions {
		t.Run(version, func(t *testing.T) {
			t.Parallel()

			ns := Scaffold(t, version, "c8-golden-setup-flags-"+version, "elasticsearch", "true")
			defer ns.Cleanup()

			cmd := exec.Command(
				"make",
				"-n",
				"install-load-test-setup",
				"chaos=true",
				"additional_load_test_setup_configuration=--set metricsExporter.image.tag=test-tag",
			)
			cmd.Dir = ns.Dir
			out, err := cmd.CombinedOutput()
			require.NoError(t, err, "make dry-run failed:\n%s", string(out))

			helmCommand := string(out)
			require.Contains(t, helmCommand, "--set chaosKiller.enabled=true")
			require.Contains(t, helmCommand, "--set metricsExporter.image.tag=test-tag")
			require.Less(
				t,
				strings.Index(helmCommand, "--set chaosKiller.enabled=true"),
				strings.Index(helmCommand, "--set metricsExporter.image.tag=test-tag"),
			)
		})
	}
}

// renderAndAssert renders a chart via the scaffolded Makefile's make target and
// compares (or writes) the resulting manifest tree against the golden directory.
// pathFilter, when non-empty, restricts the comparison to that subset of
// rendered paths (see scenario.PathFilter). extraVars are additional make
// variable assignments forwarded to Render.
func renderAndAssert(t *testing.T, version, scenario, chartName string, ns *ScaffoldedNamespace, makeTarget, workload string, pathFilter []string, extraVars ...string) {
	t.Helper()

	srcDir := ns.Render(t, makeTarget, workload, extraVars...)
	assertGoldenDir(t, version, scenario, chartName, srcDir, *update, pathFilter)
}

// TestNormalize verifies that the normalize function strips expected fields
// and is idempotent.
func TestNormalize(t *testing.T) {
	input := "metadata:\n" +
		"  labels:\n" +
		"    helm.sh/chart: camunda-platform-10.2.0\n" +
		"    app.kubernetes.io/version: \"8.6.0\"\n" +
		"    deadline-date: \"2025-12-31\"\n" +
		"  annotations:\n" +
		"    checksum/config: abc123def456\n" +
		"spec:\n" +
		"  containers:\n" +
		"  - image: registry.example.com/camunda/zeebe:8.6.0\n"

	got := normalize(input)

	require.NotContains(t, got, "helm.sh/chart")
	require.NotContains(t, got, "checksum/config")
	require.NotContains(t, got, "app.kubernetes.io/version")
	require.NotContains(t, got, "2025-12-31")            // date value normalized away
	require.Contains(t, got, "deadline-date: TEST_DATE") // label kept, value normalized
	require.Contains(t, got, "image: registry.example.com/camunda/zeebe:8.6.0")

	// Idempotent.
	require.Equal(t, got, normalize(got))
}

func TestShouldCollectAllManifestsWithoutPathFilter(t *testing.T) {
	// given
	root := t.TempDir()
	writeTestManifest(t, root, "Chart.yaml", "name: platform\n")
	writeTestManifest(t, root, "templates/orchestration/deployment.yaml", "kind: Deployment\n")

	// when
	manifests := collectManifests(t, root, nil)

	// then
	require.Equal(t, []string{"Chart.yaml", "templates/orchestration/deployment.yaml"}, sortedKeys(manifests))
}

func TestShouldCollectOnlyPathFilterMatches(t *testing.T) {
	// given
	root := t.TempDir()
	writeTestManifest(t, root, "templates/orchestration/deployment.yaml", "kind: Deployment\n")
	writeTestManifest(t, root, "templates/identity/deployment.yaml", "kind: Deployment\n")

	// when
	manifests := collectManifests(t, root, []string{"templates/orchestration"})

	// then
	require.Equal(t, []string{"templates/orchestration/deployment.yaml"}, sortedKeys(manifests))
}

func TestShouldReturnNoManifestsWhenPathFilterMatchesNothing(t *testing.T) {
	// given
	root := t.TempDir()
	writeTestManifest(t, root, "templates/orchestration/deployment.yaml", "kind: Deployment\n")

	// when
	manifests := collectManifests(t, root, []string{"templates/missing"})

	// then
	require.Empty(t, manifests)
}

func TestShouldMatchPathFilterByExactFileOrDirectoryPrefix(t *testing.T) {
	// given
	filter := []string{"./Chart.yaml", "templates/orchestration/"}

	// when / then
	require.True(t, matchesPathFilter("Chart.yaml", filter))
	require.True(t, matchesPathFilter("templates/orchestration/deployment.yaml", filter))
	require.False(t, matchesPathFilter("templates/orchestration-extra/deployment.yaml", filter))
	require.False(t, matchesPathFilter("templates/identity/deployment.yaml", filter))
}

func writeTestManifest(t *testing.T, root, rel, content string) {
	t.Helper()

	path := filepath.Join(root, filepath.FromSlash(rel))
	require.NoError(t, os.MkdirAll(filepath.Dir(path), 0o755))
	require.NoError(t, os.WriteFile(path, []byte(content), 0o644))
}
