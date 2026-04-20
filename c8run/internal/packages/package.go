/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package packages

import (
	"encoding/base64"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"sort"
	"strconv"
	"strings"

	"github.com/camunda/camunda/c8run/internal/archive"
	"github.com/camunda/camunda/c8run/internal/jre"
	"github.com/rs/zerolog/log"
)

const requiredJavaMajorVersion = 21

var conservativeJREModules = []string{
	// jdeps cannot see provider and locale modules loaded indirectly at runtime.
	"jdk.charsets",
	"jdk.crypto.ec",
	"jdk.localedata",
	"jdk.zipfs",
}

func Clean(camundaVersion string) {
	// Older C8Run builds extracted Elasticsearch locally. Remove any leftovers so they cannot be
	// picked up by later packaging runs after Elasticsearch was removed from the distribution.
	legacyElasticsearchArtifacts, err := filepath.Glob("elasticsearch-*")
	if err != nil {
		log.Error().Err(err).Msg("failed to list legacy elasticsearch artifacts")
	} else {
		for _, artifact := range legacyElasticsearchArtifacts {
			if err := os.RemoveAll(artifact); err != nil {
				log.Error().Err(err).Str("path", artifact).Msg("failed to remove legacy elasticsearch artifact")
			}
		}
	}
	for _, path := range []string{"elasticsearch.process", "elasticsearch.process.lock"} {
		if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
			log.Error().Err(err).Str("path", path).Msg("failed to remove legacy elasticsearch process file")
		}
	}
	legacyComposeArtifacts, err := filepath.Glob("docker-compose-*")
	if err != nil {
		log.Error().Err(err).Msg("failed to list legacy docker compose artifacts")
	} else {
		for _, artifact := range legacyComposeArtifacts {
			if err := os.RemoveAll(artifact); err != nil {
				log.Error().Err(err).Str("path", artifact).Msg("failed to remove legacy docker compose artifact")
			}
		}
	}
	if err := os.RemoveAll("camunda-zeebe-" + camundaVersion); err != nil {
		log.Error().Err(err).Msg("failed to remove camunda")
	}
	if err := os.RemoveAll("camunda-db-rdbms-schema" + camundaVersion); err != nil {
		log.Error().Err(err).Msg("failed to remove camunda-db-r-dbms-schema")
	}
	if err := os.RemoveAll("rdbms-schema"); err != nil {
		log.Error().Err(err).Msg("failed to remove rdbms-schema")
	}
	if err := os.RemoveAll(jre.DirectoryName); err != nil {
		log.Error().Err(err).Msg("failed to remove bundled JRE")
	}

	logFiles := []string{"camunda.log", "connectors.log", "elasticsearch.log"}
	for _, logFile := range logFiles {
		_, err := os.Stat(filepath.Join("log", logFile))
		if !errors.Is(err, os.ErrNotExist) {
			if err := os.Remove(filepath.Join("log", logFile)); err != nil {
				log.Error().Err(err).Msg("failed to remove log file")
			}
		}
	}

	connectorJars, err := filepath.Glob("connector-runtime-bundle-*-with-dependencies.jar")
	if err != nil {
		log.Error().Err(err).Msg("failed to discover connector jars")
	} else {
		for _, jar := range connectorJars {
			if err := os.Remove(jar); err != nil && !errors.Is(err, os.ErrNotExist) {
				log.Error().Err(err).Str("file", jar).Msg("failed to remove connector jar")
			}
		}
	}
}

func downloadAndExtract(filePath, url, extractDir string, baseDir string, authToken string, extractFunc func(string, string) error) error {
	err := archive.DownloadFile(filePath, url, authToken)
	if err != nil {
		return fmt.Errorf("downloadAndExtract: failed to download file at url %s\n%w\n%s", url, err, debug.Stack())
	}

	_, err = os.Stat(extractDir)
	if errors.Is(err, os.ErrNotExist) {
		err = extractFunc(filePath, baseDir)
		if err != nil {
			return fmt.Errorf("downloadAndExtract: failed to extract from archive at %s\n%w\n%s", filePath, err, debug.Stack())
		}
	}
	return nil
}

