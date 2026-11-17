package archive

import (
	"encoding/base64"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func shrinkRetryDelays(t *testing.T) {
	t.Helper()
	originalDelays := downloadRetryDelays
	downloadRetryDelays = []time.Duration{time.Millisecond, time.Millisecond}
	t.Cleanup(func() { downloadRetryDelays = originalDelays })
}

func TestDownloadFileWritesContentAtomically(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("payload"))
	}))
	defer server.Close()
	target := filepath.Join(t.TempDir(), "artifact.tar.gz")

	err := DownloadFile(target, server.URL, "")

	require.NoError(t, err)
	content, err := os.ReadFile(target)
	require.NoError(t, err)
	assert.Equal(t, "payload", string(content))
	assert.NoFileExists(t, target+".partial")
}

func TestDownloadFileRetriesTransientServerErrors(t *testing.T) {
	shrinkRetryDelays(t)
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		if requests.Add(1) < 3 {
			w.WriteHeader(http.StatusBadGateway)
			return
		}
		_, _ = w.Write([]byte("payload"))
	}))
	defer server.Close()
	target := filepath.Join(t.TempDir(), "artifact.tar.gz")

	err := DownloadFile(target, server.URL, "")

	require.NoError(t, err)
	assert.EqualValues(t, 3, requests.Load())
	content, err := os.ReadFile(target)
	require.NoError(t, err)
	assert.Equal(t, "payload", string(content))
	assert.NoFileExists(t, target+".partial")
}

func TestDownloadFileRetriesTruncatedBody(t *testing.T) {
	shrinkRetryDelays(t)
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		if requests.Add(1) == 1 {
			// Announce more bytes than are sent, then drop the connection mid-body —
			// the client sees an error during io.Copy, like a broken stream.
			w.Header().Set("Content-Length", "1024")
			_, _ = w.Write([]byte("trunc"))
			if flusher, ok := w.(http.Flusher); ok {
				flusher.Flush()
			}
			hijacker, ok := w.(http.Hijacker)
			require.True(t, ok)
			conn, _, err := hijacker.Hijack()
			require.NoError(t, err)
			_ = conn.Close()
			return
		}
		_, _ = w.Write([]byte("payload"))
	}))
	defer server.Close()
	target := filepath.Join(t.TempDir(), "artifact.tar.gz")

	err := DownloadFile(target, server.URL, "")

	require.NoError(t, err)
	assert.EqualValues(t, 2, requests.Load())
	content, err := os.ReadFile(target)
	require.NoError(t, err)
	assert.Equal(t, "payload", string(content))
	assert.NoFileExists(t, target+".partial")
}

func TestDownloadFileDoesNotRetryClientErrors(t *testing.T) {
	shrinkRetryDelays(t)
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		requests.Add(1)
		w.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()
	target := filepath.Join(t.TempDir(), "artifact.tar.gz")

	err := DownloadFile(target, server.URL, "")

	require.Error(t, err)
	assert.ErrorContains(t, err, "bad http status")
	assert.EqualValues(t, 1, requests.Load())
	assert.NoFileExists(t, target)
	assert.NoFileExists(t, target+".partial")
}

func TestDownloadFileGivesUpAfterRetryBudget(t *testing.T) {
	shrinkRetryDelays(t)
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		requests.Add(1)
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()
	target := filepath.Join(t.TempDir(), "artifact.tar.gz")

	err := DownloadFile(target, server.URL, "")

	require.Error(t, err)
	assert.EqualValues(t, len(downloadRetryDelays)+1, requests.Load())
	assert.NoFileExists(t, target)
	assert.NoFileExists(t, target+".partial")
}

func TestDownloadFileSkipsExistingNonEmptyFile(t *testing.T) {
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		requests.Add(1)
		_, _ = w.Write([]byte("fresh"))
	}))
	defer server.Close()
	target := filepath.Join(t.TempDir(), "artifact.tar.gz")
	require.NoError(t, os.WriteFile(target, []byte("existing"), 0o644))

	err := DownloadFile(target, server.URL, "")

	require.NoError(t, err)
	assert.EqualValues(t, 0, requests.Load())
	content, err := os.ReadFile(target)
	require.NoError(t, err)
	assert.Equal(t, "existing", string(content))
}

func TestDownloadFileReplacesExistingEmptyFile(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("payload"))
	}))
	defer server.Close()
	target := filepath.Join(t.TempDir(), "artifact.tar.gz")
	require.NoError(t, os.WriteFile(target, nil, 0o644))

	err := DownloadFile(target, server.URL, "")

	require.NoError(t, err)
	content, err := os.ReadFile(target)
	require.NoError(t, err)
	assert.Equal(t, "payload", string(content))
}

func TestDownloadFileSendsAuthorizationHeader(t *testing.T) {
	var gotAuth atomic.Value
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth.Store(r.Header.Get("Authorization"))
		_, _ = w.Write([]byte("payload"))
	}))
	defer server.Close()

	// Built at runtime so secret scanners do not mistake the literal for a real credential.
	basicAuth := "Basic " + base64.StdEncoding.EncodeToString([]byte("test-user:test-pass"))
	basicTarget := filepath.Join(t.TempDir(), "basic.tar.gz")
	require.NoError(t, DownloadFile(basicTarget, server.URL, basicAuth))
	assert.Equal(t, basicAuth, gotAuth.Load())

	bearerTarget := filepath.Join(t.TempDir(), "bearer.tar.gz")
	require.NoError(t, DownloadFile(bearerTarget, server.URL, "token123"))
	assert.Equal(t, "Bearer token123", gotAuth.Load())
}
