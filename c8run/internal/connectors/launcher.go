package connectors

import (
	"strconv"
	"strings"
)

func UsePropertiesLauncher(version string) bool {
	major, minor, ok := parseMajorMinor(version)
	if !ok {
		return false
	}
	if major > 8 {
		return true
	}
	if major < 8 {
		return false
	}
	return minor >= 9
}

func parseMajorMinor(version string) (int, int, bool) {
	version = strings.TrimSpace(version)
	if version == "" {
		return 0, 0, false
	}
	base := strings.SplitN(version, "-", 2)[0]
	parts := strings.Split(base, ".")
	if len(parts) < 2 {
		return 0, 0, false
	}
	major, err := strconv.Atoi(parts[0])
	if err != nil {
		return 0, 0, false
	}
	minor, err := strconv.Atoi(parts[1])
	if err != nil {
		return 0, 0, false
	}
	return major, minor, true
}
