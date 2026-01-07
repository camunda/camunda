package connectors

import (
	"os"
	"strings"
)

const propertiesLauncherEnvVar = "CONNECTORS_USE_PROPERTIES_LAUNCHER"

func UsePropertiesLauncher() bool {
	value := strings.TrimSpace(os.Getenv(propertiesLauncherEnvVar))
	if value == "" {
		return false
	}

	value = strings.ToLower(value)
	switch value {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}
