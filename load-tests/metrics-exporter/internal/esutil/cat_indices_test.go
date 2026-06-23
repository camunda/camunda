package esutil

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestCatIndices_shouldReturnIndexInfo(t *testing.T) {
	// given
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[{"index":"test-index","pri":"3","rep":"1"},{"index":"another-index","pri":"1","rep":"0"}]`))
	}))
	t.Cleanup(srv.Close)

	c := NewClient(srv.URL)

	// when
	indices, err := c.CatIndices(context.Background())

	// then
	if err != nil {
		t.Fatalf("CatIndices returned error: %v", err)
	}
	if len(indices) != 2 {
		t.Fatalf("expected 2 indices, got %d", len(indices))
	}
	if indices[0].Index != "test-index" || indices[0].Primary != "3" || indices[0].Replicas != "1" {
		t.Errorf("indices[0] = %+v, want {Index:test-index Primary:3 Replicas:1}", indices[0])
	}
	if indices[1].Index != "another-index" || indices[1].Primary != "1" || indices[1].Replicas != "0" {
		t.Errorf("indices[1] = %+v, want {Index:another-index Primary:1 Replicas:0}", indices[1])
	}
}

func TestCatIndices_shouldReturnEmptySliceOnNoIndices(t *testing.T) {
	// given
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[]`))
	}))
	t.Cleanup(srv.Close)

	c := NewClient(srv.URL)

	// when
	indices, err := c.CatIndices(context.Background())

	// then
	if err != nil {
		t.Fatalf("CatIndices returned error: %v", err)
	}
	if len(indices) != 0 {
		t.Errorf("expected 0 indices, got %d", len(indices))
	}
}

func TestCatIndices_shouldReturnErrorOnHTTPFailure(t *testing.T) {
	// given
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"error":"internal server error"}`))
	}))
	t.Cleanup(srv.Close)

	c := NewClient(srv.URL)

	// when
	_, err := c.CatIndices(context.Background())

	// then
	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestCatIndices_shouldSendRequestToCorrectEndpoint(t *testing.T) {
	// given
	var gotPath, gotQuery string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotQuery = r.URL.RawQuery
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`[]`))
	}))
	t.Cleanup(srv.Close)

	c := NewClient(srv.URL)

	// when
	_, _ = c.CatIndices(context.Background())

	// then
	if gotPath != "/_cat/indices" {
		t.Errorf("request path = %q, want /_cat/indices", gotPath)
	}
	if gotQuery != "format=json&h=index,pri,rep" {
		t.Errorf("request query = %q, want format=json&h=index,pri,rep", gotQuery)
	}
}
