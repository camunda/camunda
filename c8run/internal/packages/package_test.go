package packages

import (
	"archive/zip"
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

func TestBuildJavaHelperArgsUsesJava21Release(t *testing.T) {
	// when
	args := buildJavaHelperArgs("JavaVersion.java")

	// then
	expectedArgs := []string{"--release", "21", "JavaVersion.java"}
	if !reflect.DeepEqual(args, expectedArgs) {
		t.Fatalf("expected javac args %v, got %v", expectedArgs, args)
	}
}

func TestCleanPreservesRequestedConnectorJar(t *testing.T) {
	// given
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}
	root := t.TempDir()
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to change working directory: %v", err)
	}
	defer func() {
		if err := os.Chdir(cwd); err != nil {
			t.Fatalf("failed to restore working directory: %v", err)
		}
	}()

	connectorJarToKeep := "connector-runtime-bundle-8.8.0-alpha5-with-dependencies.jar"
	staleConnectorJar := "connector-runtime-bundle-8.7.0-with-dependencies.jar"
	if err := os.WriteFile(connectorJarToKeep, []byte("current"), 0o644); err != nil {
		t.Fatalf("failed to write current connector jar: %v", err)
	}
	if err := os.WriteFile(staleConnectorJar, []byte("stale"), 0o644); err != nil {
		t.Fatalf("failed to write stale connector jar: %v", err)
	}

	// when
	Clean("8.8.0-alpha5", connectorJarToKeep)

	// then
	if _, err := os.Stat(connectorJarToKeep); err != nil {
		t.Fatalf("expected current connector jar to be preserved: %v", err)
	}
	if _, err := os.Stat(staleConnectorJar); !os.IsNotExist(err) {
		t.Fatalf("expected stale connector jar to be removed, stat error: %v", err)
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

func TestMergeModulesAddsRuntimeJREModules(t *testing.T) {
	// when
	modules := mergeModules([]string{"java.base"}, conservativeJREModules, runtimeProviderJREModules)

	// then
	for _, expectedModule := range []string{
		"java.base",
		"java.management.rmi",
		"java.xml.crypto",
		"jdk.charsets",
		"jdk.crypto.ec",
		"jdk.localedata",
		"jdk.management.agent",
		"jdk.naming.dns",
		"jdk.zipfs",
	} {
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

func TestRocksdbNativeLibName(t *testing.T) {
	tests := []struct {
		osType   string
		arch     string
		expected string
	}{
		{"linux", "x86_64", "librocksdbjni-linux64.so"},
		{"linux", "aarch64", "librocksdbjni-linux-aarch64.so"},
		{"darwin", "x86_64", "librocksdbjni-osx-x86_64.jnilib"},
		{"darwin", "aarch64", "librocksdbjni-osx-arm64.jnilib"},
		{"windows", "x86_64", "librocksdbjni-win64.dll"},
	}
	for _, tt := range tests {
		t.Run(tt.osType+"/"+tt.arch, func(t *testing.T) {
			got, err := rocksdbNativeLibName(tt.osType, tt.arch)
			if err != nil {
				t.Fatalf("rocksdbNativeLibName(%q, %q) error: %v", tt.osType, tt.arch, err)
			}
			if got != tt.expected {
				t.Fatalf("rocksdbNativeLibName(%q, %q) = %q, want %q", tt.osType, tt.arch, got, tt.expected)
			}
		})
	}
}

func TestRocksdbNativeLibNameUnknownPlatformErrors(t *testing.T) {
	tests := []struct {
		osType string
		arch   string
	}{
		{"freebsd", "x86_64"},
		{"windows", "arm64"},
		{"linux", "i386"},
		{"darwin", "i386"},
	}
	for _, tt := range tests {
		t.Run(tt.osType+"/"+tt.arch, func(t *testing.T) {
			_, err := rocksdbNativeLibName(tt.osType, tt.arch)
			if err == nil {
				t.Fatalf("expected error for unsupported os/arch %s/%s, got nil", tt.osType, tt.arch)
			}
		})
	}
}

func TestRewriteJarDroppingEntriesStripsOtherPlatforms(t *testing.T) {
	// given: fake JAR with all 5 native libs plus non-native entries
	root := t.TempDir()
	jarPath := filepath.Join(root, "rocksdbjni-9.0.0.jar")

	f, err := os.Create(jarPath)
	if err != nil {
		t.Fatalf("failed to create temp jar: %v", err)
	}
	w := zip.NewWriter(f)
	entries := []struct{ name, content string }{
		{"META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"},
		{"librocksdbjni-linux64.so", "linux64-binary"},
		{"librocksdbjni-linux-aarch64.so", "linux-aarch64-binary"},
		{"librocksdbjni-osx-arm64.jnilib", "osx-arm64-binary"},
		{"librocksdbjni-osx-x86_64.jnilib", "osx-x86_64-binary"},
		{"librocksdbjni-win64.dll", "win64-binary"},
		{"org/rocksdb/RocksDB.class", "class-bytes"},
	}
	for _, e := range entries {
		fw, err := w.Create(e.name)
		if err != nil {
			t.Fatalf("failed to create zip entry %s: %v", e.name, err)
		}
		if _, err := fw.Write([]byte(e.content)); err != nil {
			t.Fatalf("failed to write zip entry %s: %v", e.name, err)
		}
	}
	if err := w.Close(); err != nil {
		t.Fatalf("failed to close zip writer: %v", err)
	}
	if err := f.Close(); err != nil {
		t.Fatalf("failed to close jar file: %v", err)
	}

	// when
	keepLib := "librocksdbjni-linux64.so"
	shouldDrop := func(name string) bool {
		return isRocksdbNativeLib(name) && name != keepLib
	}
	dropped, err := rewriteJarDroppingEntries(jarPath, shouldDrop)

	// then
	if err != nil {
		t.Fatalf("rewriteJarDroppingEntries returned error: %v", err)
	}
	if dropped != 4 {
		t.Fatalf("expected 4 entries dropped, got %d", dropped)
	}

	r, err := zip.OpenReader(jarPath)
	if err != nil {
		t.Fatalf("failed to open rewritten jar: %v", err)
	}
	defer func() {
		if err := r.Close(); err != nil {
			t.Errorf("failed to close rewritten jar: %v", err)
		}
	}()

	var keptNative []string
	allKept := make(map[string]bool)
	for _, entry := range r.File {
		allKept[entry.Name] = true
		if isRocksdbNativeLib(entry.Name) {
			keptNative = append(keptNative, entry.Name)
		}
	}

	if len(keptNative) != 1 || keptNative[0] != "librocksdbjni-linux64.so" {
		t.Fatalf("expected only linux64 native lib, got %v", keptNative)
	}
	for _, mustKeep := range []string{"META-INF/MANIFEST.MF", "org/rocksdb/RocksDB.class"} {
		if !allKept[mustKeep] {
			t.Fatalf("expected non-native entry %q to be preserved, got entries: %v", mustKeep, allKept)
		}
	}
}

func TestRewriteJarDroppingEntriesDropsNothingWhenPredicateNeverMatches(t *testing.T) {
	// given: JAR with entries that the predicate does not match
	root := t.TempDir()
	jarPath := filepath.Join(root, "rocksdbjni-9.0.0.jar")

	f, err := os.Create(jarPath)
	if err != nil {
		t.Fatalf("failed to create temp jar: %v", err)
	}
	w := zip.NewWriter(f)
	fw, err := w.Create("org/rocksdb/RocksDB.class")
	if err != nil {
		t.Fatalf("failed to create zip entry: %v", err)
	}
	if _, err := fw.Write([]byte("class-bytes")); err != nil {
		t.Fatalf("failed to write zip entry: %v", err)
	}
	if err := w.Close(); err != nil {
		t.Fatalf("failed to close zip writer: %v", err)
	}
	if err := f.Close(); err != nil {
		t.Fatalf("failed to close jar file: %v", err)
	}

	// when: predicate never matches (nothing to drop)
	shouldDrop := func(name string) bool {
		return isRocksdbNativeLib(name) && name != "librocksdbjni-linux64.so"
	}
	dropped, err := rewriteJarDroppingEntries(jarPath, shouldDrop)

	// then
	if err != nil {
		t.Fatalf("expected no error, got: %v", err)
	}
	if dropped != 0 {
		t.Fatalf("expected 0 entries dropped, got %d", dropped)
	}
}

func TestStripRocksDbNativeLibsStripsJar(t *testing.T) {
	// given
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}
	root := t.TempDir()
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to chdir to temp dir: %v", err)
	}
	defer func() {
		if err := os.Chdir(cwd); err != nil {
			t.Fatalf("failed to restore working directory: %v", err)
		}
	}()

	version := "8.10.0-test"
	libDir := filepath.Join("camunda-zeebe-"+version, "lib")
	if err := os.MkdirAll(libDir, 0o755); err != nil {
		t.Fatalf("failed to create lib dir: %v", err)
	}
	jarPath := filepath.Join(libDir, "rocksdbjni-9.0.0.jar")
	f, err := os.Create(jarPath)
	if err != nil {
		t.Fatalf("failed to create test jar: %v", err)
	}
	w := zip.NewWriter(f)
	for _, name := range []string{
		"librocksdbjni-linux64.so",
		"librocksdbjni-linux-aarch64.so",
		"librocksdbjni-osx-arm64.jnilib",
		"librocksdbjni-osx-x86_64.jnilib",
		"librocksdbjni-win64.dll",
	} {
		fw, err := w.Create(name)
		if err != nil {
			t.Fatalf("failed to create zip entry %s: %v", name, err)
		}
		if _, err := fw.Write([]byte("binary")); err != nil {
			t.Fatalf("failed to write zip entry %s: %v", name, err)
		}
	}
	if err := w.Close(); err != nil {
		t.Fatalf("failed to close zip writer: %v", err)
	}
	if err := f.Close(); err != nil {
		t.Fatalf("failed to close jar file: %v", err)
	}

	// when
	err = stripRocksDbNativeLibs(version, "linux", "x86_64")

	// then
	if err != nil {
		t.Fatalf("stripRocksDbNativeLibs returned error: %v", err)
	}

	r, err := zip.OpenReader(jarPath)
	if err != nil {
		t.Fatalf("failed to open stripped jar: %v", err)
	}
	defer func() {
		if err := r.Close(); err != nil {
			t.Errorf("failed to close stripped jar: %v", err)
		}
	}()

	var nativeLibs []string
	for _, entry := range r.File {
		if isRocksdbNativeLib(entry.Name) {
			nativeLibs = append(nativeLibs, entry.Name)
		}
	}
	if len(nativeLibs) != 1 || nativeLibs[0] != "librocksdbjni-linux64.so" {
		t.Fatalf("expected only linux64 native lib after strip, got %v", nativeLibs)
	}
}

func TestStripRocksDbNativeLibsErrorsWhenJarMissing(t *testing.T) {
	// given: empty temp dir — no camunda-zeebe dir, no jar
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}
	root := t.TempDir()
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to chdir to temp dir: %v", err)
	}
	defer func() {
		if err := os.Chdir(cwd); err != nil {
			t.Fatalf("failed to restore working directory: %v", err)
		}
	}()

	// when
	err = stripRocksDbNativeLibs("8.10.0-test", "linux", "x86_64")

	// then
	if err == nil {
		t.Fatal("expected error when rocksdbjni jar not found, got nil")
	}
}

