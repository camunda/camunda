package main

import (
	"os"
	"testing"

	"github.com/camunda/camunda/c8run/internal/types"
)

// We test getBaseCommandSettings indirectly by constructing args and invoking parsing logic.

func TestDefaultStartupUrlUsesDocsForEightNineAndLater(t *testing.T) {
	settings := buildSettingsWithVersion(t, []string{"c8run", "start"}, "8.9.0")

	if settings.StartupUrl != docsStartupURL {
		t.Fatalf("expected StartupUrl to be docs URL, got %s", settings.StartupUrl)
	}
}

func TestDefaultStartupUrlUsesOperateForOlderVersions(t *testing.T) {
	settings := buildSettingsWithVersion(t, []string{"c8run", "start", "--port", "9090"}, "8.8.0")

	expected := "http://localhost:9090/operate"
	if settings.StartupUrl != expected {
		t.Fatalf("expected StartupUrl to be %s, got %s", expected, settings.StartupUrl)
	}
}

func TestStartupUrlNotRecomputedWhenProvided(t *testing.T) {
	settings := buildSettingsWithVersion(t, []string{
		"c8run", "start", "--port", "9090", "--startup-url", "http://example.test/custom",
	}, "8.9.0")

	if settings.StartupUrl != "http://example.test/custom" {
		t.Fatalf("expected StartupUrl to remain custom value, got %s", settings.StartupUrl)
	}
}

func buildSettingsWithVersion(t *testing.T, args []string, camundaVersion string) types.C8RunSettings {
	t.Helper()

	oldArgs := os.Args
	os.Args = args
	t.Cleanup(func() { os.Args = oldArgs })

	settings, startupURLProvided, err := getBaseCommandSettings("start")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !startupURLProvided {
		settings.StartupUrl = createDefaultStartupUrl(&settings, camundaVersion)
	}
	return settings
}
