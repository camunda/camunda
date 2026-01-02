package deployer

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestResolveScenarioFiles(t *testing.T) {
	// Create a temporary chart directory structure
	tmpDir := t.TempDir()
	chartPath := filepath.Join(tmpDir, "test-chart")
	scenarioBase := filepath.Join(chartPath, "test", "integration", "scenarios", "chart-full-setup")
	
	if err := os.MkdirAll(scenarioBase, 0755); err != nil {
		t.Fatalf("failed to create test directory: %v", err)
	}

	// Create test scenario files
	scenarios := map[string]string{
		"basic":      "values-integration-test-ingress-basic.yaml",
		"keycloak":   "values-integration-test-ingress-keycloak.yaml",
		"opensearch": "values-integration-test-ingress-opensearch.yaml",
	}

	for _, filename := range scenarios {
		path := filepath.Join(scenarioBase, filename)
		if err := os.WriteFile(path, []byte("# test values"), 0644); err != nil {
			t.Fatalf("failed to create test file %s: %v", filename, err)
		}
	}

	tests := []struct {
		name        string
		scenarioDir string
		scenarios   []string
		want        []string
		wantErr     bool
	}{
		{
			name:        "single scenario",
			scenarioDir: scenarioBase,
			scenarios:   []string{"basic"},
			want: []string{
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
			},
			wantErr: false,
		},
		{
			name:        "multiple scenarios",
			scenarioDir: scenarioBase,
			scenarios:   []string{"basic", "keycloak", "opensearch"},
			want: []string{
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
				filepath.Join(scenarioBase, "values-integration-test-ingress-keycloak.yaml"),
				filepath.Join(scenarioBase, "values-integration-test-ingress-opensearch.yaml"),
			},
			wantErr: false,
		},
		{
			name:        "empty scenarios list",
			scenarioDir: scenarioBase,
			scenarios:   []string{},
			want:        nil,
			wantErr:     false,
		},
		{
			name:        "nil scenarios list",
			scenarioDir: scenarioBase,
			scenarios:   nil,
			want:        nil,
			wantErr:     false,
		},
		{
			name:        "scenario with whitespace",
			scenarioDir: scenarioBase,
			scenarios:   []string{"  basic  "},
			want: []string{
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
			},
			wantErr: false,
		},
		{
			name:        "scenario with empty strings",
			scenarioDir: scenarioBase,
			scenarios:   []string{"", "basic", "", "keycloak", ""},
			want: []string{
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
				filepath.Join(scenarioBase, "values-integration-test-ingress-keycloak.yaml"),
			},
			wantErr: false,
		},
		{
			name:        "missing scenario",
			scenarioDir: scenarioBase,
			scenarios:   []string{"nonexistent"},
			want:        nil,
			wantErr:     true,
		},
		{
			name:        "one valid one missing",
			scenarioDir: scenarioBase,
			scenarios:   []string{"basic", "nonexistent"},
			want:        nil,
			wantErr:     true,
		},
		{
			name:        "multiple missing",
			scenarioDir: scenarioBase,
			scenarios:   []string{"missing1", "missing2"},
			want:        nil,
			wantErr:     true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ResolveScenarioFiles(tt.scenarioDir, tt.scenarios)
			if (err != nil) != tt.wantErr {
				t.Errorf("ResolveScenarioFiles() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("ResolveScenarioFiles() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestResolveScenarioFiles_ErrorMessage(t *testing.T) {
	tmpDir := t.TempDir()
	scenarioDir := filepath.Join(tmpDir, "scenarios")
	
	if err := os.MkdirAll(scenarioDir, 0755); err != nil {
		t.Fatalf("failed to create test directory: %v", err)
	}

	// Create only one scenario
	path := filepath.Join(scenarioDir, "values-integration-test-ingress-basic.yaml")
	if err := os.WriteFile(path, []byte("# test"), 0644); err != nil {
		t.Fatalf("failed to create test file: %v", err)
	}

	// Try to resolve two scenarios, one missing
	_, err := ResolveScenarioFiles(scenarioDir, []string{"basic", "missing1", "missing2"})
	if err == nil {
		t.Fatal("expected error, got nil")
	}

	// Check that error message contains the missing scenario names
	errMsg := err.Error()
	if !contains(errMsg, "missing1") || !contains(errMsg, "missing2") {
		t.Errorf("error message should contain missing scenario names, got: %s", errMsg)
	}
}

// Helper function
func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(s) > len(substr) && 
		(s[0:len(substr)] == substr || s[len(s)-len(substr):] == substr || 
		anySubstring(s, substr)))
}

func anySubstring(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

