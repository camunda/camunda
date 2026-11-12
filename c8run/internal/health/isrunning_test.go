package health

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestIsRunningDetectsHealthyServiceBeforeTheNextRetry(t *testing.T) {
	var healthy atomic.Bool
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		if healthy.Load() {
			w.WriteHeader(http.StatusOK)
			return
		}
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer server.Close()
	time.AfterFunc(300*time.Millisecond, func() { healthy.Store(true) })

	start := time.Now()
	ok := isRunning(context.Background(), "test", server.URL, 2, 14*time.Second)
	elapsed := time.Since(start)

	if !ok {
		t.Fatal("expected service to be detected as running")
	}
	if elapsed > 5*time.Second {
		t.Fatalf("expected detection well before the 14s retry delay, took %s", elapsed)
	}
}

func TestIsRunningGivesUpAfterRetriesTimesDelay(t *testing.T) {
	var probes atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		probes.Add(1)
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer server.Close()
	original := healthPollInterval
	healthPollInterval = 20 * time.Millisecond
	defer func() { healthPollInterval = original }()

	start := time.Now()
	ok := isRunning(context.Background(), "test", server.URL, 2, 200*time.Millisecond)
	elapsed := time.Since(start)

	if ok {
		t.Fatal("expected service to be reported as not running")
	}
	if elapsed < 400*time.Millisecond || elapsed > 2*time.Second {
		t.Fatalf("expected to wait about retries*delay (400ms), took %s", elapsed)
	}
	if probes.Load() <= 3 {
		t.Fatalf("expected probing between retries, got %d probes", probes.Load())
	}
}
