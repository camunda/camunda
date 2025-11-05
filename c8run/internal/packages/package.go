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
	"strings"

	"github.com/camunda/camunda/c8run/internal/archive"
	"github.com/rs/zerolog/log"
)

func Clean(camundaVersion string, elasticsearchVersion string) {
	if err := os.RemoveAll("elasticsearch-" + elasticsearchVersion); err != nil {
		log.Error().Err(err).Msg("failed to remove elasticsearch")
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

	logFiles := []string{"camunda.log", "connectors.log", "elasticsearch.log"}
	for _, logFile := range logFiles {
		_, err := os.Stat(filepath.Join("log", logFile))
		if !errors.Is(err, os.ErrNotExist) {
			if err := os.Remove(filepath.Join("log", logFile)); err != nil {
				log.Error().Err(err).Msg("failed to remove log file")
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

func getFilesToArchive(osType, elasticsearchVersion, connectorsFilePath, camundaVersion, composeExtractionPath string) []string {
	commonFiles := []string{
		filepath.Join("c8run", "README.md"),
		filepath.Join("c8run", "connectors-application.properties"),
		filepath.Join("c8run", connectorsFilePath),
		filepath.Join("c8run", "elasticsearch-"+elasticsearchVersion),
		filepath.Join("c8run", "custom_connectors"),
		filepath.Join("c8run", "endpoints.txt"),
		filepath.Join("c8run", "JavaVersion.class"),
		filepath.Join("c8run", "JavaHome.class"),
		filepath.Join("c8run", "log"),
		filepath.Join("c8run", "camunda-zeebe-"+camundaVersion),
		filepath.Join("c8run", ".env"),
		filepath.Join("c8run", composeExtractionPath),
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

func New(camundaVersion, elasticsearchVersion, connectorsVersion, composeTag string) error {
	osType, architecture, pkgName, finalOutputExtension, extractFunc, err := setOsSpecificValues()
	if err != nil {
		fmt.Printf("%+v", err)
		os.Exit(1)
	}

	elasticsearchUrl := "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-" + elasticsearchVersion + "-" + osType + "-" + architecture + pkgName
	elasticsearchFilePath := "elasticsearch-" + elasticsearchVersion + pkgName
	camundaFilePath := "camunda-zeebe-" + camundaVersion + pkgName
	camundaUrl := "https://repository.nexus.camunda.cloud/content/groups/internal/io/camunda/camunda-zeebe/" + camundaVersion + "/camunda-zeebe-" + camundaVersion + pkgName
	connectorsFilePath := "connector-runtime-bundle-" + connectorsVersion + "-with-dependencies.jar"
	connectorsUrl := "https://repository.nexus.camunda.cloud/content/groups/internal/io/camunda/connector/connector-runtime-bundle/" + connectorsVersion + "/" + connectorsFilePath
	sqlZipFilePath := "camunda-db-rdbms-schema-" + camundaVersion + ".zip"
	sqlZipUrl := "https://repository.nexus.camunda.cloud/content/groups/internal/io/camunda/camunda-db-rdbms-schema/" + camundaVersion + "/" + "camunda-db-rdbms-schema-" + camundaVersion + ".zip"

	composeUrl := "https://github.com/camunda/camunda-distributions/releases/download/docker-compose-" + composeTag + "/docker-compose-" + composeTag + ".zip"
	composeFilePath := "docker-compose-" + composeTag + ".zip"
	// just a file to check to see if it was already extracted
	composeExtractionPath := "docker-compose-" + composeTag

	authToken := os.Getenv("GH_TOKEN")

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

	Clean(camundaVersion, elasticsearchVersion)

	err = downloadAndExtract(elasticsearchFilePath, elasticsearchUrl, "elasticsearch-"+elasticsearchVersion, ".", "", extractFunc)
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to fetch elasticsearch: %w\n%s", err, debug.Stack())
	}

	err = downloadAndExtract(camundaFilePath, camundaUrl, "camunda-zeebe-"+camundaVersion, ".", javaArtifactsToken, extractFunc)
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to download camunda %w\n%s", err, debug.Stack())
	}

	err = downloadAndExtract(connectorsFilePath, connectorsUrl, connectorsFilePath, ".", javaArtifactsToken, func(_, _ string) error { return nil })
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to fetch connectors: %w\n%s", err, debug.Stack())
	}

	err = downloadAndExtract(composeFilePath, composeUrl, composeExtractionPath, composeExtractionPath, authToken, archive.UnzipSource)
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to fetch compose release %w\n%s", err, debug.Stack())
	}

	err = downloadAndExtract(sqlZipFilePath, sqlZipUrl, "camunda-db-rdbms-schema-"+camundaVersion, "rdbms-schema", javaArtifactsToken, archive.UnzipSource)
	if err != nil {
		log.Warn().Msg("Package " + osType + ": failed to download camunda-db-rdbms-schema, continuing without unpacking it")
		// Continue without unpacking
	}

	err = os.Chdir("..")
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to chdir %w", err)
	}

	sourceRoot := "c8run"
	archiveRoot := "c8run-" + camundaVersion

	filesToArchive := getFilesToArchive(osType, elasticsearchVersion, connectorsFilePath, camundaVersion, composeExtractionPath)
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
