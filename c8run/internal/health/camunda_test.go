package health

import (
    "bytes"
    "io"
    "os"
    "path/filepath"
    "strconv"
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

    // Expect all dynamic component ports to equal 9090
    for _, component := range []string{"Operate", "Tasklist", "Identity", "Orchestration Cluster API"} {
        if !strings.Contains(out, "http://localhost:9090") {
            t.Fatalf("expected %s endpoint to include port 9090; output: %s", component, out)
        }
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
    expectedPorts := map[string]int{"Operate": 8080, "Tasklist": 8080, "Identity": 8080, "Orchestration Cluster API": 8080}
    for name, port := range expectedPorts {
        if !strings.Contains(out, "http://localhost:"+strconv.Itoa(port)) {
            t.Fatalf("expected %s endpoint to include port %d; output: %s", name, port, out)
        }
    }
    // Ensure the provided (ignored) port is not shown for those endpoints
    if strings.Contains(out, "http://localhost:9090/operate") || strings.Contains(out, "http://localhost:9090/tasklist") || strings.Contains(out, "http://localhost:9090/identity") || strings.Contains(out, "http://localhost:9090/v2/") {
        t.Fatalf("docker mode should ignore custom port 9090; output: %s", out)
    }
}