func setOsSpecificValues() (string, string, string, string, func(string, string) error, error) {
	var architecture string
	osType := runtime.GOOS
	var pkgName string
	var finalOutputExtension string
	var extractFunc func(string, string) error

	switch osType {
	case "windows":
		architecture = "x86_64"
		pkgName = ".zip"
		finalOutputExtension = ".zip"
		extractFunc = archive.UnzipSource
		return osType, architecture, pkgName, finalOutputExtension, extractFunc, nil
	case "linux", "darwin":
		pkgName = ".tar.gz"
		if osType == "linux" {
			finalOutputExtension = ".tar.gz"
		} else {
			finalOutputExtension = ".zip"
		}
		extractFunc = archive.ExtractTarGzArchive
		switch runtime.GOARCH {
		case "amd64":
			architecture = "x86_64"
		case "arm64":
			architecture = "aarch64"
		default:
			return "", "", "", "", nil, fmt.Errorf("unsupported architecture: %s", runtime.GOARCH)
		}
		return osType, architecture, pkgName, finalOutputExtension, extractFunc, nil
	default:
		return "", "", "", "", nil, fmt.Errorf("unsupported operating system: %s", osType)
	}
}

func getJavaArtifactsToken() (string, error) {
	javaArtifactsUser := os.Getenv("JAVA_ARTIFACTS_USER")
	javaArtifactsPassword := os.Getenv("JAVA_ARTIFACTS_PASSWORD")

	if javaArtifactsUser == "" || javaArtifactsPassword == "" {
		return "", fmt.Errorf("JAVA_ARTIFACTS_USER or JAVA_ARTIFACTS_PASSWORD environment variables are not set")
	}

	token := base64.StdEncoding.EncodeToString([]byte(javaArtifactsUser + ":" + javaArtifactsPassword))
	return "Basic " + token, nil
}

func getFilesToArchive(osType, connectorsFilePath, camundaVersion string) []string {
	commonFiles := []string{
		filepath.Join("c8run", "README.md"),
		filepath.Join("c8run", "connectors-application.properties"),
		filepath.Join("c8run", connectorsFilePath),
		filepath.Join("c8run", "custom_connectors"),
		filepath.Join("c8run", "endpoints.txt"),
		filepath.Join("c8run", "JavaVersion.class"),
		filepath.Join("c8run", "JavaHome.class"),
		filepath.Join("c8run", "log"),
		filepath.Join("c8run", jre.DirectoryName),
		filepath.Join("c8run", "camunda-zeebe-"+camundaVersion),
		filepath.Join("c8run", ".env"),
		filepath.Join("c8run", "configuration", "application.yaml"),
	}

	if dirExists("c8run/rdbms-schema") {
		commonFiles = append(commonFiles, filepath.Join("c8run", "rdbms-schema"))
	}

	switch osType {
	case "windows":
		return append(commonFiles, filepath.Join("c8run", "c8run.exe"), filepath.Join("c8run", "package.bat"))
	case "linux", "darwin":
		return append(commonFiles, filepath.Join("c8run", "c8run"), filepath.Join("c8run", "start.sh"), filepath.Join("c8run", "shutdown.sh"), filepath.Join("c8run", "package.sh"))
	}
	return nil
}

func createTarGzArchive(filesToArchive []string, outputPath, sourceRoot, targetRoot string) error {
	outputArchive, err := os.Create(outputPath)
	if err != nil {
		return fmt.Errorf("failed to create empty archive file: %w\n%s", err, debug.Stack())
	}
	defer func() {
		if err := outputArchive.Close(); err != nil {
			log.Error().Err(err).Msg("failed to close output archive")
		}
	}()

	if err := archive.CreateTarGzArchive(filesToArchive, outputArchive, sourceRoot, targetRoot); err != nil {
		return fmt.Errorf("failed to fill camunda archive: %w\n%s", err, debug.Stack())
	}
	return nil
}

func dirExists(path string) bool {
	info, err := os.Stat(path)
	if err != nil {
		return false
	}
	return info.IsDir()
}

func createZipArchive(filesToArchive []string, outputPath, sourceRoot, targetRoot string) error {
	if err := archive.ZipSource(filesToArchive, outputPath, sourceRoot, targetRoot); err != nil {
		return fmt.Errorf("failed to create c8run package: %w\n%s", err, debug.Stack())
	}
	return nil
}

func BuildJavaScripts() error {
	javaVersionCmd := exec.Command("javac", "JavaVersion.java")
	var out strings.Builder
	var stderr strings.Builder
	javaVersionCmd.Stdout = &out
	javaVersionCmd.Stderr = &stderr
	err := javaVersionCmd.Run()
	if err != nil {
		return fmt.Errorf("failed to compile JavaVersion : %w", err)
	}
	javaHomeCmd := exec.Command("javac", "JavaHome.java")
	javaHomeCmd.Stdout = &out
	javaHomeCmd.Stderr = &stderr
	err = javaHomeCmd.Run()
	if err != nil {
		return fmt.Errorf("failed to compile JavaHome : %w", err)
	}
	return nil
}

