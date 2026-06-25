// golden_test.go
package golden

import (
	"flag"
	"os/exec"
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
// Workload scenarios render only the load-tester chart — that is where the
// workload flags apply — so they don't duplicate the storage-matrix renders.
//
// SetupTarget, when set, names an alternative Makefile target for rendering the
// load-test-setup chart only — used to test opt-in chart features that have a
// dedicated make target (e.g. template-load-test-setup-chaos). This avoids
// duplicating the full storage matrix for features that are orthogonal to storage.
type scenario struct {
	Name        string
	Storage     string // elasticsearch | opensearch | postgresql | none
	Optimize    bool
	Stable      bool
	Workload    string // "" = default profile; e.g. "max", "realistic"
	SetupTarget string // named make target for template-load-test-setup variants
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
// backend and render just the load-tester chart. "realistic" pulls a values file
// from the camunda-load-tests-helm repo at render time — the same live-latest
// policy as the load-tester chart itself, so its golden is regenerated when that
// file changes.
var defaultScenarios = []scenario{
	{Name: "elasticsearch", Storage: "elasticsearch", Optimize: true, Stable: false},
	{Name: "opensearch", Storage: "opensearch", Optimize: true, Stable: false},
	{Name: "rdbms", Storage: "postgresql", Optimize: false, Stable: false},
	{Name: "rdbms-optimize", Storage: "postgresql", Optimize: true, Stable: false},
	{Name: "none", Storage: "none", Optimize: false, Stable: false},
	{Name: "elasticsearch-stable", Storage: "elasticsearch", Optimize: true, Stable: true},
	{Name: "opensearch-stable", Storage: "opensearch", Optimize: true, Stable: true},
	{Name: "rdbms-stable", Storage: "postgresql", Optimize: false, Stable: true},
	{Name: "max", Storage: "elasticsearch", Optimize: true, Stable: false, Workload: "max"},
	{Name: "realistic", Storage: "elasticsearch", Optimize: true, Stable: false, Workload: "realistic"},
	// SetupTarget scenarios render only the load-test-setup chart via a named
	// Makefile target, verifying opt-in chart features without duplicating the
	// full storage matrix.
	{Name: "chaos-killer", Storage: "elasticsearch", Optimize: false, SetupTarget: "template-load-test-setup-chaos"},
}

// versions lists the setup directories under test, each the name of a directory
// under load-tests/setup/ that contains a newLoadTest.sh.
//
// Only "main" is active for now. The stable versions scaffold identically; to
// enable one, uncomment its line and run:
//
//	go test -update-golden -run 'TestGoldenFiles/<version>' ./...
//
// to generate its golden files, then commit them. No other code change is needed.
var versions = []string{
	"main",
	"stable-89",
	"stable-88",
	// "stable-87",
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

			//if v == "stable-87" {
			//    if s.Storage != "elasticsearch" {
			//        // Only elasticsearch is supported on 8.7
			//        continue
			//    }
			//}

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

			// Workload scenarios only affect the load-tester chart, so we
			// render just that one with the workload profile applied.
			if s.Workload != "" {
				renderAndAssert(t, s.Version, s.Name, "load-tester", ns, "template-load-test", s.Workload)
				return
			}

			// SetupTarget scenarios verify opt-in load-test-setup features
			// via their dedicated Makefile target. They render only that
			// chart to avoid duplicating the full platform/load-tester matrix.
			if s.SetupTarget != "" {
				renderAndAssert(t, s.Version, s.Name, "load-test-setup", ns, s.SetupTarget, "")
				return
			}

			// The platform chart has separate make targets for the
			// install vs install-stable values composition.
			platformTarget := "template"
			if s.Stable {
				platformTarget = "template-stable"
			}

			renderAndAssert(t, s.Version, s.Name, "platform", ns, platformTarget, "")
			renderAndAssert(t, s.Version, s.Name, "load-tester", ns, "template-load-test", "")
			renderAndAssert(t, s.Version, s.Name, "load-test-setup", ns, "template-load-test-setup", "")
		})
	}
}

func TestInstallLoadTestSetupKeepsMakefileFlagsWhenAdditionalConfigurationIsProvided(t *testing.T) {
	ns := Scaffold(t, "main", "c8-golden-setup-flags", "postgresql", "true")
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
	require.Contains(t, helmCommand, "--set metricsExporter.database.url=http://elastic:9200")
	require.Contains(t, helmCommand, "--set chaosKiller.enabled=true")
	require.Contains(t, helmCommand, "--set metricsExporter.image.tag=test-tag")
	require.Less(
		t,
		strings.Index(helmCommand, "--set chaosKiller.enabled=true"),
		strings.Index(helmCommand, "--set metricsExporter.image.tag=test-tag"),
	)
}

// renderAndAssert renders a chart via the scaffolded Makefile's make target and
// compares (or writes) the resulting manifest tree against the golden directory.
func renderAndAssert(t *testing.T, version, scenario, chartName string, ns *ScaffoldedNamespace, makeTarget, workload string) {
	t.Helper()

	srcDir := ns.Render(t, makeTarget, workload)
	assertGoldenDir(t, version, scenario, chartName, srcDir, *update)
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
	require.NotContains(t, got, "2025-12-31")            // date value normalized away
	require.Contains(t, got, "deadline-date: TEST_DATE") // label kept, value normalized
	require.Contains(t, got, "image: registry.example.com/camunda/zeebe:TEST")
	require.Contains(t, got, `app.kubernetes.io/version: "8.6.0"`) // kept

	// Idempotent.
	require.Equal(t, got, normalize(got))
}
