package main

import (
    "os"
    "testing"
)

// We test getBaseCommandSettings indirectly by constructing args and invoking parsing logic.

func TestStartupUrlRecomputedWhenPortOverridden(t *testing.T) {
    // Simulate: c8run start --port 9090 (no --startup-url)
    osArgs := []string{"c8run", "start", "--port", "9090"}
    oldArgs := os.Args
    os.Args = osArgs
    defer func(){ os.Args = oldArgs }()

    settings, err := getBaseCommandSettings("start")
    if err != nil { t.Fatalf("unexpected error: %v", err) }

    expected := "http://localhost:9090/operate"
    if settings.StartupUrl != expected {
        t.Fatalf("expected StartupUrl to be %s, got %s", expected, settings.StartupUrl)
    }
}

func TestStartupUrlNotRecomputedWhenProvided(t *testing.T) {
    // Simulate: c8run start --port 9090 --startup-url http://example.test/custom
    osArgs := []string{"c8run", "start", "--port", "9090", "--startup-url", "http://example.test/custom"}
    oldArgs := os.Args
    os.Args = osArgs
    defer func(){ os.Args = oldArgs }()

    settings, err := getBaseCommandSettings("start")
    if err != nil { t.Fatalf("unexpected error: %v", err) }

    if settings.StartupUrl != "http://example.test/custom" {
        t.Fatalf("expected StartupUrl to remain custom value, got %s", settings.StartupUrl)
    }
}
