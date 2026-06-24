package startupurl

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/camunda/camunda/c8run/internal/types"
)

const (
	DocsURL         = "https://docs.camunda.io/docs/next/self-managed/quickstart/developer-quickstart/c8run/#work-with-camunda-8-run"
	markerFileName  = ".c8run-quickstart-seen"
	markerFileValue = "seen\n"
)

var (
	userConfigDir = os.UserConfigDir
	userHomeDir   = os.UserHomeDir
)

func MarkerPath(baseDir string) string {
	if configDir, err := userConfigDir(); err == nil && strings.TrimSpace(configDir) != "" {
		return filepath.Join(configDir, "camunda", "c8run", markerFileName)
	}

	if homeDir, err := userHomeDir(); err == nil && strings.TrimSpace(homeDir) != "" {
		return filepath.Join(homeDir, ".config", "camunda", "c8run", markerFileName)
	}

	return filepath.Join(baseDir, markerFileName)
}

func OperateURL(settings types.C8RunSettings) string {
	return fmt.Sprintf("%s://localhost:%s/operate", settings.GetProtocol(), strconv.Itoa(settings.Port))
}

func Default(settings types.C8RunSettings, camundaVersion string) string {
	if shouldUseDocsStartup(camundaVersion, settings.StartupMarkerPath) {
		return DocsURL
	}
	return OperateURL(settings)
}

func MarkSeen(markerPath string) error {
	markerPath = strings.TrimSpace(markerPath)
	if markerPath == "" || hasSeen(markerPath) {
		return nil
	}

	if err := os.MkdirAll(filepath.Dir(markerPath), 0o755); err != nil {
		return fmt.Errorf("failed to create quickstart marker directory: %w", err)
	}

	if err := os.WriteFile(markerPath, []byte(markerFileValue), 0o644); err != nil {
		return fmt.Errorf("failed to write quickstart marker: %w", err)
	}

	return nil
}

func shouldUseDocsStartup(camundaVersion string, markerPath string) bool {
	major, minor, ok := parseMajorMinor(camundaVersion)
	if !ok {
		return false
	}
	if hasSeen(markerPath) {
		return false
	}
	if major > 8 {
		return true
	}
	return major == 8 && minor >= 9
}

func hasSeen(markerPath string) bool {
	markerPath = strings.TrimSpace(markerPath)
	if markerPath == "" {
		return false
	}

	_, err := os.Stat(markerPath)
	return err == nil
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
