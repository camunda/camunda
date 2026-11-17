// Package esutil provides shared Elasticsearch query helpers used by the
// per-product metric packages (optimize, operate, ...).
package esutil

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// Search runs a _search request against the given index pattern with the
// provided request body and returns the raw response body. It only handles the
// HTTP boilerplate (method, headers, status checks); callers are responsible for
// building the request body and parsing the response.
func (c *Client) Search(ctx context.Context, index string, body []byte) ([]byte, error) {
	endpoint := fmt.Sprintf("%s/%s/_search", c.baseURL, index)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("build search request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	res, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("elasticsearch search %s: %w", index, err)
	}
	defer res.Body.Close() //nolint:errcheck

	if res.StatusCode < 200 || res.StatusCode >= 300 {
		snippet, _ := io.ReadAll(io.LimitReader(res.Body, 512))
		return nil, fmt.Errorf("elasticsearch %s: %s: %s", index, res.Status, strings.TrimSpace(string(snippet)))
	}

	respBody, err := io.ReadAll(res.Body)
	if err != nil {
		return nil, fmt.Errorf("read search response: %w", err)
	}
	return respBody, nil
}
