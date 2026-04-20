package packages

import (
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/camunda/camunda/c8run/internal/jre"
)

func TestParseJavaMajorVersion(t *testing.T) {
	tests := []struct {
		name            string
		version         string
		expectedVersion int
	}{
		{
			name:            "modern version",
			version:         "21.0.8",
			expectedVersion: 21,
		},
		{
			name:            "quoted modern version",
			version:         `"22.0.2" 2024-07-16`,
			expectedVersion: 22,
		},
		{
			name:            "legacy version",
			version:         "1.8.0_412",
			expectedVersion: 8,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// when
			actualVersion, err := parseJavaMajorVersion(tt.version)

			// then
			if err != nil {
				t.Fatalf("parseJavaMajorVersion returned error: %v", err)
			}
			if actualVersion != tt.expectedVersion {
				t.Fatalf("expected Java major version %d, got %d", tt.expectedVersion, actualVersion)
			}
		})
	}
}

func TestParseJDepsModuleOutputSortsAndDeduplicatesModules(t *testing.T) {
	// given
	output := "java.sql,java.base\njdk.unsupported,java.base\n"

	// when
	modules := parseJDepsModuleOutput(output)

	// then
	expectedModules := []string{"java.base", "java.sql", "jdk.unsupported"}
	if !reflect.DeepEqual(modules, expectedModules) {
		t.Fatalf("expected modules %v, got %v", expectedModules, modules)
	}
}

func TestBuildJLinkArgsUsesConservativeCompression(t *testing.T) {
	// given
	modules := []string{"java.base", "jdk.unsupported"}

	// when
	args := buildJLinkArgs(modules, jre.DirectoryName)

	// then
	argsLine := strings.Join(args, " ")
	if !strings.Contains(argsLine, "--add-modules java.base,jdk.unsupported") {
		t.Fatalf("expected add-modules argument, got %v", args)
	}
	if !strings.Contains(argsLine, "--compress zip-6") {
		t.Fatalf("expected zip-6 compression, got %v", args)
	}
	if !strings.Contains(argsLine, "--strip-debug") {
		t.Fatalf("expected strip-debug option, got %v", args)
	}
	if !strings.Contains(argsLine, "--no-header-files") {
		t.Fatalf("expected no-header-files option, got %v", args)
	}
	if !strings.Contains(argsLine, "--no-man-pages") {
		t.Fatalf("expected no-man-pages option, got %v", args)
	}
}

func TestGetFilesToArchiveIncludesBundledJRE(t *testing.T) {
	// when
	files := getFilesToArchive("linux", "connector-runtime-bundle-test-with-dependencies.jar", "8.10.0-alpha1")

	// then
	expectedJREPath := filepath.Join("c8run", jre.DirectoryName)
	for _, file := range files {
		if file == expectedJREPath {
			return
		}
	}
	t.Fatalf("expected files to archive to include %s, got %v", expectedJREPath, files)
}

func TestMergeModulesAddsConservativeJREModules(t *testing.T) {
	// when
	modules := mergeModules([]string{"java.base"}, conservativeJREModules)

	// then
	for _, expectedModule := range []string{"java.base", "jdk.charsets", "jdk.crypto.ec", "jdk.localedata", "jdk.zipfs"} {
		found := false
		for _, module := range modules {
			if module == expectedModule {
				found = true
				break
			}
		}
		if !found {
			t.Fatalf("expected module %s in %v", expectedModule, modules)
		}
	}
}

func TestMaterializeSymlinksReplacesSymlinkWithRegularFile(t *testing.T) {
	// given
	root := t.TempDir()
	targetPath := filepath.Join(root, "target.txt")
	linkPath := filepath.Join(root, "link.txt")
	if err := os.WriteFile(targetPath, []byte("jre legal text"), 0o644); err != nil {
		t.Fatalf("failed to write target file: %v", err)
	}
	if err := os.Symlink(targetPath, linkPath); err != nil {
		t.Skipf("symlinks are not supported in this environment: %v", err)
	}

	// when
	err := materializeSymlinks(root)

	// then
	if err != nil {
		t.Fatalf("materializeSymlinks returned error: %v", err)
	}
	info, err := os.Lstat(linkPath)
	if err != nil {
		t.Fatalf("failed to stat materialized file: %v", err)
	}
	if info.Mode()&os.ModeSymlink != 0 {
		t.Fatalf("expected %s to be a regular file, got mode %s", linkPath, info.Mode())
	}
	content, err := os.ReadFile(linkPath)
	if err != nil {
		t.Fatalf("failed to read materialized file: %v", err)
	}
	if string(content) != "jre legal text" {
		t.Fatalf("expected materialized content %q, got %q", "jre legal text", string(content))
	}
}
