// Package testutil provides shared helpers for testing the metrics-exporter collectors.
package testutil

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

// NewSearchServer starts an httptest.Server that returns the given body and HTTP status code.
// It also validates that every request body is valid JSON.
func NewSearchServer(t *testing.T, body string, status int) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		reqBody, _ := io.ReadAll(r.Body)
		if !json.Valid(reqBody) {
			t.Errorf("request body is not valid JSON: %s", reqBody)
		}
		w.WriteHeader(status)
		_, _ = w.Write([]byte(body))
	}))
	t.Cleanup(srv.Close)
	return srv
}
