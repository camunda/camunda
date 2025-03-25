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
)

func Clean(camundaVersion string, elasticsearchVersion string) {
	os.RemoveAll("elasticsearch-" + elasticsearchVersion)
	os.RemoveAll("camunda-zeebe-" + camundaVersion)

	logFiles := []string{"camunda.log", "connectors.log", "elasticsearch.log"}
	for _, logFile := range logFiles {
		_, err := os.Stat(filepath.Join("log", logFile))
		if !errors.Is(err, os.ErrNotExist) {
			os.Remove(filepath.Join("log", logFile))
		}
	}
}

func downloadAndExtract(filePath, url, extractDir string, authToken string, extractFunc func(string, string) error) error {
	err := archive.DownloadFile(filePath, url, authToken)
	if err != nil {
		return fmt.Errorf("downloadAndExtract: failed to download file at url %s\n%w\n%s", url, err, debug.Stack())
	}

	_, err = os.Stat(extractDir)
	if errors.Is(err, os.ErrNotExist) {
		err = extractFunc(filePath, ".")
		if err != nil {
			return fmt.Errorf("downloadAndExtract: failed to extract from archive at %s\n%w\n%s", filePath, err, debug.Stack())
		}
	}
	return nil
}

func setOsSpecificValues() (string, string, string, string, func(string, string) error, error) {
	var architecture string
	var osType string = runtime.GOOS
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
		if runtime.GOARCH == "amd64" {
			architecture = "x86_64"
		} else if runtime.GOARCH == "arm64" {
			architecture = "aarch64"
		} else {
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
	}

	if osType == "windows" {
		return append(commonFiles, filepath.Join("c8run", "c8run.exe"), filepath.Join("c8run", "package.bat"))
	} else if osType == "linux" || osType == "darwin" {
		return append(commonFiles, filepath.Join("c8run", "c8run"), filepath.Join("c8run", "start.sh"), filepath.Join("c8run", "shutdown.sh"), filepath.Join("c8run", "package.sh"))
	}
	return nil
}

func createTarGzArchive(filesToArchive []string, outputPath string) error {
	outputArchive, err := os.Create(outputPath)
	if err != nil {
		return fmt.Errorf("failed to create empty archive file: %w\n%s", err, debug.Stack())
	}
	defer outputArchive.Close()

	if err := archive.CreateTarGzArchive(filesToArchive, outputArchive); err != nil {
		return fmt.Errorf("failed to fill camunda archive: %w\n%s", err, debug.Stack())
	}
	return nil
}

func createZipArchive(filesToArchive []string, outputPath string) error {
	if err := archive.ZipSource(filesToArchive, outputPath); err != nil {
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
	var osType, architecture, pkgName, finalOutputExtension, extractFunc, err = setOsSpecificValues()
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
	composeUrl := "https://github.com/camunda/camunda-platform/archive/refs/tags/" + composeTag + pkgName
	composeFilePath := composeTag + pkgName
	composeExtractionPath := "camunda-platform-" + composeTag
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

	err = downloadAndExtract(elasticsearchFilePath, elasticsearchUrl, "elasticsearch-"+elasticsearchVersion, "", extractFunc)
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to fetch elasticsearch: %w\n%s", err, debug.Stack())
	}

	err = downloadAndExtract(camundaFilePath, camundaUrl, "camunda-zeebe-"+camundaVersion, javaArtifactsToken, extractFunc)
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to download camunda %w\n%s", err, debug.Stack())
	}

	err = downloadAndExtract(connectorsFilePath, connectorsUrl, connectorsFilePath, javaArtifactsToken, func(_, _ string) error { return nil })
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to fetch connectors: %w\n%s", err, debug.Stack())
	}

	err = downloadAndExtract(composeFilePath, composeUrl, composeExtractionPath, authToken, extractFunc)
	if err != nil {
		return fmt.Errorf("Package "+osType+": failed to fetch compose release %w\n%s", err, debug.Stack())
	}

	filesToArchive := getFilesToArchive(osType, elasticsearchVersion, connectorsFilePath, camundaVersion, composeExtractionPath)
	outputFileName := "camunda8-run-" + camundaVersion + "-" + osType + "-" + architecture + finalOutputExtension
	var outputPath string

	if osType == "linux" {
		outputPath = filepath.Join("c8run", outputFileName)
		err = os.Chdir("..")
		if err != nil {
			return fmt.Errorf("Package "+osType+": failed to chdir %w", err)
		}
		if err := createTarGzArchive(filesToArchive, outputPath); err != nil {
			return fmt.Errorf("Package %s: %w", osType, err)
		}
		err = os.Chdir("c8run")
		if err != nil {
			return fmt.Errorf("Package "+osType+": failed to chdir %w", err)
		}
	} else {
		outputPath = outputFileName
		if err := createZipArchive(filesToArchive, outputPath); err != nil {
			return fmt.Errorf("Package %s: %w", osType, err)
		}
	}

	return nil

}
