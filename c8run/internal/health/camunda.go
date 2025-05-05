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
	"text/template"
	"time"

	"github.com/camunda/camunda/c8run/internal/types"
)

type opener interface {
	OpenBrowser(protocol string, port int) error
}

type Ports struct {
	OperatePort  int
	TasklistPort int
	IdentityPort int
	CamundaPort  int
}

func QueryCamunda(c8 opener, name string, settings types.C8RunSettings) error {
	protocol := "http"
	http.DefaultTransport.(*http.Transport).TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	if settings.HasKeyStore() {
		protocol = "https"
	}
	healthEndpoint := protocol + "://localhost:9600/actuator/health"
	if isRunning(name, healthEndpoint, 24, 14*time.Second) {
		fmt.Println(name + " has successfully been started.")
		err := c8.OpenBrowser(protocol, settings.Port)
		if err != nil {
			fmt.Println("Failed to open browser")
			return nil
		}
		if err := PrintStatus(settings); err != nil {
			fmt.Println("Failed to print status:", err)
			return err
		}
		return nil
	} else {
		return fmt.Errorf("queryCamunda: %s did not start", name)
	}
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

func PrintStatus(settings types.C8RunSettings) error {
	operatePort, tasklistPort, identityPort, camundaPort := 8080, 8080, 8080, 8080

	// Overwrite ports if Docker is enabled
	if settings.Docker {
		operatePort = 8081
		tasklistPort = 8082
		identityPort = 8084
		camundaPort = 8088
	}

	endpoints, _ := os.ReadFile("endpoints.txt")
	t, err := template.New("endpoints").Parse(string(endpoints))
	if err != nil {
		return fmt.Errorf("Error: failed to parse endpoints template: %s", err.Error())
	}

	data := Ports{
		OperatePort:  operatePort,
		TasklistPort: tasklistPort,
		IdentityPort: identityPort,
		CamundaPort:  camundaPort,
	}

	err = t.Execute(os.Stdout, data)
	if err != nil {
		return fmt.Errorf("Error: failed to fill endpoints template %s", err.Error())
	}
	return nil
}
