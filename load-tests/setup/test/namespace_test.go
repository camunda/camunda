package golden

import (
	"bytes"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/stretchr/testify/require"
)

func runParseNamespace(t *testing.T, input string) (string, string, error) {
	t.Helper()
	_, currentFile, _, ok := runtime.Caller(0)
	require.True(t, ok)
	utilsPath := filepath.Join(filepath.Dir(currentFile), "..", "utils.sh")

	cmd := exec.Command("bash", "-c", `source "$1"; parse_namespace "$2"`, "bash", utilsPath, input)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	stdout, err := cmd.Output()
	return strings.TrimSpace(string(stdout)), stderr.String(), err
}

func TestParseNamespaceLowercasesAndAddsPrefix(t *testing.T) {
	got, stderr, err := runParseNamespace(t, "INC-5337")
	require.NoError(t, err, stderr)
	require.Equal(t, "c8-inc-5337", got)
}

func TestParseNamespaceLowercasesExistingPrefix(t *testing.T) {
	got, stderr, err := runParseNamespace(t, "C8-foo")
	require.NoError(t, err, stderr)
	require.Equal(t, "c8-foo", got)
}

func TestParseNamespaceRejectsInvalidCharacters(t *testing.T) {
	_, stderr, err := runParseNamespace(t, "MY_test")
	require.Error(t, err)
	require.Contains(t, stderr, "Allowed: lowercase letters, digits, '-'")
	require.Contains(t, stderr, "c8-my_test")
}
