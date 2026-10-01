/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package physicaltenants

import (
	"bytes"
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gopkg.in/yaml.v3"
)

func TestValidateID(t *testing.T) {
	for _, ok := range []string{"sales", "team2", "a", strings.Repeat("a", 64)} {
		assert.NoError(t, ValidateID(ok), ok)
	}
	err := ValidateID("Sales-EU")
	require.Error(t, err)
	assert.Contains(t, err.Error(), `try "saleseu"`)
	assert.ErrorContains(t, ValidateID("default"), "reserved")
	assert.ErrorContains(t, ValidateID(""), "empty")
	assert.ErrorContains(t, ValidateID(strings.Repeat("a", 65)), "maximum is 64")
	assert.ErrorContains(t, ValidateID("---"), "lowercase letters and digits only")
}

func TestParseIDList(t *testing.T) {
	ids, err := ParseIDList([]string{"a,b", " c ", ""})
	require.NoError(t, err)
	assert.Equal(t, []string{"a", "b", "c"}, ids)
	_, err = ParseIDList([]string{"a", "a"})
	assert.ErrorContains(t, err, "more than once")
	_, err = ParseIDList([]string{"a,B"})
	assert.Error(t, err)
}

func TestSupportsVersion(t *testing.T) {
	assert.True(t, SupportsVersion("8.10.0"))
	assert.True(t, SupportsVersion("8.11.0-alpha1"))
	assert.True(t, SupportsVersion("9.0.0"))
	assert.True(t, SupportsVersion("SNAPSHOT"))
	assert.False(t, SupportsVersion("8.9.3"))
	assert.False(t, SupportsVersion("8.8.0"))
}

func TestStoreAddListRemove(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), "nested", FileName))
	tenants, err := store.List()
	require.NoError(t, err)
	assert.Empty(t, tenants)

	require.NoError(t, store.Add([]Tenant{{ID: "sales"}, {ID: "hr", Username: "alice"}}, map[string]string{"hr": "s3cret"}))
	tenants, err = store.List()
	require.NoError(t, err)
	assert.Equal(t, []Tenant{{ID: "hr", Username: "alice"}, {ID: "sales"}}, tenants)

	pw, ok, err := store.Password("hr")
	require.NoError(t, err)
	assert.True(t, ok)
	assert.Equal(t, "s3cret", pw)

	assert.ErrorContains(t, store.Add([]Tenant{{ID: "sales"}}, nil), "already exists")
	assert.ErrorContains(t, store.Remove([]string{"nope"}), "unknown physical tenant")

	require.NoError(t, store.Remove([]string{"hr"}))
	_, ok, err = store.Password("hr")
	require.NoError(t, err)
	assert.False(t, ok)
	content, err := os.ReadFile(store.Path())
	require.NoError(t, err)
	assert.NotContains(t, string(content), "s3cret", "a removed tenant's password is deleted")

	require.NoError(t, store.Reset())
	tenants, err = store.List()
	require.NoError(t, err)
	assert.Empty(t, tenants)
}

func TestStoreRejectsInvalidFile(t *testing.T) {
	path := filepath.Join(t.TempDir(), FileName)
	require.NoError(t, os.WriteFile(path, []byte("version: 1\ntenants:\n  - id: Bad-ID\n"), 0o644))
	_, err := NewStore(path).List()
	assert.ErrorContains(t, err, "not a valid physical tenant ID")
}

func TestResolvePathHonoursEnv(t *testing.T) {
	base := t.TempDir()
	t.Setenv(FileEnv, "custom.yaml")
	path, err := ResolvePath(base)
	require.NoError(t, err)
	assert.Equal(t, filepath.Join(base, "custom.yaml"), path)
}

func renderMap(t *testing.T, storage string) map[string]any {
	t.Helper()
	content, err := Render([]types.PhysicalTenant{{ID: "sales", Username: "demo", Password: "demo"}}, storage)
	require.NoError(t, err)
	assert.NotContains(t, string(content), "password", "logins are passed via the environment")
	var root map[string]any
	require.NoError(t, yaml.Unmarshal(content, &root))
	return root["camunda"].(map[string]any)["physical-tenants"].(map[string]any)["sales"].(map[string]any)
}