func TestStripRocksDbNativeLibsErrorsWhenExpectedLibMissing(t *testing.T) {
	// given: JAR contains only the non-target platform libs (libName absent)
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}
	root := t.TempDir()
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to chdir to temp dir: %v", err)
	}
	defer func() {
		if err := os.Chdir(cwd); err != nil {
			t.Fatalf("failed to restore working directory: %v", err)
		}
	}()

	version := "8.10.0-test"
	libDir := filepath.Join("camunda-zeebe-"+version, "lib")
	if err := os.MkdirAll(libDir, 0o755); err != nil {
		t.Fatalf("failed to create lib dir: %v", err)
	}
	jarPath := filepath.Join(libDir, "rocksdbjni-9.0.0.jar")
	f, err := os.Create(jarPath)
	if err != nil {
		t.Fatalf("failed to create test jar: %v", err)
	}
	w := zip.NewWriter(f)
	// Only non-linux64 entries — the expected lib (librocksdbjni-linux64.so) is absent.
	for _, name := range []string{
		"librocksdbjni-linux-aarch64.so",
		"librocksdbjni-osx-arm64.jnilib",
	} {
		fw, err := w.Create(name)
		if err != nil {
			t.Fatalf("failed to create zip entry %s: %v", name, err)
		}
		if _, err := fw.Write([]byte("binary")); err != nil {
			t.Fatalf("failed to write zip entry %s: %v", name, err)
		}
	}
	if err := w.Close(); err != nil {
		t.Fatalf("failed to close zip writer: %v", err)
	}
	if err := f.Close(); err != nil {
		t.Fatalf("failed to close jar file: %v", err)
	}

	// when
	err = stripRocksDbNativeLibs(version, "linux", "x86_64")

	// then: should error because the expected lib is not present
	if err == nil {
		t.Fatal("expected error when libName not found in JAR, got nil")
	}
}

