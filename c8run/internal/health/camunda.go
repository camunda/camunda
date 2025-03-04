/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package health

import (
	"crypto/tls"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"time"
)

type C8Run interface {
	OpenBrowser() error
	ProcessTree(commandPid int) []*os.Process
	VersionCmd(javaBinaryPath string) *exec.Cmd
	ElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd
	ConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd
	CamundaCmd(camundaVersion string, parentDir string, extraArgs string) *exec.Cmd
}

func QueryCamunda(c8 C8Run, name string, url string) error {
	http.DefaultTransport.(*http.Transport).TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	if isRunning(name, url, 24, 14*time.Second) {
		fmt.Println(name + " has successfully been started.")
		err := c8.OpenBrowser()
		if err != nil {
			fmt.Println("Failed to open browser")
			return nil
		}
		if err := getStatus(); err != nil {
			fmt.Println("Failed to print status:", err)
			return err
		}
		return nil
	} else {
		return fmt.Errorf("Error: %s did not start!", name)
	}
	return nil
}

func isRunning(name, url string, retries int, delay time.Duration) bool {
	for retries >= 0 {
		fmt.Printf("Waiting for %s to start. %d retries left\n", name, retries)
		time.Sleep(delay)
		resp, err := http.Get(url)
		if err == nil && resp.StatusCode >= 200 && resp.StatusCode <= 400 {
			return true
		}
		retries--
	}
	return false
}

func printStatus(port int) error {
	endpoints, err := os.ReadFile("endpoints.txt")
	fmt.Println(string(endpoints))
	return err
}

func getStatus() error {
	endpoints, err := os.ReadFile("endpoints.txt")
	fmt.Println(string(endpoints))
	return err
}
