// scaffold.go
package golden

import (
	"os"
	"os/exec"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
)

// ScaffoldedNamespace is the product of running newLoadTest.sh. It renders Helm
// charts through the scaffolded Makefile's own `template*` targets, so the test
// exercises the exact values composition that `make install` uses rather than a
// reimplementation of it.
type ScaffoldedNamespace struct {
	Dir  string
	Name string
}

// Cleanup removes the scaffolded namespace directory.
// Always call via defer: defer ns.Cleanup()
func (ns *ScaffoldedNamespace) Cleanup() {
	_ = os.RemoveAll(ns.Dir)
}

// Scaffold runs newLoadTest.sh <namespace> <storage> 1 <optimize> true with
// GIT_AUTHOR=golden-test in the environment, from the given versioned setup
// directory, and returns a ScaffoldedNamespace.
//
// newLoadTest.sh reads the author label from the GIT_AUTHOR env var
// (git_author=${GIT_AUTHOR:-$(compute_git_author)}); pinning it to a constant
// makes the camunda.io/created-by label deterministic across machines. The
// storage and optimize arguments are baked into the generated Makefile, which
// drives the values composition for the template targets used by Render.
//
// setupDirName is the versioned setup directory name: "main", "stable-89", etc.
// namespace must be unique per (version, scenario) pair to allow parallel execution.
// storage is one of: elasticsearch, opensearch, postgresql, none.
// optimize is "true" or "false".
func Scaffold(t *testing.T, setupDirName, namespace, storage, optimize string) *ScaffoldedNamespace {
	t.Helper()

	scriptDir := filepath.Join(repoRoot(t), "load-tests", "setup", setupDirName)
	script := filepath.Join(scriptDir, "newLoadTest.sh")

	// newLoadTest.sh creates the namespace directory as a sibling of the setup dir:
	// ROOT_DIR = parent of SCRIPT_DIR = load-tests/setup/
	namespaceDir := filepath.Join(filepath.Dir(scriptDir), namespace)

	// Clean up any leftover from a previous interrupted run.
	_ = os.RemoveAll(namespaceDir)

	cmd := exec.Command(script,
		namespace, storage, "1", optimize, "true", // 5th arg: enable_single_zone=true (default)
	)
	// newLoadTest.sh copies sibling files (Makefile, values/) with relative
	// paths, so it must run from its own directory.
	cmd.Dir = scriptDir
	cmd.Env = append(os.Environ(), "GIT_AUTHOR=golden-test")
	out, err := cmd.CombinedOutput()
	require.NoError(t, err, "newLoadTest.sh failed:\n%s", string(out))

	return &ScaffoldedNamespace{Dir: namespaceDir, Name: namespace}
}

// Render runs `make <target>` in the namespace directory and returns the path to
// the directory helm wrote the rendered manifests into via --output-dir (one file
// per chart template, mirroring the chart structure). When workload is non-empty
// it is passed as `scenario=<workload>` (e.g. max, realistic), selecting the
// Makefile's workload profile flags — exactly as `make install scenario=<workload>`.
//
// The template targets pin --namespace $(namespace), so .Release.Namespace matches
// what `make install` produces and is deterministic across machines.
func (ns *ScaffoldedNamespace) Render(t *testing.T, target, workload string) string {
	t.Helper()

	// Render into an isolated, auto-cleaned dir via the Makefile's
	// template_output_dir override, so output never mixes with scaffold files.
	outDir := t.TempDir()
	args := []string{target, "template_output_dir=" + outDir}
	if workload != "" {
		args = append(args, "scenario="+workload)
	}
	cmd := exec.Command("make", args...)
	cmd.Dir = ns.Dir
	out, err := cmd.CombinedOutput()
	require.NoError(t, err, "make %s failed:\n%s", target, string(out))

	// helm --output-dir writes a single top-level chart directory; return it as
	// the manifest tree root.
	entries, err := os.ReadDir(outDir)
	require.NoError(t, err)
	require.Len(t, entries, 1, "expected one chart output dir in %s, got %v", outDir, entries)
	return filepath.Join(outDir, entries[0].Name())
}

// repoRoot walks up from the test package directory until it finds the .git
// entry that marks the monorepo root.
func repoRoot(t *testing.T) string {
	t.Helper()
	dir, err := filepath.Abs(".")
	require.NoError(t, err)
	for {
		if _, err := os.Stat(filepath.Join(dir, ".git")); err == nil {
			return dir
		}
		parent := filepath.Dir(dir)
		require.NotEqual(t, parent, dir, "could not find repo root")
		dir = parent
	}
}
