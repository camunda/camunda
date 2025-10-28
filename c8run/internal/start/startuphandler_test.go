package start

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/camunda/camunda/c8run/internal/processmanagement"
)

func TestStartApplicationTruncatesExistingLogFile(t *testing.T) {
	t.Setenv("GO_WANT_HELPER_PROCESS", "0")

	handler := &StartupHandler{
		ProcessHandler: &processmanagement.ProcessHandler{},
	}

	tempDir := t.TempDir()
	logPath := filepath.Join(tempDir, "elasticsearch.log")
	pidPath := filepath.Join(tempDir, "elasticsearch.pid")

	if err := os.WriteFile(logPath, []byte("old log entry\n"), 0644); err != nil {
		t.Fatalf("failed to seed log file: %v", err)
	}

	stop := func() {}

	firstCmd := helperCommand("first run\n")
	if err := handler.startApplication(firstCmd, pidPath, logPath, stop); err != nil {
		t.Fatalf("startApplication failed: %v", err)
	}
	if err := firstCmd.Wait(); err != nil {
		t.Fatalf("first helper process failed: %v", err)
	}

	firstLogContents, err := os.ReadFile(logPath)
	if err != nil {
		t.Fatalf("failed to read log after first run: %v", err)
	}
	if strings.Contains(string(firstLogContents), "old log entry") {
		t.Errorf("expected old log contents to be truncated, got %q", string(firstLogContents))
	}

	secondCmd := helperCommand("second run\n")
	if err := handler.startApplication(secondCmd, pidPath, logPath, stop); err != nil {
		t.Fatalf("startApplication failed on second run: %v", err)
	}
	if err := secondCmd.Wait(); err != nil {
		t.Fatalf("second helper process failed: %v", err)
	}

	secondLogContents, err := os.ReadFile(logPath)
	if err != nil {
		t.Fatalf("failed to read log after second run: %v", err)
	}
	if strings.Contains(string(secondLogContents), "first run") {
		t.Errorf("expected log to be truncated before second start, got %q", string(secondLogContents))
	}
	if !strings.Contains(string(secondLogContents), "second run") {
		t.Errorf("expected log to capture second run output, got %q", string(secondLogContents))
	}
}

func helperCommand(output string) *exec.Cmd {
	cmd := exec.Command(os.Args[0], "-test.run=TestHelperProcess", "--", output)
	cmd.Env = append(os.Environ(), "GO_WANT_HELPER_PROCESS=1", "HELPER_PROCESS_OUTPUT="+output)
	return cmd
}

func TestHelperProcess(t *testing.T) {
	if os.Getenv("GO_WANT_HELPER_PROCESS") != "1" {
		return
	}
	output := os.Getenv("HELPER_PROCESS_OUTPUT")
	if _, err := os.Stdout.WriteString(output); err != nil {
		os.Exit(1)
	}
	os.Exit(0)
}
