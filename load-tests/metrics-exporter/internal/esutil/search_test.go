package esutil

import (
	"context"
	"io"
	"net/http"
	"strings"
	"testing"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

func newMockClient(rt roundTripFunc) *Client {
	c := NewClient("http://es.test")
	c.httpClient.Transport = rt
	return c
}

func TestSearch_RequestShape(t *testing.T) {
	var gotReq *http.Request
	var gotBody []byte
	c := newMockClient(func(r *http.Request) (*http.Response, error) {
		gotReq = r
		gotBody, _ = io.ReadAll(r.Body)
		return &http.Response{
			StatusCode: 200,
			Body:       io.NopCloser(strings.NewReader(`{"ok":true}`)),
		}, nil
	})

	const reqBody = `{"size":0,"query":{"match_all":{}}}`
	resp, err := c.Search(context.Background(), "operate-list-view-*", []byte(reqBody))
	if err != nil {
		t.Fatalf("Search returned error: %v", err)
	}
	if string(resp) != `{"ok":true}` {
		t.Errorf("response = %q, want the upstream body verbatim", resp)
	}
	if gotReq.Method != http.MethodPost {
		t.Errorf("method = %q, want POST", gotReq.Method)
	}
	if !strings.HasSuffix(gotReq.URL.Path, "/_search") {
		t.Errorf("path = %q, want it to end with /_search", gotReq.URL.Path)
	}
	if got := gotReq.Header.Get("Content-Type"); got != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", got)
	}
	if string(gotBody) != reqBody {
		t.Errorf("forwarded body = %q, want %q", gotBody, reqBody)
	}
}

func TestSearch_HTTPError(t *testing.T) {
	c := newMockClient(func(*http.Request) (*http.Response, error) {
		return &http.Response{
			StatusCode: 500,
			Status:     "500 Internal Server Error",
			Body:       io.NopCloser(strings.NewReader(`{"error":"boom"}`)),
		}, nil
	})
	if _, err := c.Search(context.Background(), "idx", []byte(`{}`)); err == nil {
		t.Fatal("expected error on 500 response, got nil")
	}
}

func TestSearch_WildcardIndexNotPercentEncoded(t *testing.T) {
	var gotReq *http.Request
	c := newMockClient(func(r *http.Request) (*http.Response, error) {
		gotReq = r
		return &http.Response{
			StatusCode: 200,
			Body:       io.NopCloser(strings.NewReader(`{}`)),
		}, nil
	})

	if _, err := c.Search(context.Background(), "operate-list-view-*", []byte(`{}`)); err != nil {
		t.Fatalf("Search returned error: %v", err)
	}

	// The wildcard * must reach Elasticsearch as a literal *, not as %2A.
	// url.PathEscape encodes * to %2A; backends that do not decode before routing
	// would treat it as a literal index name rather than a wildcard pattern.
	uri := gotReq.URL.RequestURI()
	if strings.Contains(uri, "%2A") {
		t.Errorf("request URI %q contains %%2A: wildcard * was percent-encoded and will not match any index", uri)
	}
}

func TestSearch_PropagatesContextCancel(t *testing.T) {
	c := newMockClient(func(r *http.Request) (*http.Response, error) {
		<-r.Context().Done()
		return nil, r.Context().Err()
	})

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	if _, err := c.Search(ctx, "idx", []byte(`{}`)); err == nil {
		t.Fatal("expected error from cancelled context, got nil")
	}
}
