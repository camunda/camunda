//go:build integration

package integration_test

import (
	"bytes"
	"context"
	"embed"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
)

//go:embed fixtures
var fixtures embed.FS

type searchBackend struct {
	name  string
	image string
	env   map[string]string
}

// startSearchContainer starts an Elasticsearch or OpenSearch container and returns its base URL.
func startSearchContainer(t *testing.T, backend searchBackend) string {
	t.Helper()

	ctx := context.Background()
	req := testcontainers.ContainerRequest{
		Image:        backend.image,
		Env:          backend.env,
		ExposedPorts: []string{"9200/tcp"},
		WaitingFor: wait.ForHTTP("/_cluster/health").
			WithPort("9200/tcp").
			WithStartupTimeout(120 * time.Second),
	}

	container, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	if err != nil {
		t.Fatalf("start %s container: %v", backend.name, err)
	}
	t.Cleanup(func() {
		if err := container.Terminate(ctx); err != nil {
			t.Logf("terminate %s container: %v", backend.name, err)
		}
	})

	host, err := container.Host(ctx)
	if err != nil {
		t.Fatalf("get %s container host: %v", backend.name, err)
	}
	port, err := container.MappedPort(ctx, "9200/tcp")
	if err != nil {
		t.Fatalf("get %s container port: %v", backend.name, err)
	}

	return fmt.Sprintf("http://%s:%s", host, port.Port())
}

// createIndex creates an index with an explicit JSON mapping, ensuring field types
// are defined before fixtures are loaded. Without this, Elasticsearch's dynamic
// mapping would assign keyword string fields a "text" type, which breaks
// term queries (analyzed text does not match camelCase values exactly).
func createIndex(t *testing.T, baseURL, index, mapping string) {
	t.Helper()

	url := fmt.Sprintf("%s/%s", baseURL, index)
	req, err := http.NewRequestWithContext(context.Background(), http.MethodPut, url, strings.NewReader(mapping))
	if err != nil {
		t.Fatalf("build create-index request for %s: %v", index, err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("create index %s: %v", index, err)
	}
	defer resp.Body.Close() //nolint:errcheck

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		t.Fatalf("create index %s: status %s: %s", index, resp.Status, body)
	}
}

// loadFixtures bulk-indexes all documents from a fixture file into the given index,
// then refreshes the index so documents are immediately visible to searches.
// Fixture files use the Elasticsearch Bulk API ndjson format.
func loadFixtures(t *testing.T, baseURL, index, fixture string) {
	t.Helper()

	data, err := fixtures.ReadFile(fixture)
	if err != nil {
		t.Fatalf("read fixture %s: %v", fixture, err)
	}

	// POST the ndjson payload to the index-scoped bulk endpoint.
	bulkURL := fmt.Sprintf("%s/%s/_bulk", baseURL, index)
	req, err := http.NewRequestWithContext(context.Background(), http.MethodPost, bulkURL, bytes.NewReader(data))
	if err != nil {
		t.Fatalf("build bulk request for %s: %v", fixture, err)
	}
	req.Header.Set("Content-Type", "application/x-ndjson")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("bulk index %s: %v", fixture, err)
	}
	defer resp.Body.Close() //nolint:errcheck

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		t.Fatalf("bulk index %s: status %s: %s", fixture, resp.Status, body)
	}

	var result struct {
		Errors bool `json:"errors"`
	}
	if err := json.Unmarshal(body, &result); err != nil {
		t.Fatalf("parse bulk response for %s: %v", fixture, err)
	}
	if result.Errors {
		t.Fatalf("bulk index %s: one or more documents failed to index: %s", fixture, body)
	}

	// Force a refresh so documents are visible to the next search.
	refreshURL := fmt.Sprintf("%s/%s/_refresh", baseURL, index)
	refreshReq, err := http.NewRequestWithContext(context.Background(), http.MethodPost, refreshURL, nil)
	if err != nil {
		t.Fatalf("build refresh request for %s: %v", index, err)
	}
	refreshResp, err := http.DefaultClient.Do(refreshReq)
	if err != nil {
		t.Fatalf("refresh %s: %v", index, err)
	}
	defer refreshResp.Body.Close() //nolint:errcheck
	if refreshResp.StatusCode < 200 || refreshResp.StatusCode >= 300 {
		t.Fatalf("refresh %s: status %s", index, refreshResp.Status)
	}
}
