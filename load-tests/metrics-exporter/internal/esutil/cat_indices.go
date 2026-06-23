package esutil

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// IndexInfo holds the shard configuration for a single index as returned by /_cat/indices.
type IndexInfo struct {
	Index    string `json:"index"`
	Primary  string `json:"pri"`
	Replicas string `json:"rep"`
}

// CatIndices fetches per-index primary shard count and replica count via /_cat/indices.
func (c *Client) CatIndices(ctx context.Context) ([]IndexInfo, error) {
	endpoint := c.baseURL + "/_cat/indices?format=json&h=index,pri,rep"

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, fmt.Errorf("build cat-indices request: %w", err)
	}
	req.Header.Set("Accept", "application/json")

	res, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("elasticsearch cat-indices: %w", err)
	}
	defer res.Body.Close() //nolint:errcheck

	if res.StatusCode < 200 || res.StatusCode >= 300 {
		snippet, _ := io.ReadAll(io.LimitReader(res.Body, 512))
		return nil, fmt.Errorf("elasticsearch cat-indices: %s: %s", res.Status, strings.TrimSpace(string(snippet)))
	}

	body, err := io.ReadAll(res.Body)
	if err != nil {
		return nil, fmt.Errorf("read cat-indices response: %w", err)
	}

	var indices []IndexInfo
	if err := json.Unmarshal(body, &indices); err != nil {
		return nil, fmt.Errorf("parse cat-indices response: %w", err)
	}

	return indices, nil
}
