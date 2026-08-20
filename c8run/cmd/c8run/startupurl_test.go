package main

import (
	"os"
	"runtime"
	"testing"

	"github.com/camunda/camunda/c8run/internal/startupurl"
	"github.com/camunda/camunda/c8run/internal/types"
)

// We test getBaseCommandSettings indirectly by constructing args and invoking parsing logic.

func TestShouldUseQuickstartUrlForFirstStartupOnEightNineAndLater(t *testing.T) {
	// given
	baseDir := t.TempDir()
	setUserConfigEnv(t, t.TempDir())

	// when
	settings := buildSettingsWithVersion(t, []string{"c8run", "start"}, "8.9.0", baseDir)

	// then
	if settings.StartupUrl != docsStartupURL {
		t.Fatalf("expected StartupUrl to be docs URL, got %s", settings.StartupUrl)
	}
}

func TestShouldUseOperateUrlAfterQuickstartWasSeen(t *testing.T) {
	// given
	baseDir := t.TempDir()
	setUserConfigEnv(t, t.TempDir())
	markerPath := startupurl.MarkerPath(baseDir)
	if err := startupurl.MarkSeen(markerPath); err != nil {
		t.Fatalf("failed to create quickstart marker: %v", err)
	}

	// when
	settings := buildSettingsWithVersion(t, []string{"c8run", "start"}, "8.9.0", baseDir)

	// then
	expected := "http://localhost:8080/operate"
	if settings.StartupUrl != expected {
		t.Fatalf("expected StartupUrl to be %s, got %s", expected, settings.StartupUrl)
	}
}

func TestShouldUseOperateUrlForOlderVersions(t *testing.T) {
	// given
	baseDir := t.TempDir()
	setUserConfigEnv(t, t.TempDir())

	// when
	settings := buildSettingsWithVersion(t, []string{"c8run", "start", "--port", "9090"}, "8.8.0", baseDir)

	// then
	expected := "http://localhost:9090/operate"
	if settings.StartupUrl != expected {
		t.Fatalf("expected StartupUrl to be %s, got %s", expected, settings.StartupUrl)
	}
}

func TestShouldKeepCustomStartupUrlWhenProvided(t *testing.T) {
	// given
	baseDir := t.TempDir()
	setUserConfigEnv(t, t.TempDir())

	// when
	settings := buildSettingsWithVersion(t, []string{
		"c8run", "start", "--port", "9090", "--startup-url", "http://example.test/custom",
	}, "8.9.0", baseDir)

	// then
	if settings.StartupUrl != "http://example.test/custom" {
		t.Fatalf("expected StartupUrl to remain custom value, got %s", settings.StartupUrl)
	}
}

func buildSettingsWithVersion(t *testing.T, args []string, camundaVersion string, baseDir string) types.C8RunSettings {
	t.Helper()

	oldArgs := os.Args
	os.Args = args
	t.Cleanup(func() { os.Args = oldArgs })

	settings, startupURLProvided, err := getBaseCommandSettings("start")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	settings.StartupMarkerPath = startupurl.MarkerPath(baseDir)
	if !startupURLProvided {
		settings.StartupUrl = createDefaultStartupUrl(&settings, camundaVersion)
	}
	return settings
}

func setUserConfigEnv(t *testing.T, dir string) {
	t.Helper()

	switch runtime.GOOS {
	case "windows":
		t.Setenv("APPDATA", dir)
	case "darwin":
		t.Setenv("HOME", dir)
	default:
		t.Setenv("XDG_CONFIG_HOME", dir)
		t.Setenv("HOME", dir)
	}
}