func TestStripRocksDbNativeLibsIsIdempotent(t *testing.T) {
	// given: JAR already stripped — only the target lib remains
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}
	root := t.TempDir()
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to chdir to temp dir: %v", err)
	}
	defer func() {
		if err := os.Chdir(cwd); err != nil {
			t.Fatalf("failed to restore working directory: %v", err)
		}
	}()

	version := "8.10.0-test"
	libDir := filepath.Join("camunda-zeebe-"+version, "lib")
	if err := os.MkdirAll(libDir, 0o755); err != nil {
		t.Fatalf("failed to create lib dir: %v", err)
	}
	jarPath := filepath.Join(libDir, "rocksdbjni-9.0.0.jar")
	f, err := os.Create(jarPath)
	if err != nil {
		t.Fatalf("failed to create test jar: %v", err)
	}
	w := zip.NewWriter(f)
	// Only the kept lib — simulates a JAR that was already stripped.
	fw, err := w.Create("librocksdbjni-linux64.so")
	if err != nil {
		t.Fatalf("failed to create zip entry: %v", err)
	}
	if _, err := fw.Write([]byte("binary")); err != nil {
		t.Fatalf("failed to write zip entry: %v", err)
	}
	if err := w.Close(); err != nil {
		t.Fatalf("failed to close zip writer: %v", err)
	}
	if err := f.Close(); err != nil {
		t.Fatalf("failed to close jar file: %v", err)
	}

	// when: first call
	if err := stripRocksDbNativeLibs(version, "linux", "x86_64"); err != nil {
		t.Fatalf("first call failed: %v", err)
	}
	// when: second call on already-stripped JAR
	if err := stripRocksDbNativeLibs(version, "linux", "x86_64"); err != nil {
		t.Fatalf("second call (idempotency) failed: %v", err)
	}
}

