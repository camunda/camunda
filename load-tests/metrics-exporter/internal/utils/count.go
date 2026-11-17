// Package utils provides higher-level Elasticsearch query helpers, built on top
// of the esutil transport client, that are shared across the per-product metric
// collectors (operate, optimize, ...).
package utils

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/camunda/camunda/load-tests/metrics-exporter/internal/esutil"
)

type CompletedDocumentsResponse struct {
	Completed int64
	Total     int64
}

// completedDocumentsRequest is the body of the size:0 _search issued by
// CountCompleted: it returns the total document count (track_total_hits) for the
// caller-provided query, plus the "completed" subset (documents that have an
// endDate) as a filter aggregation.
type completedDocumentsRequest struct {
	Size           int             `json:"size"`
	TrackTotalHits bool            `json:"track_total_hits"`
	Query          json.RawMessage `json:"query"`
	Aggs           json.RawMessage `json:"aggs"`
}

// CountCompleted runs a single size:0 _search against the given index pattern
// and returns both the total number of documents matching query and the subset
// of those that have an endDate field ("completed"), in one request.
func CountCompleted(ctx context.Context, client *esutil.Client, index string, query string) (*CompletedDocumentsResponse, error) {
	body, err := json.Marshal(completedDocumentsRequest{
		// size=0: return no documents, only the counts
		//   See: https://www.elastic.co/docs/explore-analyze/query-filter/aggregations#return-only-agg-results
		Size: 0,

		// track_total_hits=true: return the total number of hits instead of maximizing up to 10000 hits
		//   See: https://www.elastic.co/docs/solutions/search/the-search-api#track-total-hits
		TrackTotalHits: true,
		Query:          json.RawMessage(query),

		// Count the number of completed documents with a "endDate" field.
		Aggs: json.RawMessage(`{"completed": {"filter": {"exists": {"field": "endDate"}}}}`),
	})
	if err != nil {
		return nil, fmt.Errorf("build count completed request: %w", err)
	}

	raw, err := client.Search(ctx, index, body)
	if err != nil {
		return nil, fmt.Errorf("error while counting completed documents: %w", err)
	}

	var res struct {
		TimedOut bool `json:"timed_out"`
		Shards   struct {
			Failed int `json:"failed"`
		} `json:"_shards"`
		Hits struct {
			Total struct {
				Value int64 `json:"value"`
			} `json:"total"`
		} `json:"hits"`
		Aggregations struct {
			Completed struct {
				DocCount int64 `json:"doc_count"`
			} `json:"completed"`
		} `json:"aggregations"`
	}
	if err := json.Unmarshal(raw, &res); err != nil {
		return nil, fmt.Errorf("decode count completed documents: %w", err)
	}
	if res.TimedOut {
		return nil, fmt.Errorf("elasticsearch query timed out on %s", index)
	}
	if res.Shards.Failed > 0 {
		return nil, fmt.Errorf("elasticsearch returned %d failed shards on %s", res.Shards.Failed, index)
	}

	return &CompletedDocumentsResponse{
		Completed: res.Aggregations.Completed.DocCount,
		Total:     res.Hits.Total.Value,
	}, nil
}