func TestRenderRdbmsUsesTablePrefix(t *testing.T) {
	for _, storage := range []string{"", "rdbms"} {
		sales := renderMap(t, storage)
		prefix := sales["data"].(map[string]any)["secondary-storage"].(map[string]any)["rdbms"].(map[string]any)["prefix"]
		assert.Equal(t, "SALES_", prefix)
	}
}

func TestRenderDocumentStoresUseIndexPrefixAndPolicies(t *testing.T) {
	for _, storage := range []string{"elasticsearch", "opensearch"} {
		sales := renderMap(t, storage)
		store := sales["data"].(map[string]any)["secondary-storage"].(map[string]any)[storage].(map[string]any)
		assert.Equal(t, "sales", store["index-prefix"])
		history := store["history"].(map[string]any)
		assert.Equal(t, "sales-camunda-retention-policy", history["policy-name"])
	}
}

func TestRenderNoneHasNoStorage(t *testing.T) {
	assert.NotContains(t, renderMap(t, "none"), "data")
}

func TestCredentialEnv(t *testing.T) {
	env := CredentialEnv([]types.PhysicalTenant{{ID: "hr", Username: "alice", Password: "pw"}})
	assert.Equal(t, "alice", env["CAMUNDA_PHYSICALTENANTS_HR_SECURITY_INITIALIZATION_USERS_0_USERNAME"])
	assert.Equal(t, "pw", env["CAMUNDA_PHYSICALTENANTS_HR_SECURITY_INITIALIZATION_USERS_0_PASSWORD"])
	assert.Equal(t, "alice", env["CAMUNDA_PHYSICALTENANTS_HR_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0"])
}

func TestWriteGeneratedConfigRemovesStaleFile(t *testing.T) {
	base := t.TempDir()
	path, err := WriteGeneratedConfig(base, []types.PhysicalTenant{{ID: "a"}}, "rdbms")
	require.NoError(t, err)
	assert.FileExists(t, path)
	empty, err := WriteGeneratedConfig(base, nil, "rdbms")
	require.NoError(t, err)
	assert.Empty(t, empty)
	assert.NoFileExists(t, path)
}

func TestConfigDeclaresTenants(t *testing.T) {
	dir := t.TempDir()
	nested := filepath.Join(dir, "a.yaml")
	flat := filepath.Join(dir, "b.yaml")
	none := filepath.Join(dir, "c.yaml")
	require.NoError(t, os.WriteFile(nested, []byte("camunda:\n  physical-tenants:\n    x: {}\n"), 0o644))
	require.NoError(t, os.WriteFile(flat, []byte("camunda.physical-tenants.x.foo: 1\n"), 0o644))
	require.NoError(t, os.WriteFile(none, []byte("camunda:\n  data: {}\n"), 0o644))
	assert.True(t, ConfigDeclaresTenants(nested))
	assert.True(t, ConfigDeclaresTenants(flat))
	assert.False(t, ConfigDeclaresTenants(none))
	assert.False(t, ConfigDeclaresTenants(filepath.Join(dir, "missing.yaml")))
}

func TestResolve(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), FileName))
	require.NoError(t, store.Add([]Tenant{{ID: "a"}, {ID: "b", Username: "bob", NoConnectors: true}, {ID: "c"}}, map[string]string{"b": "pw"}))

	busy := map[int]bool{8088: true}
	res, err := Resolve(ResolveInput{
		Store: store, DefaultUsername: "demo", DefaultPassword: "demo",
		ConnectorsEnabled: true, FirstConnectorsPort: 8087,
		PortFree: func(p int) bool { return !busy[p] },
	})
	require.NoError(t, err)
	assert.Equal(t, []types.PhysicalTenant{
		{ID: "a", Username: "demo", Password: "demo", Connectors: true, ConnectorsPort: 8087},
		{ID: "b", Username: "bob", Password: "pw"},
		{ID: "c", Username: "demo", Password: "demo", Connectors: true, ConnectorsPort: 8089},
	}, res.Tenants)

	// --physical-tenants replaces saved tenants for one run.
	res, err = Resolve(ResolveInput{Store: store, FlagIDs: []string{"z"}, DefaultUsername: "u", DefaultPassword: "p"})
	require.NoError(t, err)
	assert.Equal(t, []types.PhysicalTenant{{ID: "z", Username: "u", Password: "p"}}, res.Tenants)
}