func TestZstdNativePrefix(t *testing.T) {
	tests := []struct {
		osType   string
		arch     string
		expected string
	}{
		{"linux", "x86_64", "linux/amd64/"},
		{"linux", "aarch64", "linux/aarch64/"},
		{"darwin", "x86_64", "darwin/x86_64/"},
		{"darwin", "aarch64", "darwin/aarch64/"},
		{"windows", "x86_64", "win/amd64/"},
	}
	for _, tt := range tests {
		t.Run(tt.osType+"/"+tt.arch, func(t *testing.T) {
			got, err := zstdNativePrefix(tt.osType, tt.arch)
			if err != nil {
				t.Fatalf("zstdNativePrefix(%q, %q) error: %v", tt.osType, tt.arch, err)
			}
			if got != tt.expected {
				t.Fatalf("zstdNativePrefix(%q, %q) = %q, want %q", tt.osType, tt.arch, got, tt.expected)
			}
		})
	}
}

func TestZstdNativePrefixUnknownPlatformErrors(t *testing.T) {
	tests := []struct {
		osType string
		arch   string
	}{
		{"freebsd", "x86_64"},
		{"windows", "aarch64"},
		{"linux", "i386"},
	}
	for _, tt := range tests {
		t.Run(tt.osType+"/"+tt.arch, func(t *testing.T) {
			_, err := zstdNativePrefix(tt.osType, tt.arch)
			if err == nil {
				t.Fatalf("expected error for unsupported os/arch %s/%s, got nil", tt.osType, tt.arch)
			}
		})
	}
}

