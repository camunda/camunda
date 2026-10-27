// Package esutil provides shared Elasticsearch query helpers used by the
// per-product metric packages (optimize, operate, ...).
package esutil

import (
	"net/http"
	"strings"
	"time"
)

// The client information to connect to Elasticsearch
type Client struct {
	httpClient *http.Client
	baseURL    string
}

func NewClient(baseURL string) *Client {
	return &Client{
		httpClient: &http.Client{Timeout: 30 * time.Second},
		baseURL:    strings.TrimRight(baseURL, "/"),
	}
}