func TestResolveDefersToUserConfig(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), FileName))
	require.NoError(t, store.Add([]Tenant{{ID: "a"}}, nil))
	cfg := filepath.Join(t.TempDir(), "app.yaml")
	require.NoError(t, os.WriteFile(cfg, []byte("camunda:\n  physical-tenants:\n    x: {}\n"), 0o644))

	res, err := Resolve(ResolveInput{Store: store, UserConfigs: []string{cfg}})
	require.NoError(t, err)
	assert.Empty(t, res.Tenants)
	require.Len(t, res.Notices, 1)
	assert.Contains(t, res.Notices[0], cfg)

	_, err = Resolve(ResolveInput{Store: store, UserConfigs: []string{cfg}, FlagIDs: []string{"b"}})
	assert.ErrorContains(t, err, "cannot be combined")
}

func TestResolveExternalModeIgnoresStore(t *testing.T) {
	t.Setenv(ModeEnv, "external")
	store := NewStore(filepath.Join(t.TempDir(), FileName))
	require.NoError(t, store.Add([]Tenant{{ID: "a"}}, nil))
	res, err := Resolve(ResolveInput{Store: store})
	require.NoError(t, err)
	assert.Empty(t, res.Tenants)
}

func TestEndpointsFor(t *testing.T) {
	assert.Equal(t, "http://localhost:8080/operate", EndpointsFor(DefaultID, "http", 8080).Operate)
	e := EndpointsFor("sales", "https", 9090)
	assert.Equal(t, "https://localhost:9090/physical-tenants/sales/operate", e.Operate)
	assert.Equal(t, "https://localhost:9090/physical-tenants/sales/v2/", e.REST)
}

func TestPrintSummary(t *testing.T) {
	var buf bytes.Buffer
	settings := types.C8RunSettings{Port: 8080, Username: "demo", PhysicalTenants: []types.PhysicalTenant{
		{ID: "sales", Username: "demo", Connectors: true, ConnectorsPort: 8087},
		{ID: "hr", Username: "alice"},
	}}
	PrintSummary(&buf, settings, []ProbeResult{{ID: "sales", Ready: true}, {ID: "hr", Err: "HTTP 404"}}, 8086)
	out := buf.String()
	assert.Contains(t, out, "http://localhost:8080/physical-tenants/sales/operate")
	assert.Contains(t, out, "http://localhost:8087/")
	assert.Contains(t, out, "NOT READY")
	assert.Contains(t, out, "hr did not become ready: HTTP 404")
	assert.Contains(t, out, "Camunda-Physical-Tenant: sales")
	assert.Contains(t, out, "secrets --tenant sales")

	buf.Reset()
	PrintSummary(&buf, types.C8RunSettings{}, nil, 8086)
	assert.Empty(t, buf.String())
}

func TestLastStartPort(t *testing.T) {
	base := t.TempDir()
	assert.Zero(t, LastStartPort(base))
	_, err := WriteGeneratedConfigForPort(base, []types.PhysicalTenant{{ID: "a"}}, "rdbms", 8090)
	require.NoError(t, err)
	assert.Equal(t, 8090, LastStartPort(base))
}

func TestStoreConcurrentAddsAreNotLost(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), FileName))
	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			id := fmt.Sprintf("t%d", i)
			assert.NoError(t, NewStore(store.Path()).Add([]Tenant{{ID: id, Username: "u"}}, map[string]string{id: "pw"}))
		}(i)
	}
	wg.Wait()
	tenants, err := store.List()
	require.NoError(t, err)
	assert.Len(t, tenants, 20)
	for _, tenant := range tenants {
		_, ok, err := store.Password(tenant.ID)
		require.NoError(t, err)
		assert.True(t, ok, tenant.ID)
	}
}

