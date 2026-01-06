package start

import (
	"net"
	"testing"
	"time"
)

func TestEnsurePortAvailable(t *testing.T) {
	inUseListener, err := net.Listen("tcp4", ":0")
	if err != nil {
		t.Fatalf("failed to get ephemeral listener: %v", err)
	}

	port := inUseListener.Addr().(*net.TCPAddr).Port

	if err := ensurePortAvailable(port); err == nil {
		t.Fatalf("expected error when port %d is already bound", port)
	}

	if err := inUseListener.Close(); err != nil {
		t.Fatalf("failed to close temporary listener: %v", err)
	}
	time.Sleep(25 * time.Millisecond) // give the OS a moment to release the port

	if err := ensurePortAvailable(port); err != nil {
		t.Fatalf("expected port %d to be reported as available: %v", port, err)
	}
}
