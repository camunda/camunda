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
	"sync"
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
	// Warning is set when the tenant is up but the seeded login could not be confirmed.
	Warning string
	// Unverified means the tenant answered but authentication stopped the request before its
	// secondary storage could be checked (e.g. under OIDC). It does not fail startup.
	Unverified bool
}

// Probe checks each tenant's topology endpoint so a tenant that failed to come up is named
// explicitly instead of hiding behind the cluster-wide health check.
// Tenants are probed concurrently under one deadline (attempts*delay), so the total wait
// does not grow with the number of tenants.
func Probe(ctx context.Context, settings types.C8RunSettings, attempts int, delay time.Duration) []ProbeResult {
	client := &http.Client{
		Timeout:   5 * time.Second,
		Transport: &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}},
	}
	ctx, cancel := context.WithTimeout(ctx, time.Duration(attempts)*delay)
	defer cancel()
	results := make([]ProbeResult, len(settings.PhysicalTenants))
	var wg sync.WaitGroup
	for i, t := range settings.PhysicalTenants {
		wg.Add(1)
		go func(i int, t types.PhysicalTenant) {
			defer wg.Done()
			results[i] = probeOne(ctx, client, settings, t, attempts, delay)
		}(i, t)
	}
	wg.Wait()
	return results
}

func probeOne(ctx context.Context, client *http.Client, settings types.C8RunSettings, t types.PhysicalTenant, attempts int, delay time.Duration) ProbeResult {
	{
		// A search endpoint is gated on the tenant's secondary storage (503 until its schema is
		// ready), so this checks the tenant can actually serve requests, not just its topology.
		url := EndpointsFor(t.ID, settings.GetProtocol(), settings.Port).REST + "process-definitions/search"
		result := ProbeResult{ID: t.ID}
		for i := 0; i < attempts; i++ {
			req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, strings.NewReader(`{"page":{"limit":1}}`))
			if err == nil {
				req.Header.Set("Content-Type", "application/json")
			}
			if err != nil {
				result.Err = err.Error()
				break
			}
			req.SetBasicAuth(t.Username, t.Password)
			resp, err := client.Do(req)
			if err == nil {
				_ = resp.Body.Close()
				if resp.StatusCode < 300 {
					result.Ready, result.Err, result.Warning = true, "", ""
					break
				}
				// Unknown tenants are rejected with 404 before security runs, so 401/403 proves the
				// tenant is configured and serving. Security runs before the storage check, though,
				// so its storage readiness is reported as unverified rather than claimed.
				if resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden {
					result.Ready, result.Unverified, result.Err = true, true, ""
					if settings.OIDC {
						result.Warning = "storage readiness can't be checked without an OIDC token; open its Operate URL to confirm"
					} else {
						result.Warning = fmt.Sprintf("the login for %s was rejected (HTTP %d), so storage readiness was not checked; if this tenant ID was used before, it keeps the users from that time", t.Username, resp.StatusCode)
					}
					break
				}
				result.Err = fmt.Sprintf("POST %s returned HTTP %d", url, resp.StatusCode)
				switch resp.StatusCode {
				case http.StatusNotFound:
					result.Err += " (the tenant is not configured; check log/camunda.log for physical tenant validation errors)"
				case http.StatusServiceUnavailable:
					result.Err += " (its secondary storage is not ready; check log/camunda.log for schema errors)"
				}
			} else if result.Err == "" || ctx.Err() == nil {
				result.Err = err.Error()
			}
			select {
			case <-ctx.Done():
				if result.Err == "" {
					result.Err = "timed out waiting for the tenant"
				}
				return result
			case <-time.After(delay):
			}
		}
		return result
	}
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
	_, _ = fmt.Fprintln(w, "Physical tenants:")
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(tw, "  TENANT\tSTATUS\tLOGIN\tOPERATE\tORCHESTRATION API\tCONNECTORS")
	defaultConnectors := "disabled"
	if !settings.DisableConnectors {
		defaultConnectors = fmt.Sprintf("http://localhost:%d/", inboundPort)
	}
	d := EndpointsFor(DefaultID, protocol, settings.Port)
	_, _ = fmt.Fprintf(tw, "  %s\t%s\t%s\t%s\t%s\t%s\n", DefaultID, "ready", orDemo(settings.Username), d.Operate, d.REST, defaultConnectors)
	for _, t := range settings.PhysicalTenants {
		e := EndpointsFor(t.ID, protocol, settings.Port)
		state := "ready"
		if r, ok := status[t.ID]; ok {
			switch {
			case !r.Ready:
				state = "NOT READY"
			case r.Unverified:
				state = "up (unverified)"
			}
		}
		conn := "disabled"
		if t.Connectors {
			conn = fmt.Sprintf("http://localhost:%d/", t.ConnectorsPort)
		}
		_, _ = fmt.Fprintf(tw, "  %s\t%s\t%s\t%s\t%s\t%s\n", t.ID, state, t.Username, e.Operate, e.REST, conn)
	}
	_ = tw.Flush()
	_, _ = fmt.Fprintln(w)
	for _, r := range results {
		if !r.Ready {
			_, _ = fmt.Fprintf(w, "  ! %s did not become ready: %s\n", r.ID, r.Err)
		} else if r.Warning != "" {
			_, _ = fmt.Fprintf(w, "  ! %s is up, but %s\n", r.ID, r.Warning)
		}
	}
	example := settings.PhysicalTenants[0]
	ex := EndpointsFor(example.ID, protocol, settings.Port)
	_, _ = fmt.Fprintln(w, "Connect to a physical tenant:")
	_, _ = fmt.Fprintf(w, "  - Desktop Modeler: cluster endpoint %s\n", ex.REST)
	_, _ = fmt.Fprintf(w, "  - REST:            prefix paths with /physical-tenants/<id> (e.g. %stopology)\n", ex.REST)
	_, _ = fmt.Fprintf(w, "  - gRPC (:26500):   send header \"Camunda-Physical-Tenant: %s\"\n", example.ID)
	_, _ = fmt.Fprintf(w, "  - Java/Spring:     camunda.client.physical-tenant-id=%s\n", example.ID)
	_, _ = fmt.Fprintf(w, "  - MCP:             %s\n", ex.MCP)
	_, _ = fmt.Fprintf(w, "Each tenant has its own local secrets: `c8run secrets --tenant %s set <NAME>`.\n", example.ID)
	_, _ = fmt.Fprintln(w, "Manage tenants with `c8run tenants list|add|remove`.")
	_, _ = fmt.Fprintln(w)
}

func orDemo(v string) string {
	if strings.TrimSpace(v) == "" {
		return "demo"
	}
	return v
}