func BuildJRE(camundaVersion, connectorsFilePath string) error {
	if err := ensureJLinkVersion(); err != nil {
		return err
	}

	modules, err := detectRequiredJREModules(camundaVersion, connectorsFilePath)
	if err != nil {
		return err
	}

	if err := os.RemoveAll(jre.DirectoryName); err != nil {
		return fmt.Errorf("failed to remove existing bundled JRE: %w", err)
	}

	args := buildJLinkArgs(modules, jre.DirectoryName)
	jlinkCmd := exec.Command("jlink", args...)
	var out strings.Builder
	var stderr strings.Builder
	jlinkCmd.Stdout = &out
	jlinkCmd.Stderr = &stderr
	if err := jlinkCmd.Run(); err != nil {
		return fmt.Errorf("failed to build bundled JRE with jlink: %w\n%s", err, stderr.String())
	}

	if err := materializeSymlinks(jre.DirectoryName); err != nil {
		return fmt.Errorf("failed to materialize bundled JRE symlinks: %w", err)
	}

	return nil
}

func ensureJLinkVersion() error {
	cmd := exec.Command("jlink", "--version")
	var out strings.Builder
	var stderr strings.Builder
	cmd.Stdout = &out
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		return fmt.Errorf("failed to run jlink --version; install a JDK %d or newer to package C8Run: %w\n%s", requiredJavaMajorVersion, err, stderr.String())
	}

	majorVersion, err := parseJavaMajorVersion(out.String())
	if err != nil {
		return fmt.Errorf("failed to parse jlink version %q: %w", strings.TrimSpace(out.String()), err)
	}
	if majorVersion < requiredJavaMajorVersion {
		return fmt.Errorf("jlink version %d is too old; install a JDK %d or newer to package C8Run", majorVersion, requiredJavaMajorVersion)
	}
	return nil
}

func detectRequiredJREModules(camundaVersion, connectorsFilePath string) ([]string, error) {
	camundaHome := "camunda-zeebe-" + camundaVersion
	camundaLibDir := filepath.Join(camundaHome, "lib")
	camundaJar := filepath.Join(camundaLibDir, "camunda-zeebe-"+camundaVersion+".jar")

	if _, err := os.Stat(camundaJar); err != nil {
		return nil, fmt.Errorf("failed to locate Camunda distribution JAR %s: %w", camundaJar, err)
	}
	if _, err := os.Stat(connectorsFilePath); err != nil {
		return nil, fmt.Errorf("failed to locate connectors runtime JAR %s: %w", connectorsFilePath, err)
	}

	classPath := strings.Join(
		[]string{
			filepath.Join(camundaLibDir, "*"),
			connectorsFilePath,
		},
		string(os.PathListSeparator),
	)

	args := []string{
		"--ignore-missing-deps",
		"--multi-release",
		strconv.Itoa(requiredJavaMajorVersion),
		"--print-module-deps",
		"--class-path",
		classPath,
		camundaJar,
		connectorsFilePath,
	}
	jdepsCmd := exec.Command("jdeps", args...)
	var out strings.Builder
	var stderr strings.Builder
	jdepsCmd.Stdout = &out
	jdepsCmd.Stderr = &stderr
	if err := jdepsCmd.Run(); err != nil {
		return nil, fmt.Errorf("failed to determine JRE modules with jdeps: %w\n%s", err, stderr.String())
	}

	modules := mergeModules(parseJDepsModuleOutput(out.String()), conservativeJREModules)
	if len(modules) == 0 {
		return nil, fmt.Errorf("jdeps did not report any JRE modules")
	}
	return modules, nil
}

func parseJavaMajorVersion(version string) (int, error) {
	version = strings.TrimSpace(version)
	if version == "" {
		return 0, fmt.Errorf("empty version")
	}

	firstToken := strings.Fields(version)[0]
	firstToken = strings.Trim(firstToken, `"`)
	parts := strings.Split(firstToken, ".")
	if len(parts) == 0 {
		return 0, fmt.Errorf("invalid version")
	}

	majorVersion, err := strconv.Atoi(parts[0])
	if err != nil {
		return 0, err
	}
	if majorVersion == 1 && len(parts) > 1 {
		return strconv.Atoi(parts[1])
	}
	return majorVersion, nil
}

func parseJDepsModuleOutput(output string) []string {
	var modules []string
	for _, line := range strings.Split(output, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		for _, module := range strings.Split(line, ",") {
			module = strings.TrimSpace(module)
			if module != "" {
				modules = append(modules, module)
			}
		}
	}
	return mergeModules(modules, nil)
}

