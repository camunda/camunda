//go:build linux || darwin

package unix

import (
	"context"
	"strings"
	"testing"
)

func TestConnectorsCmdWithCustomPort(t *testing.T) {
	tests := []struct {
		name         string
		camundaPort  int
		expectedAddr string
	}{
		{
			name:         "default port",
			camundaPort:  8080,
			expectedAddr: "CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS=http://localhost:8080",
		},
		{
			name:         "custom port 9000",
			camundaPort:  9000,
			expectedAddr: "CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS=http://localhost:9000",
		},
		{
			name:         "custom port 8888",
			camundaPort:  8888,
			expectedAddr: "CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS=http://localhost:8888",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := context.Background()
			unixC8Run := &UnixC8Run{}

			cmd := unixC8Run.ConnectorsCmd(
				ctx,
				"java",
				"/test/parent/dir",
				"8.8.1",
				tt.camundaPort,
				8086,
			)

			// Check that the environment variable is set correctly
			found := false
			for _, env := range cmd.Env {
				if strings.Contains(env, "CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS=") {
					if env != tt.expectedAddr {
						t.Errorf("Expected environment variable %q, got %q", tt.expectedAddr, env)
					}
					found = true
					break
				}
			}

			if !found {
				t.Errorf("CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS environment variable not found in command environment")
			}

			// Verify the command structure - cmd.Path will be resolved by exec.CommandContext
			// so we check cmd.Args[0] instead which is the actual command
			if !strings.Contains(cmd.Args[0], "java") {
				t.Errorf("Expected command to contain 'java', got %q", cmd.Args[0])
			}

			// Check that the classpath includes custom_connectors
			args := strings.Join(cmd.Args, " ")
			if !strings.Contains(args, "custom_connectors") {
				t.Errorf("Expected classpath to include 'custom_connectors', got: %s", args)
			}

			// Check that the main class is correct
			if !strings.Contains(args, "io.camunda.connector.runtime.app.ConnectorRuntimeApplication") {
				t.Errorf("Expected main class 'io.camunda.connector.runtime.app.ConnectorRuntimeApplication', got: %s", args)
			}

			// Check that the spring config location is included
			if !strings.Contains(args, "--spring.config.additional-location=") {
				t.Errorf("Expected spring config location argument, got: %s", args)
			}
		})
	}
}

func TestConnectorsCmdPortNotInArgs(t *testing.T) {
	ctx := context.Background()
	unixC8Run := &UnixC8Run{}

	cmd := unixC8Run.ConnectorsCmd(
		ctx,
		"java",
		"/test/parent/dir",
		"8.8.1",
		9000,
		8086,
	)

	// The port should be in the environment variable, not in the command arguments
	args := strings.Join(cmd.Args, " ")
	if strings.Contains(args, "9000") {
		t.Errorf("Port should not be in command arguments, only in environment variable. Args: %s", args)
	}
}

func TestConnectorsCmdRespectsExistingZeebeRestEnv(t *testing.T) {
	ctx := context.Background()
	unixC8Run := &UnixC8Run{}

	customAddress := "https://external-host:9443"
	t.Setenv("CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS", customAddress)

	cmd := unixC8Run.ConnectorsCmd(
		ctx,
		"java",
		"/test/parent/dir",
		"8.8.1",
		9000,
		8086,
	)

	if cmd.Env != nil {
		for _, env := range cmd.Env {
			if strings.Contains(env, "CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS=") {
				t.Fatalf("expected existing CAMUNDA_CLIENT_ZEEBE_REST_ADDRESS to be preserved, but command overrides it: %s", env)
			}
		}
	}
}

func TestConnectorsCmdUsesPropertiesLauncherWhenVersionRequiresIt(t *testing.T) {
	ctx := context.Background()
	unixC8Run := &UnixC8Run{}

	cmd := unixC8Run.ConnectorsCmd(
		ctx,
		"java",
		"/test/parent/dir",
		"8.9.0",
		8080,
		8086,
	)

	args := strings.Join(cmd.Args, " ")
	if !strings.Contains(args, "org.springframework.boot.loader.launch.PropertiesLauncher") {
		t.Fatalf("expected PropertiesLauncher to be used when env var enabled, got args: %s", args)
	}
	if strings.Contains(args, "io.camunda.connector.runtime.app.ConnectorRuntimeApplication") {
		t.Fatalf("did not expect legacy main class when PropertiesLauncher is enabled")
	}
}

func TestConnectorsCmdInjectsCustomConnectorsPort(t *testing.T) {
	ctx := context.Background()
	unixC8Run := &UnixC8Run{}

	// when a non-default connectors port is provided
	cmd := unixC8Run.ConnectorsCmd(ctx, "java", "/test/parent/dir", "8.8.1", 8080, 9000)

	// then --server.port is passed as a Spring Boot arg
	args := strings.Join(cmd.Args, " ")
	if !strings.Contains(args, "--server.port=9000") {
		t.Fatalf("expected --server.port=9000 in args when custom connectors port is set, got: %s", args)
	}
}

func TestConnectorsCmdDoesNotInjectDefaultConnectorsPort(t *testing.T) {
	ctx := context.Background()
	unixC8Run := &UnixC8Run{}

	// when the default connectors port is used
	cmd := unixC8Run.ConnectorsCmd(ctx, "java", "/test/parent/dir", "8.8.1", 8080, 8086)

	// then --server.port is not injected (connectors-application.properties already sets it)
	args := strings.Join(cmd.Args, " ")
	if strings.Contains(args, "--server.port=") {
		t.Fatalf("expected no --server.port arg for default port, got: %s", args)
	}
}
