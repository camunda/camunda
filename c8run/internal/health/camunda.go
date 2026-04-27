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
	"strings"
	"text/template"
	"time"

	"github.com/camunda/camunda/c8run/internal/startupurl"
	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
)

const (
	inboundConnectorsPort = 8086
	zeebeAPIURL           = "http://localhost:26500"
	camundaMetricsURL     = "http://localhost:9600/actuator/prometheus"
	quickstartURL         = "https://docs.camunda.io/docs/next/guides/getting-started-example/"
	javaSpringGuideURL    = "https://docs.camunda.io/docs/guides/getting-started-java-spring/"
	agentGuideURL         = "https://docs.camunda.io/docs/next/guides/getting-started-agentic-orchestration/"
)

type opener interface {
	OpenBrowser(ctx context.Context, url string) error
}

type StartupSummary struct {
	OperateURL           string
	TasklistURL          string
	AdminURL             string
	Username             string
	Password             string
	OrchestrationAPI     string
	OrchestrationMCP     string
	InboundConnectorsAPI string
	ZeebeAPI             string
	CamundaMetrics       string
	DesktopModelerTarget string
	QuickstartURL        string
	JavaDevelopersURL    string
	AgentGuideURL        string
}

var (
	isRunningFunc   = isRunning
	printStatusFunc = PrintStatus
	markSeenStartup = startupurl.MarkSeen
)

func QueryCamunda(ctx context.Context, c8 opener, name string, settings types.C8RunSettings, retries int) error {
	healthEndpoint := fmt.Sprintf("%s://localhost:9600/actuator/health", settings.GetProtocol())
	if isRunningFunc(ctx, name, healthEndpoint, retries, 14*time.Second) {
		if err := c8.OpenBrowser(ctx, settings.StartupUrl); err != nil {
			log.Err(err).Msg("Failed to open browser")
		}
		if err := printStatusFunc(settings); err != nil {
			log.Err(err).Msg("Failed to print status")
			return err
		}
		if err := markSeenStartup(settings.StartupMarkerPath); err != nil {
			log.Warn().Err(err).Str("path", settings.StartupMarkerPath).Msg("Failed to persist quickstart marker")
		}
		return nil
	}
	return fmt.Errorf("queryCamunda: %s did not start", name)
}

func QueryConnectors(ctx context.Context, name string, retries int) error {
	healthEndpoint := fmt.Sprintf("http://localhost:%d/actuator/health", inboundConnectorsPort)
	if isRunningFunc(ctx, name, healthEndpoint, retries, 14*time.Second) {
		return nil
	}
	return fmt.Errorf("queryConnectors: %s did not start", name)
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
					if err := resp.Body.Close(); err != nil {
						log.Error().Err(err).Msg("failed to close response body")
					}
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
	operatePort, tasklistPort, adminPort, camundaPort := settings.Port, settings.Port, settings.Port, settings.Port

	username := settings.Username
	if strings.TrimSpace(username) == "" {
		username = "demo"
	}
	password := settings.Password
	if strings.TrimSpace(password) == "" {
		password = "demo"
	}

	protocol := settings.GetProtocol()
	endpoints, _ := os.ReadFile("endpoints.txt")
	t, err := template.New("endpoints").Parse(string(endpoints))
	if err != nil {
		return fmt.Errorf("failed to parse endpoints template: %s", err.Error())
	}

	data := StartupSummary{
		OperateURL:           fmt.Sprintf("%s://localhost:%d/operate", protocol, operatePort),
		TasklistURL:          fmt.Sprintf("%s://localhost:%d/tasklist", protocol, tasklistPort),
		AdminURL:             fmt.Sprintf("%s://localhost:%d/admin", protocol, adminPort),
		Username:             username,
		Password:             password,
		OrchestrationAPI:     fmt.Sprintf("%s://localhost:%d/v2/", protocol, camundaPort),
		OrchestrationMCP:     fmt.Sprintf("%s://localhost:%d/mcp/cluster", protocol, camundaPort),
		InboundConnectorsAPI: fmt.Sprintf("http://localhost:%d/", inboundConnectorsPort),
		ZeebeAPI:             zeebeAPIURL,
		CamundaMetrics:       camundaMetricsURL,
		DesktopModelerTarget: fmt.Sprintf("%s://localhost:%d/v2/", protocol, camundaPort),
		QuickstartURL:        quickstartURL,
		JavaDevelopersURL:    javaSpringGuideURL,
		AgentGuideURL:        agentGuideURL,
	}

	err = t.Execute(os.Stdout, data)
	if err != nil {
		return fmt.Errorf("failed to fill endpoints template %s", err.Error())
	}
	return nil
}
