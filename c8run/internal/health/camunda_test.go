package health

import (
	"bytes"
	"context"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/camunda/camunda/c8run/internal/types"
)

// captureOutput executes f while capturing stdout output and returns it as string.
func captureOutput(t *testing.T, f func()) string {
	t.Helper()

	old := os.Stdout
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatalf("failed to create pipe: %v", err)
	}
	os.Stdout = w
	defer func() { os.Stdout = old }()

	done := make(chan struct{})
	var buf bytes.Buffer
	go func() {
		_, _ = io.Copy(&buf, r)
		close(done)
	}()

	f()
	_ = w.Close()
	<-done
	return buf.String()
}

// assertEndpointForLabel finds the output line containing "label:" and asserts it also contains endpoint.
func assertEndpointForLabel(t *testing.T, out, label, endpoint string) {
	t.Helper()

	prefix := label + ":"
	for _, line := range strings.Split(out, "\n") {
		if strings.Contains(line, prefix) {
			if !strings.Contains(line, endpoint) {
				t.Fatalf("line for %q does not contain expected endpoint %s: %s", label, endpoint, line)
			}
			return
		}
	}
	t.Fatalf("no line found containing label %q; output: %s", label, out)
}

type stubOpener struct {
	url string
}

func (s *stubOpener) OpenBrowser(_ context.Context, url string) error {
	s.url = url
	return nil
}

func TestShouldUsePortFlagForStatusOutputOutsideDocker(t *testing.T) {
	// given
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get wd: %v", err)
	}
	root := filepath.Clean(filepath.Join(cwd, "../.."))
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to chdir to module root: %v", err)
	}
	t.Cleanup(func() { _ = os.Chdir(cwd) })

	settings := types.C8RunSettings{Port: 9090, Docker: false}

	// when
	out := captureOutput(t, func() {
		if err := PrintStatus(settings); err != nil {
			t.Fatalf("PrintStatus failed: %v", err)
		}
	})

	// then
	expectedEndpoints := map[string]string{
		"Operate":                   "http://localhost:9090/operate",
		"Tasklist":                  "http://localhost:9090/tasklist",
		"Admin":                     "http://localhost:9090/admin",
		"Orchestration Cluster API": "http://localhost:9090/v2/",
		"Orchestration Cluster":     "http://localhost:9090/mcp/cluster",
	}
	for label, endpoint := range expectedEndpoints {
		assertEndpointForLabel(t, out, label, endpoint)
	}
}

func TestShouldIgnorePortFlagForStatusOutputInDockerMode(t *testing.T) {
	// given
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get wd: %v", err)
	}
	root := filepath.Clean(filepath.Join(cwd, "../.."))
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to chdir to module root: %v", err)
	}
	t.Cleanup(func() { _ = os.Chdir(cwd) })

	settings := types.C8RunSettings{Port: 9090, Docker: true}

	// when
	out := captureOutput(t, func() {
		if err := PrintStatus(settings); err != nil {
			t.Fatalf("PrintStatus failed: %v", err)
		}
	})

	// then
	expectedEndpoints := map[string]string{
		"Operate":                   "http://localhost:8080/operate",
		"Tasklist":                  "http://localhost:8080/tasklist",
		"Admin":                     "http://localhost:8080/admin",
		"Orchestration Cluster API": "http://localhost:8080/v2/",
		"Orchestration Cluster":     "http://localhost:8080/mcp/cluster",
	}
	for label, endpoint := range expectedEndpoints {
		assertEndpointForLabel(t, out, label, endpoint)
	}
	if strings.Contains(out, "http://localhost:9090/operate") ||
		strings.Contains(out, "http://localhost:9090/tasklist") ||
		strings.Contains(out, "http://localhost:9090/admin") ||
		strings.Contains(out, "http://localhost:9090/v2/") ||
		strings.Contains(out, "http://localhost:9090/mcp/cluster") {
		t.Fatalf("docker mode should ignore custom port 9090; output: %s", out)
	}
}

func TestShouldMarkQuickstartAsSeenAfterSuccessfulStartup(t *testing.T) {
	// given
	markerPath := filepath.Join(t.TempDir(), ".c8run-quickstart-seen")
	settings := types.C8RunSettings{
		StartupUrl:        "http://localhost:8080/operate",
		StartupMarkerPath: markerPath,
	}
	opener := &stubOpener{}

	originalIsRunningFunc := isRunningFunc
	originalPrintStatusFunc := printStatusFunc
	t.Cleanup(func() {
		isRunningFunc = originalIsRunningFunc
		printStatusFunc = originalPrintStatusFunc
	})

	isRunningFunc = func(_ context.Context, _ string, _ string, _ int, _ time.Duration) bool {
		return true
	}
	printStatusFunc = func(types.C8RunSettings) error {
		return nil
	}

	// when
	err := QueryCamunda(context.Background(), opener, "Camunda", settings, 0)
	if err != nil {
		t.Fatalf("QueryCamunda failed: %v", err)
	}

	// then
	if opener.url != settings.StartupUrl {
		t.Fatalf("expected browser to open %s, got %s", settings.StartupUrl, opener.url)
	}
	if _, err := os.Stat(markerPath); err != nil {
		t.Fatalf("expected marker file to be created: %v", err)
	}
}
