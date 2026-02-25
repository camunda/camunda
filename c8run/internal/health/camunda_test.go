package health

import (
    "bytes"
    "io"
    "os"
    "path/filepath"
    "strings"
    "testing"

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

func TestPrintStatus_NonDockerUsesPortFlag(t *testing.T) {
    // Ensure working directory contains endpoints.txt
    cwd, err := os.Getwd()
    if err != nil { t.Fatalf("failed to get wd: %v", err) }
    root := filepath.Clean(filepath.Join(cwd, "../..")) // c8run module root
    if err := os.Chdir(root); err != nil { t.Fatalf("failed to chdir to module root: %v", err) }
    t.Cleanup(func(){ _ = os.Chdir(cwd) })

    settings := types.C8RunSettings{Port: 9090, Docker: false}
    out := captureOutput(t, func() {
        if err := PrintStatus(settings); err != nil { t.Fatalf("PrintStatus failed: %v", err) }
    })

    // Verify specific endpoint paths are rendered with the correct port
    expectedEndpoints := map[string]string{
        "Operate":                   "http://localhost:9090/operate",
        "Tasklist":                  "http://localhost:9090/tasklist",
        "Identity":                  "http://localhost:9090/identity",
        "Orchestration Cluster API": "http://localhost:9090/v2/",
        "Orchestration Cluster":     "http://localhost:9090/mcp/cluster",
    }
    for label, endpoint := range expectedEndpoints {
        assertEndpointForLabel(t, out, label, endpoint)
    }
}

func TestPrintStatus_DockerModeIgnoresPortFlag(t *testing.T) {
    // Ensure working directory contains endpoints.txt
    cwd, err := os.Getwd()
    if err != nil { t.Fatalf("failed to get wd: %v", err) }
    root := filepath.Clean(filepath.Join(cwd, "../.."))
    if err := os.Chdir(root); err != nil { t.Fatalf("failed to chdir to module root: %v", err) }
    t.Cleanup(func(){ _ = os.Chdir(cwd) })

    settings := types.C8RunSettings{Port: 9090, Docker: true}
    out := captureOutput(t, func() {
        if err := PrintStatus(settings); err != nil { t.Fatalf("PrintStatus failed: %v", err) }
    })

    // Verify fixed docker ports are present - all components use port 8080 in Docker mode
    expectedEndpoints := map[string]string{
        "Operate":                   "http://localhost:8080/operate",
        "Tasklist":                  "http://localhost:8080/tasklist",
        "Identity":                  "http://localhost:8080/identity",
        "Orchestration Cluster API": "http://localhost:8080/v2/",
        "Orchestration Cluster":     "http://localhost:8080/mcp/cluster",
    }
    for label, endpoint := range expectedEndpoints {
        assertEndpointForLabel(t, out, label, endpoint)
    }
    // Ensure the provided (ignored) port is not shown for those endpoints
    if strings.Contains(out, "http://localhost:9090/operate") || strings.Contains(out, "http://localhost:9090/tasklist") || strings.Contains(out, "http://localhost:9090/identity") || strings.Contains(out, "http://localhost:9090/v2/") || strings.Contains(out, "http://localhost:9090/mcp/cluster") {
        t.Fatalf("docker mode should ignore custom port 9090; output: %s", out)
    }
}
