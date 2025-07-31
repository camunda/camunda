/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package health

import (
	"context"
	"crypto/tls"
	"fmt"
	"net/http"
	"os"
	"text/template"
	"time"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
)

type opener interface {
	OpenBrowser(ctx context.Context, url string) error
}

type Ports struct {
	OperatePort  int
	TasklistPort int
	IdentityPort int
	CamundaPort  int
}

func QueryCamunda(ctx context.Context, c8 opener, name string, settings types.C8RunSettings, retries int) error {
	healthEndpoint := fmt.Sprintf("%s://localhost:9600/actuator/health", settings.GetProtocol())
	if isRunning(ctx, name, healthEndpoint, retries, 14*time.Second) {
		log.Info().Str("name", name).Msg("has successfully been started.")
		if err := c8.OpenBrowser(ctx, settings.StartupUrl); err != nil {
			log.Err(err).Msg("Failed to open browser")
			return nil
		}
		if err := PrintStatus(settings); err != nil {
			log.Err(err).Msg("Failed to print status")
			return err
		}
		return nil
	}
	return fmt.Errorf("queryCamunda: %s did not start", name)
}

func isRunning(ctx context.Context, name, url string, retries int, delay time.Duration) bool {
	transport := &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}}
	client := &http.Client{Transport: transport, Timeout: 5 * time.Second}

	for i := retries; i >= 0; i-- {
		log.Info().Str("name", name).Int("retries_left", i).Msg("Waiting for service to start")

		// Build request with context so that we can cancel early when the caller terminates.
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
		if err == nil {
			resp, err := client.Do(req)
			if err == nil {
				if resp.Body != nil {
					resp.Body.Close()
				}

				if resp.StatusCode >= 200 && resp.StatusCode < 400 {
					return true
				}
			}
		}

		if i == 0 {
			log.Warn().Msg("Retry attempts expired")
			return false
		}

		select {
		case <-ctx.Done():
			log.Info().Msg("Gracefully stopping our retries...")
			return false
		case <-time.After(delay):
		}
	}
	return false
}

func PrintStatus(settings types.C8RunSettings) error {
	operatePort, tasklistPort, identityPort, camundaPort := 8088, 8088, 8088, 8088

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
		return fmt.Errorf("failed to parse endpoints template: %s", err.Error())
	}

	data := Ports{
		OperatePort:  operatePort,
		TasklistPort: tasklistPort,
		IdentityPort: identityPort,
		CamundaPort:  camundaPort,
	}

	err = t.Execute(os.Stdout, data)
	if err != nil {
		return fmt.Errorf("failed to fill endpoints template %s", err.Error())
	}
	return nil
}