func TestStoreKeepsTenantsAndPasswordsInOneOwnerOnlyFile(t *testing.T) {
	dir := t.TempDir()
	store := NewStore(filepath.Join(dir, FileName))
	require.NoError(t, store.Add([]Tenant{{ID: "hr", Username: "alice"}}, map[string]string{"hr": "pw"}))
	entries, err := os.ReadDir(dir)
	require.NoError(t, err)
	var names []string
	for _, e := range entries {
		names = append(names, e.Name())
	}
	assert.ElementsMatch(t, []string{FileName, FileName + lockSuffix}, names, "one data file, so every change is one atomic rename")
	if runtime.GOOS != "windows" {
		info, err := os.Stat(store.Path())
		require.NoError(t, err)
		assert.Equal(t, os.FileMode(0o600), info.Mode().Perm())
	}

	// A rejected change writes nothing.
	before, err := os.ReadFile(store.Path())
	require.NoError(t, err)
	assert.Error(t, store.Remove([]string{"hr", "missing"}))
	after, err := os.ReadFile(store.Path())
	require.NoError(t, err)
	assert.Equal(t, before, after)
}

func TestStoreRejectsDuplicateIDs(t *testing.T) {
	path := filepath.Join(t.TempDir(), FileName)
	require.NoError(t, os.WriteFile(path, []byte("version: 1\ntenants:\n  - id: a\n  - id: a\n"), 0o644))
	_, err := NewStore(path).List()
	assert.ErrorContains(t, err, "more than once")
}

func TestSnapshotIsConsistent(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), FileName))
	require.NoError(t, store.Add([]Tenant{{ID: "hr", Username: "alice"}}, map[string]string{"hr": "pw"}))
	tenants, creds, err := store.Snapshot()
	require.NoError(t, err)
	assert.Len(t, tenants, 1)
	assert.Equal(t, "pw", creds["hr"])
}

func TestScrubTenantEnv(t *testing.T) {
	env := ScrubTenantEnv([]string{"PATH=/bin", "CAMUNDA_PHYSICALTENANTS_HR_SECURITY_INITIALIZATION_USERS_0_PASSWORD=x", "camunda_physicaltenants_a_b=y", "CAMUNDA_CLIENT_X=1"})
	assert.Equal(t, []string{"PATH=/bin", "CAMUNDA_CLIENT_X=1"}, env)
}

func TestProbeRunsTenantsConcurrentlyUnderOneDeadline(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer server.Close()
	u, _ := url.Parse(server.URL)
	port, _ := strconv.Atoi(u.Port())
	settings := types.C8RunSettings{Port: port}
	for _, id := range []string{"a", "b", "c", "d", "e"} {
		settings.PhysicalTenants = append(settings.PhysicalTenants, types.PhysicalTenant{ID: id})
	}
	start := time.Now()
	results := Probe(context.Background(), settings, 3, 100*time.Millisecond)
	elapsed := time.Since(start)
	require.Len(t, results, 5)
	for i, r := range results {
		assert.Equal(t, settings.PhysicalTenants[i].ID, r.ID)
		assert.False(t, r.Ready)
	}
	assert.Less(t, elapsed, 1200*time.Millisecond, "five failing tenants must share one deadline, not wait in sequence")
}

func TestSnapshotWithoutFileCreatesNothing(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "absent")
	tenants, creds, err := NewStore(filepath.Join(dir, FileName)).Snapshot()
	require.NoError(t, err)
	assert.Empty(t, tenants)
	assert.Empty(t, creds)
	assert.NoDirExists(t, dir)
}

func TestResolveSkipsReservedPorts(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), FileName))
	require.NoError(t, store.Add([]Tenant{{ID: "a"}, {ID: "b"}}, nil))
	res, err := Resolve(ResolveInput{
		Store: store, ConnectorsEnabled: true, FirstConnectorsPort: 8087,
		ReservedPorts: map[int]bool{8087: true},
		PortFree:      func(int) bool { return true },
	})
	require.NoError(t, err)
	assert.Equal(t, 8088, res.Tenants[0].ConnectorsPort, "the Camunda port must never be given to a connectors runtime")
	assert.Equal(t, 8089, res.Tenants[1].ConnectorsPort)
}

