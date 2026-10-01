/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package physicaltenants

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"net/http"
	"strings"
	"text/tabwriter"
	"time"

	"github.com/camunda/camunda/c8run/internal/types"
)

// Endpoints are the URLs of one physical tenant.
type Endpoints struct {
	Base, Operate, Tasklist, Admin, REST, MCP string
}

// EndpointsFor builds a tenant's URLs. The default tenant is served unprefixed.
func EndpointsFor(id, protocol string, port int) Endpoints {
	base := fmt.Sprintf("%s://localhost:%d", protocol, port)
	if id != DefaultID {
		base += "/physical-tenants/" + id
	}
	return Endpoints{
		Base:     base,
		Operate:  base + "/operate",
		Tasklist: base + "/tasklist",
		Admin:    base + "/admin",
		REST:     base + "/v2/",
		MCP:      base + "/mcp/cluster",
	}
}

// ProbeResult is the readiness of one tenant after Camunda reports healthy.
type ProbeResult struct {
	ID    string
	Ready bool
	Err   string
}

// Probe checks each tenant's topology endpoint so a tenant that failed to come up is named
// explicitly instead of hiding behind the cluster-wide health check.
func Probe(ctx context.Context, settings types.C8RunSettings, attempts int, delay time.Duration) []ProbeResult {
	client := &http.Client{
		Timeout:   5 * time.Second,
		Transport: &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}},
	}
	results := make([]ProbeResult, 0, len(settings.PhysicalTenants))
	for _, t := range settings.PhysicalTenants {
		url := EndpointsFor(t.ID, settings.GetProtocol(), settings.Port).REST + "topology"
		result := ProbeResult{ID: t.ID}
		for i := 0; i < attempts; i++ {
			req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
			if err != nil {
				result.Err = err.Error()
				break
			}
			req.SetBasicAuth(t.Username, t.Password)
			resp, err := client.Do(req)
			if err == nil {
				_ = resp.Body.Close()
				if resp.StatusCode < 300 {
					result.Ready, result.Err = true, ""
					break
				}
				result.Err = fmt.Sprintf("GET %s returned HTTP %d", url, resp.StatusCode)
				if resp.StatusCode == http.StatusNotFound {
					result.Err += " (the tenant is not configured; check log/camunda.log for physical tenant validation errors)"
				}
			} else {
				result.Err = err.Error()
			}
			select {
			case <-ctx.Done():
				return append(results, result)
			case <-time.After(delay):
			}
		}
		results = append(results, result)
	}
	return results
}

// PrintSummary writes the physical tenant section of the startup summary.
func PrintSummary(w io.Writer, settings types.C8RunSettings, results []ProbeResult, inboundPort int) {
	if len(settings.PhysicalTenants) == 0 {
		return
	}
	status := map[string]ProbeResult{}
	for _, r := range results {
		status[r.ID] = r
	}
	protocol := settings.GetProtocol()
	fmt.Fprintln(w, "Physical tenants:")
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	fmt.Fprintln(tw, "  TENANT\tSTATUS\tLOGIN\tOPERATE\tORCHESTRATION API\tCONNECTORS")
	defaultConnectors := "disabled"
	if !settings.DisableConnectors {
		defaultConnectors = fmt.Sprintf("http://localhost:%d/", inboundPort)
	}
	d := EndpointsFor(DefaultID, protocol, settings.Port)
	fmt.Fprintf(tw, "  %s\t%s\t%s\t%s\t%s\t%s\n", DefaultID, "ready", orDemo(settings.Username), d.Operate, d.REST, defaultConnectors)
	for _, t := range settings.PhysicalTenants {
		e := EndpointsFor(t.ID, protocol, settings.Port)
		state := "ready"
		if r, ok := status[t.ID]; ok && !r.Ready {
			state = "NOT READY"
		}
		conn := "disabled"
		if t.Connectors {
			conn = fmt.Sprintf("http://localhost:%d/", t.ConnectorsPort)
		}
		fmt.Fprintf(tw, "  %s\t%s\t%s\t%s\t%s\t%s\n", t.ID, state, t.Username, e.Operate, e.REST, conn)
	}
	_ = tw.Flush()
	fmt.Fprintln(w)
	for _, r := range results {
		if !r.Ready {
			fmt.Fprintf(w, "  ! %s did not become ready: %s\n", r.ID, r.Err)
		}
	}
	example := settings.PhysicalTenants[0]
	ex := EndpointsFor(example.ID, protocol, settings.Port)
	fmt.Fprintln(w, "Connect to a physical tenant:")
	fmt.Fprintf(w, "  - Desktop Modeler: cluster endpoint %s\n", ex.REST)
	fmt.Fprintf(w, "  - REST:            prefix paths with /physical-tenants/<id> (e.g. %stopology)\n", ex.REST)
	fmt.Fprintf(w, "  - gRPC (:26500):   send header \"Camunda-Physical-Tenant: %s\"\n", example.ID)
	fmt.Fprintf(w, "  - Java/Spring:     camunda.client.physical-tenant-id=%s\n", example.ID)
	fmt.Fprintf(w, "  - MCP:             %s\n", ex.MCP)
	fmt.Fprintln(w, "Local secrets (camunda.secrets.*) are shared by all physical tenants.")
	fmt.Fprintln(w, "Manage tenants with `c8run tenants list|add|remove`.")
	fmt.Fprintln(w)
}

func orDemo(v string) string {
	if strings.TrimSpace(v) == "" {
		return "demo"
	}
	return v
}