func TestIsZstdNativeEntry(t *testing.T) {
	tests := []struct {
		name     string
		expected bool
	}{
		{"linux/amd64/libzstd-jni-1.5.7-9.so", true},
		{"darwin/aarch64/libzstd-jni-1.5.7-9.dylib", true},
		{"win/amd64/libzstd-jni-1.5.7-9.dll", true},
		{"aix/ppc64/libzstd-jni-1.5.7-9.so", true},
		{"freebsd/amd64/libzstd-jni-1.5.7-9.so", true},
		{"META-INF/MANIFEST.MF", false},
		{"com/github/luben/zstd/Zstd.class", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := isZstdNativeEntry(tt.name)
			if got != tt.expected {
				t.Fatalf("isZstdNativeEntry(%q) = %v, want %v", tt.name, got, tt.expected)
			}
		})
	}
}

func TestStripZstdJniNativeLibsStripsJar(t *testing.T) {
	// given
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}
	root := t.TempDir()
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to chdir to temp dir: %v", err)
	}
	defer func() {
		if err := os.Chdir(cwd); err != nil {
			t.Fatalf("failed to restore working directory: %v", err)
		}
	}()

	version := "8.10.0-test"
	libDir := filepath.Join("camunda-zeebe-"+version, "lib")
	if err := os.MkdirAll(libDir, 0o755); err != nil {
		t.Fatalf("failed to create lib dir: %v", err)
	}
	jarPath := filepath.Join(libDir, "zstd-jni-1.5.7-9.jar")
	f, err := os.Create(jarPath)
	if err != nil {
		t.Fatalf("failed to create test jar: %v", err)
	}
	w := zip.NewWriter(f)
	entries := []string{
		"META-INF/MANIFEST.MF",
		"com/github/luben/zstd/Zstd.class",
		"linux/amd64/libzstd-jni-1.5.7-9.so",
		"linux/aarch64/libzstd-jni-1.5.7-9.so",
		"darwin/x86_64/libzstd-jni-1.5.7-9.dylib",
		"darwin/aarch64/libzstd-jni-1.5.7-9.dylib",
		"win/amd64/libzstd-jni-1.5.7-9.dll",
		"aix/ppc64/libzstd-jni-1.5.7-9.so",
		"freebsd/amd64/libzstd-jni-1.5.7-9.so",
	}
	for _, name := range entries {
		fw, err := w.Create(name)
		if err != nil {
			t.Fatalf("failed to create zip entry %s: %v", name, err)
		}
		if _, err := fw.Write([]byte("binary-" + name)); err != nil {
			t.Fatalf("failed to write zip entry %s: %v", name, err)
		}
	}
	if err := w.Close(); err != nil {
		t.Fatalf("failed to close zip writer: %v", err)
	}
	if err := f.Close(); err != nil {
		t.Fatalf("failed to close jar file: %v", err)
	}

	// when: strip for linux/x86_64 (keeps linux/amd64/)
	err = stripZstdJniNativeLibs(version, "linux", "x86_64")

	// then
	if err != nil {
		t.Fatalf("stripZstdJniNativeLibs returned error: %v", err)
	}

	r, err := zip.OpenReader(jarPath)
	if err != nil {
		t.Fatalf("failed to open stripped jar: %v", err)
	}
	defer func() {
		if err := r.Close(); err != nil {
			t.Errorf("failed to close stripped jar: %v", err)
		}
	}()

	kept := make(map[string]bool)
	for _, entry := range r.File {
		kept[entry.Name] = true
	}

	// Should keep: non-native entries + linux/amd64/
	for _, mustKeep := range []string{
		"META-INF/MANIFEST.MF",
		"com/github/luben/zstd/Zstd.class",
		"linux/amd64/libzstd-jni-1.5.7-9.so",
	} {
		if !kept[mustKeep] {
			t.Fatalf("expected entry %q to be preserved, got entries: %v", mustKeep, kept)
		}
	}

	// Should drop: all other native entries
	for _, mustDrop := range []string{
		"linux/aarch64/libzstd-jni-1.5.7-9.so",
		"darwin/x86_64/libzstd-jni-1.5.7-9.dylib",
		"darwin/aarch64/libzstd-jni-1.5.7-9.dylib",
		"win/amd64/libzstd-jni-1.5.7-9.dll",
		"aix/ppc64/libzstd-jni-1.5.7-9.so",
		"freebsd/amd64/libzstd-jni-1.5.7-9.so",
	} {
		if kept[mustDrop] {
			t.Fatalf("expected entry %q to be dropped, but it was kept", mustDrop)
		}
	}
}

