package connectors

import "testing"

func TestUsePropertiesLauncher(t *testing.T) {
	t.Setenv(propertiesLauncherEnvVar, "true")
	if !UsePropertiesLauncher() {
		t.Fatalf("expected PropertiesLauncher to be enabled when env var is true")
	}

	t.Setenv(propertiesLauncherEnvVar, "false")
	if UsePropertiesLauncher() {
		t.Fatalf("expected PropertiesLauncher to be disabled when env var is false")
	}

	t.Setenv(propertiesLauncherEnvVar, "YES")
	if !UsePropertiesLauncher() {
		t.Fatalf("expected PropertiesLauncher to support truthy string")
	}

	t.Setenv(propertiesLauncherEnvVar, "")
	if UsePropertiesLauncher() {
		t.Fatalf("expected PropertiesLauncher to be disabled when env var unset")
	}
}