func mergeModules(moduleGroups ...[]string) []string {
	moduleSet := make(map[string]struct{})
	for _, modules := range moduleGroups {
		for _, module := range modules {
			module = strings.TrimSpace(module)
			if module != "" {
				moduleSet[module] = struct{}{}
			}
		}
	}

	mergedModules := make([]string, 0, len(moduleSet))
	for module := range moduleSet {
		mergedModules = append(mergedModules, module)
	}
	sort.Strings(mergedModules)
	return mergedModules
}

func buildJLinkArgs(modules []string, outputDir string) []string {
	return []string{
		"--add-modules",
		strings.Join(modules, ","),
		"--strip-debug",
		"--compress",
		"zip-6",
		"--no-header-files",
		"--no-man-pages",
		"--output",
		outputDir,
	}
}

func materializeSymlinks(root string) error {
	return filepath.WalkDir(root, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.Type()&os.ModeSymlink == 0 {
			return nil
		}

		targetInfo, err := os.Stat(path)
		if err != nil {
			return err
		}
		if targetInfo.IsDir() {
			return fmt.Errorf("cannot materialize symlink to directory: %s", path)
		}

		content, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		if err := os.Remove(path); err != nil {
			return err
		}
		return os.WriteFile(path, content, targetInfo.Mode().Perm())
	})
}

func New(camundaVersion, connectorsVersion string) error {
	osType, architecture, pkgName, finalOutputExtension, extractFunc, err := setOsSpecificValues()
	if err != nil {
		fmt.Printf("%+v", err)
		os.Exit(1)
	}

	camundaFilePath := "camunda-zeebe-" + camundaVersion + pkgName
	camundaUrl := "https://repository.nexus.camunda.cloud/content/groups/internal/io/camunda/camunda-zeebe/" + camundaVersion + "/camunda-zeebe-" + camundaVersion + pkgName
	connectorsFilePath := "connector-runtime-bundle-" + connectorsVersion + "-with-dependencies.jar"
	connectorsUrl := "https://repository.nexus.camunda.cloud/content/groups/internal/io/camunda/connector/connector-runtime-bundle/" + connectorsVersion + "/" + connectorsFilePath
	sqlZipFilePath := "camunda-db-rdbms-schema-" + camundaVersion + ".zip"
	sqlZipUrl := "https://repository.nexus.camunda.cloud/content/groups/internal/io/camunda/camunda-db-rdbms-schema/" + camundaVersion + "/" + "camunda-db-rdbms-schema-" + camundaVersion + ".zip"

	// build JavaVersion and JavaHome
	err = BuildJavaScripts()
	if err != nil {
		return fmt.Errorf("failed to build JavaVersion: %w", err)
	}

	javaArtifactsToken, err := getJavaArtifactsToken()
	if err != nil {
		fmt.Printf("%+v", err)
		os.Exit(1)
	}

	Clean(camundaVersion)

	err = downloadAndExtract(camundaFilePath, camundaUrl, "camunda-zeebe-"+camundaVersion, ".", javaArtifactsToken, extractFunc)
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to download camunda %w\n%s", err, debug.Stack())
	}

	err = downloadAndExtract(connectorsFilePath, connectorsUrl, connectorsFilePath, ".", javaArtifactsToken, func(_, _ string) error { return nil })
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to fetch connectors: %w\n%s", err, debug.Stack())
	}

	err = downloadAndExtract(sqlZipFilePath, sqlZipUrl, "camunda-db-rdbms-schema-"+camundaVersion, "rdbms-schema", javaArtifactsToken, archive.UnzipSource)
	if err != nil {
		log.Warn().Msg("Package " + osType + ": failed to download camunda-db-rdbms-schema, continuing without unpacking it")
		// Continue without unpacking
	}

	if err := BuildJRE(camundaVersion, connectorsFilePath); err != nil {
		return fmt.Errorf("Package "+osType+": failed to build bundled JRE %w\n%s", err, debug.Stack())
	}

	err = os.Chdir("..")
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to chdir %w", err)
	}

	sourceRoot := "c8run"
	archiveRoot := "c8run-" + camundaVersion

	filesToArchive := getFilesToArchive(osType, connectorsFilePath, camundaVersion)
	outputFileName := "camunda8-run-" + camundaVersion + "-" + osType + "-" + architecture + finalOutputExtension
	outputPath := filepath.Join(sourceRoot, outputFileName)

	if osType == "linux" {
		if err := createTarGzArchive(filesToArchive, outputPath, sourceRoot, archiveRoot); err != nil {
			return fmt.Errorf("package %s: %w", osType, err)
		}
	} else {
		if err := createZipArchive(filesToArchive, outputPath, sourceRoot, archiveRoot); err != nil {
			return fmt.Errorf("package %s: %w", osType, err)
		}
	}

	err = os.Chdir("c8run")
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to chdir %w", err)
	}

	return nil
}
