package utils

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/esutil"
)

func TestCountCompleted_ParsesTotalAndCompleted(t *testing.T) {
	var gotBody []byte
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotBody, _ = io.ReadAll(r.Body)
		_, _ = w.Write([]byte(`{"hits":{"total":{"value":42}},"aggregations":{"completed":{"doc_count":10}}}`))
	}))
	t.Cleanup(srv.Close)

	res, err := CountCompleted(context.Background(), esutil.NewClient(srv.URL), "operate-list-view-*", `{"match_all":{}}`)
	if err != nil {
		t.Fatalf("CountCompleted returned error: %v", err)
	}
	if res.Total != 42 {
		t.Errorf("Total = %d, want 42", res.Total)
	}
	if res.Completed != 10 {
		t.Errorf("Completed = %d, want 10", res.Completed)
	}

	// The request body must be a valid size:0 _search with track_total_hits,
	// the caller's query, and the completed filter aggregation.
	var req struct {
		Size           int             `json:"size"`
		TrackTotalHits bool            `json:"track_total_hits"`
		Query          json.RawMessage `json:"query"`
		Aggs           json.RawMessage `json:"aggs"`
	}
	if err := json.Unmarshal(gotBody, &req); err != nil {
		t.Fatalf("request body is not valid JSON: %v (%s)", err, gotBody)
	}
	if req.Size != 0 {
		t.Errorf("size = %d, want 0", req.Size)
	}
	if !req.TrackTotalHits {
		t.Error("track_total_hits = false, want true")
	}
	if string(req.Query) != `{"match_all":{}}` {
		t.Errorf("query = %s, want the caller's clause verbatim", req.Query)
	}
	if !strings.Contains(string(req.Aggs), "completed") {
		t.Errorf("aggs missing the completed aggregation: %s", req.Aggs)
	}
}

func TestCountCompleted_TimedOut(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"timed_out":true,"hits":{"total":{"value":0}},"aggregations":{"completed":{"doc_count":0}}}`))
	}))
	t.Cleanup(srv.Close)

	if _, err := CountCompleted(context.Background(), esutil.NewClient(srv.URL), "idx", `{"match_all":{}}`); err == nil {
		t.Fatal("expected error on timed_out response, got nil")
	}
}

func TestCountCompleted_FailedShards(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"_shards":{"total":5,"successful":3,"failed":2},"hits":{"total":{"value":10}},"aggregations":{"completed":{"doc_count":3}}}`))
	}))
	t.Cleanup(srv.Close)

	if _, err := CountCompleted(context.Background(), esutil.NewClient(srv.URL), "idx", `{"match_all":{}}`); err == nil {
		t.Fatal("expected error on partial shard failure, got nil")
	}
}

func TestCountCompleted_HTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, `{"error":"boom"}`, http.StatusInternalServerError)
	}))
	t.Cleanup(srv.Close)

	if _, err := CountCompleted(context.Background(), esutil.NewClient(srv.URL), "idx", `{"match_all":{}}`); err == nil {
		t.Fatal("expected error on 500 response, got nil")
	}
}