func TestStripZstdJniNativeLibsIsIdempotent(t *testing.T) {
	// given: JAR already stripped — only the target platform remains
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}
	root := t.TempDir()
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to chdir to temp dir: %v", err)
	}
	defer func() {
		if err := os.Chdir(cwd); err != nil {
			t.Fatalf("failed to restore working directory: %v", err)
		}
	}()

	version := "8.10.0-test"
	libDir := filepath.Join("camunda-zeebe-"+version, "lib")
	if err := os.MkdirAll(libDir, 0o755); err != nil {
		t.Fatalf("failed to create lib dir: %v", err)
	}
	jarPath := filepath.Join(libDir, "zstd-jni-1.5.7-9.jar")
	f, err := os.Create(jarPath)
	if err != nil {
		t.Fatalf("failed to create test jar: %v", err)
	}
	w := zip.NewWriter(f)
	fw, err := w.Create("darwin/aarch64/libzstd-jni-1.5.7-9.dylib")
	if err != nil {
		t.Fatalf("failed to create zip entry: %v", err)
	}
	if _, err := fw.Write([]byte("binary")); err != nil {
		t.Fatalf("failed to write zip entry: %v", err)
	}
	if err := w.Close(); err != nil {
		t.Fatalf("failed to close zip writer: %v", err)
	}
	if err := f.Close(); err != nil {
		t.Fatalf("failed to close jar file: %v", err)
	}

	// when: first call
	if err := stripZstdJniNativeLibs(version, "darwin", "aarch64"); err != nil {
		t.Fatalf("first call failed: %v", err)
	}
	// when: second call on already-stripped JAR
	if err := stripZstdJniNativeLibs(version, "darwin", "aarch64"); err != nil {
		t.Fatalf("second call (idempotency) failed: %v", err)
	}
}