func TestResolveDefersToEnvironmentTenants(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), FileName))
	require.NoError(t, store.Add([]Tenant{{ID: "a"}}, nil))
	res, err := Resolve(ResolveInput{Store: store, EnvDeclaresTenants: true})
	require.NoError(t, err)
	assert.Empty(t, res.Tenants)
	require.Len(t, res.Notices, 1)
	_, err = Resolve(ResolveInput{Store: store, EnvDeclaresTenants: true, FlagIDs: []string{"b"}})
	assert.ErrorContains(t, err, "cannot be combined")
}

func TestApplyGeneratedConfig(t *testing.T) {
	base := t.TempDir()
	content, err := RenderForPort([]types.PhysicalTenant{{ID: "a"}}, "rdbms", 8090)
	require.NoError(t, err)
	require.NoError(t, ApplyGeneratedConfig(base, content))
	assert.Equal(t, 8090, LastStartPort(base))
	require.NoError(t, ApplyGeneratedConfig(base, nil))
	assert.NoFileExists(t, GeneratedConfigPath(base))
	empty, err := RenderForPort(nil, "rdbms", 8090)
	require.NoError(t, err)
	assert.Nil(t, empty)
}

func TestProbeTreatsAuthRejectionAsReady(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.Path, "/physical-tenants/missing/") {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer server.Close()
	u, _ := url.Parse(server.URL)
	port, _ := strconv.Atoi(u.Port())

	oidc := types.C8RunSettings{Port: port, OIDC: true, PhysicalTenants: []types.PhysicalTenant{{ID: "sales"}, {ID: "missing"}}}
	results := Probe(context.Background(), oidc, 2, 10*time.Millisecond)
	assert.True(t, results[0].Ready, "under OIDC a 401 still proves the tenant is up")
	assert.True(t, results[0].Unverified, "but storage readiness is not claimed")
	assert.Contains(t, results[0].Warning, "OIDC")
	assert.False(t, results[1].Ready, "an unknown tenant is a 404")

	basic := types.C8RunSettings{Port: port, PhysicalTenants: []types.PhysicalTenant{{ID: "sales", Username: "alice"}}}
	results = Probe(context.Background(), basic, 1, 10*time.Millisecond)
	assert.True(t, results[0].Ready)
	assert.True(t, results[0].Unverified)
	assert.Contains(t, results[0].Warning, "login for alice was rejected")

	var buf bytes.Buffer
	PrintSummary(&buf, basic, results, 8086)
	assert.Contains(t, buf.String(), "up (unverified)")
}

func TestResolveFailsWhenPortsAreExhausted(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), FileName))
	require.NoError(t, store.Add([]Tenant{{ID: "a"}, {ID: "b"}}, nil))
	_, err := Resolve(ResolveInput{
		Store: store, ConnectorsEnabled: true, FirstConnectorsPort: 65535,
		PortFree: func(int) bool { return true },
	})
	assert.ErrorContains(t, err, "no free port left")
}

func TestProbeWaitsForSecondaryStorage(t *testing.T) {
	calls := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, http.MethodPost, r.Method)
		assert.True(t, strings.HasSuffix(r.URL.Path, "/process-definitions/search"))
		calls++
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer server.Close()
	u, _ := url.Parse(server.URL)
	port, _ := strconv.Atoi(u.Port())
	results := Probe(context.Background(), types.C8RunSettings{Port: port, PhysicalTenants: []types.PhysicalTenant{{ID: "a"}}}, 2, 10*time.Millisecond)
	assert.False(t, results[0].Ready, "a tenant whose storage answers 503 is not ready")
	assert.Contains(t, results[0].Err, "secondary storage is not ready")
}

func TestRenderRejectsUnknownStorageType(t *testing.T) {
	_, err := Render([]types.PhysicalTenant{{ID: "a"}}, "${STORAGE_TYPE}")
	assert.ErrorContains(t, err, "cannot isolate physical tenants")
}