func TestStripZstdJniNativeLibsSkipsWhenJarMissing(t *testing.T) {
	// given: empty temp dir — no zstd-jni jar
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}
	root := t.TempDir()
	if err := os.Chdir(root); err != nil {
		t.Fatalf("failed to chdir to temp dir: %v", err)
	}
	defer func() {
		if err := os.Chdir(cwd); err != nil {
			t.Fatalf("failed to restore working directory: %v", err)
		}
	}()

	// when: no jar exists — should warn and return nil (not error)
	err = stripZstdJniNativeLibs("8.10.0-test", "linux", "x86_64")

	// then
	if err != nil {
		t.Fatalf("expected nil error when jar is missing (graceful skip), got: %v", err)
	}
}

func TestVerifyClassFileVersionAcceptsJava21Class(t *testing.T) {
	// given — minimal class file header with major version matching helperJavaRelease
	dir := t.TempDir()
	classFile := filepath.Join(dir, "JavaVersion.class")
	// Java class file: magic (0xCAFEBABE), minor version (0x0000), major version big-endian
	// Major version = 44 + Java version; derive from helperJavaRelease so this stays correct if the target changes.
	expectedMajor := 44 + helperJavaRelease
	data := []byte{0xCA, 0xFE, 0xBA, 0xBE, 0x00, 0x00, byte(expectedMajor >> 8), byte(expectedMajor)}
	if err := os.WriteFile(classFile, data, 0o644); err != nil {
		t.Fatalf("failed to write class file: %v", err)
	}

	// when
	err := verifyClassFileVersion(classFile)

	// then
	if err != nil {
		t.Fatalf("expected no error for Java %d class file, got: %v", helperJavaRelease, err)
	}
}

func TestVerifyClassFileVersionRejectsWrongVersion(t *testing.T) {
	// given — class file compiled for a Java version above helperJavaRelease
	dir := t.TempDir()
	classFile := filepath.Join(dir, "JavaVersion.class")
	wrongMajor := 44 + helperJavaRelease + 4
	data := []byte{0xCA, 0xFE, 0xBA, 0xBE, 0x00, 0x00, byte(wrongMajor >> 8), byte(wrongMajor)}
	if err := os.WriteFile(classFile, data, 0o644); err != nil {
		t.Fatalf("failed to write class file: %v", err)
	}

	// when
	err := verifyClassFileVersion(classFile)

	// then
	if err == nil {
		t.Fatalf("expected error for Java %d class file, got nil", helperJavaRelease+4)
	}
	if !strings.Contains(err.Error(), "expected Java 21") {
		t.Fatalf("expected error to mention 'expected Java 21', got: %v", err)
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

func TestMaterializeSymlinksReplacesSymlinkWithDirectory(t *testing.T) {
	// given
	root := t.TempDir()
	targetPath := filepath.Join(root, "java.base")
	linkPath := filepath.Join(root, "legal")
	licensePath := filepath.Join(targetPath, "LICENSE")
	if err := os.Mkdir(targetPath, 0o755); err != nil {
		t.Fatalf("failed to create target directory: %v", err)
	}
	if err := os.WriteFile(licensePath, []byte("jre legal text"), 0o644); err != nil {
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
		t.Fatalf("failed to stat materialized directory: %v", err)
	}
	if info.Mode()&os.ModeSymlink != 0 {
		t.Fatalf("expected %s to be a directory, got mode %s", linkPath, info.Mode())
	}
	if !info.IsDir() {
		t.Fatalf("expected %s to be a directory, got mode %s", linkPath, info.Mode())
	}
	content, err := os.ReadFile(filepath.Join(linkPath, "LICENSE"))
	if err != nil {
		t.Fatalf("failed to read materialized file: %v", err)
	}
	if string(content) != "jre legal text" {
		t.Fatalf("expected materialized content %q, got %q", "jre legal text", string(content))
	}
}
