package deployer

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestBuildValuesList(t *testing.T) {
	// Create a temporary chart directory structure for testing
	tmpDir := t.TempDir()
	chartPath := filepath.Join(tmpDir, "test-chart")
	scenariosRoot := filepath.Join(chartPath, "test", "integration", "scenarios")
	scenarioBase := filepath.Join(scenariosRoot, "chart-full-setup")
	commonDir := filepath.Join(scenariosRoot, "common")

	if err := os.MkdirAll(scenarioBase, 0755); err != nil {
		t.Fatalf("failed to create test directory: %v", err)
	}
	if err := os.MkdirAll(commonDir, 0755); err != nil {
		t.Fatalf("failed to create common directory: %v", err)
	}

	// Create common values files
	commonIntegrationTest := filepath.Join(commonDir, "values-integration-test.yaml")
	if err := os.WriteFile(commonIntegrationTest, []byte("# common integration test"), 0644); err != nil {
		t.Fatalf("failed to create common integration test file: %v", err)
	}
	commonPullSecrets := filepath.Join(commonDir, "values-integration-test-pull-secrets.yaml")
	if err := os.WriteFile(commonPullSecrets, []byte("# common pull secrets"), 0644); err != nil {
		t.Fatalf("failed to create common pull secrets file: %v", err)
	}

	// Create test scenario files
	scenarios := []string{"basic", "keycloak", "opensearch"}
	for _, s := range scenarios {
		filename := filepath.Join(scenarioBase, "values-integration-test-ingress-"+s+".yaml")
		if err := os.WriteFile(filename, []byte("# test values"), 0644); err != nil {
			t.Fatalf("failed to create test file: %v", err)
		}
	}

	// Create optional overlay files
	enterpriseFile := filepath.Join(scenarioBase, "values-enterprise.yaml")
	if err := os.WriteFile(enterpriseFile, []byte("# enterprise"), 0644); err != nil {
		t.Fatalf("failed to create enterprise file: %v", err)
	}

	digestFile := filepath.Join(scenarioBase, "values-digest.yaml")
	if err := os.WriteFile(digestFile, []byte("# digest"), 0644); err != nil {
		t.Fatalf("failed to create digest file: %v", err)
	}

	tests := []struct {
		name              string
		scenarioDir       string
		scenarios         []string
		auth              string
		includeEnterprise bool
		includeDigest     bool
		userValues        []string
		commonFiles       []string // pre-processed common files (nil = discover from ../common/)
		want              []string
		wantErr           bool
	}{
		{
			name:        "single scenario no auth",
			scenarioDir: scenarioBase,
			scenarios:   []string{"basic"},
			auth:        "",
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
			},
			wantErr: false,
		},
		{
			name:        "scenario with auth",
			scenarioDir: scenarioBase,
			scenarios:   []string{"basic"},
			auth:        "keycloak",
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				filepath.Join(scenarioBase, "values-integration-test-ingress-keycloak.yaml"),
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
			},
			wantErr: false,
		},
		{
			name:        "multiple scenarios",
			scenarioDir: scenarioBase,
			scenarios:   []string{"basic", "opensearch"},
			auth:        "",
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
				filepath.Join(scenarioBase, "values-integration-test-ingress-opensearch.yaml"),
			},
			wantErr: false,
		},
		{
			name:              "with enterprise overlay",
			scenarioDir:       scenarioBase,
			scenarios:         []string{"basic"},
			auth:              "",
			includeEnterprise: true,
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
				enterpriseFile,
			},
			wantErr: false,
		},
		{
			name:          "with digest overlay",
			scenarioDir:   scenarioBase,
			scenarios:     []string{"basic"},
			auth:          "",
			includeDigest: true,
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
				digestFile,
			},
			wantErr: false,
		},
		{
			name:        "with user values",
			scenarioDir: scenarioBase,
			scenarios:   []string{"basic"},
			auth:        "",
			userValues: []string{
				"/custom/values1.yaml",
				"/custom/values2.yaml",
			},
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
				"/custom/values1.yaml",
				"/custom/values2.yaml",
			},
			wantErr: false,
		},
		{
			name:              "full layering: common + auth + scenarios + overlays + user",
			scenarioDir:       scenarioBase,
			scenarios:         []string{"basic", "opensearch"},
			auth:              "keycloak",
			includeEnterprise: true,
			includeDigest:     true,
			userValues: []string{
				"/custom/override.yaml",
			},
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				filepath.Join(scenarioBase, "values-integration-test-ingress-keycloak.yaml"),
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
				filepath.Join(scenarioBase, "values-integration-test-ingress-opensearch.yaml"),
				enterpriseFile,
				digestFile,
				"/custom/override.yaml",
			},
			wantErr: false,
		},
		{
			name:        "empty scenarios list with common values",
			scenarioDir: scenarioBase,
			scenarios:   []string{},
			auth:        "",
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
			},
			wantErr: false,
		},
		{
			name:        "scenario with whitespace",
			scenarioDir: scenarioBase,
			scenarios:   []string{"  basic  ", ""},
			auth:        "",
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
			},
			wantErr: false,
		},
		{
			name:        "auth with whitespace",
			scenarioDir: scenarioBase,
			scenarios:   []string{"basic"},
			auth:        "  keycloak  ",
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				filepath.Join(scenarioBase, "values-integration-test-ingress-keycloak.yaml"),
				filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml"),
			},
			wantErr: false,
		},
		{
			name:        "missing scenario file",
			scenarioDir: scenarioBase,
			scenarios:   []string{"nonexistent"},
			auth:        "",
			want:        nil,
			wantErr:     true,
		},
		{
			name:        "missing auth scenario file",
			scenarioDir: scenarioBase,
			scenarios:   []string{"basic"},
			auth:        "nonexistent-auth",
			want:        nil,
			wantErr:     true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := BuildValuesList(
				tt.scenarioDir,
				tt.scenarios,
				tt.auth,
				tt.includeEnterprise,
				tt.includeDigest,
				tt.userValues,
				tt.commonFiles,
			)
			if (err != nil) != tt.wantErr {
				t.Errorf("BuildValuesList() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			// Treat nil and empty slice as equivalent
			if !slicesEqual(got, tt.want) {
				t.Errorf("BuildValuesList() got:\n%v\n\nwant:\n%v", got, tt.want)
			}
		})
	}
}

// slicesEqual compares two slices, treating nil and empty slices as equivalent
func slicesEqual(a, b []string) bool {
	if len(a) == 0 && len(b) == 0 {
		return true
	}
	return reflect.DeepEqual(a, b)
}

func TestBuildValuesListNoCommonDir(t *testing.T) {
	// Test behavior when common directory doesn't exist (backward compatibility)
	tmpDir := t.TempDir()
	scenarioBase := filepath.Join(tmpDir, "scenarios", "chart-full-setup")
	// Note: no common directory created

	if err := os.MkdirAll(scenarioBase, 0755); err != nil {
		t.Fatalf("failed to create test directory: %v", err)
	}

	// Create test scenario file
	basicFile := filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml")
	if err := os.WriteFile(basicFile, []byte("# test values"), 0644); err != nil {
		t.Fatalf("failed to create test file: %v", err)
	}

	// Pass nil for commonFiles to trigger discovery (which should find nothing)
	got, err := BuildValuesList(scenarioBase, []string{"basic"}, "", false, false, nil, nil)
	if err != nil {
		t.Errorf("BuildValuesList() unexpected error = %v", err)
		return
	}

	// Should only have the scenario file, no common files
	want := []string{basicFile}
	if !slicesEqual(got, want) {
		t.Errorf("BuildValuesList() without common dir got:\n%v\n\nwant:\n%v", got, want)
	}
}

func TestBuildValuesListWithPreProcessedCommon(t *testing.T) {
	// Test behavior when pre-processed common files are provided
	tmpDir := t.TempDir()
	scenarioBase := filepath.Join(tmpDir, "scenarios", "chart-full-setup")

	if err := os.MkdirAll(scenarioBase, 0755); err != nil {
		t.Fatalf("failed to create test directory: %v", err)
	}

	// Create test scenario file
	basicFile := filepath.Join(scenarioBase, "values-integration-test-ingress-basic.yaml")
	if err := os.WriteFile(basicFile, []byte("# test values"), 0644); err != nil {
		t.Fatalf("failed to create test file: %v", err)
	}

	// Create "pre-processed" common files (simulating files written to temp dir)
	processedCommon1 := filepath.Join(tmpDir, "values-integration-test.yaml")
	if err := os.WriteFile(processedCommon1, []byte("# processed common"), 0644); err != nil {
		t.Fatalf("failed to create processed common file: %v", err)
	}
	processedCommon2 := filepath.Join(tmpDir, "values-integration-test-pull-secrets.yaml")
	if err := os.WriteFile(processedCommon2, []byte("# processed pull secrets"), 0644); err != nil {
		t.Fatalf("failed to create processed common file: %v", err)
	}

	// Pass pre-processed common files
	preProcessedCommon := []string{processedCommon1, processedCommon2}
	got, err := BuildValuesList(scenarioBase, []string{"basic"}, "", false, false, nil, preProcessedCommon)
	if err != nil {
		t.Errorf("BuildValuesList() unexpected error = %v", err)
		return
	}

	// Should use pre-processed common files, not discover from ../common/
	want := []string{processedCommon1, processedCommon2, basicFile}
	if !slicesEqual(got, want) {
		t.Errorf("BuildValuesList() with pre-processed common got:\n%v\n\nwant:\n%v", got, want)
	}
}

func TestResolveCommonValues(t *testing.T) {
	tmpDir := t.TempDir()
	scenariosRoot := filepath.Join(tmpDir, "scenarios")
	scenarioBase := filepath.Join(scenariosRoot, "chart-full-setup")
	commonDir := filepath.Join(scenariosRoot, "common")

	if err := os.MkdirAll(scenarioBase, 0755); err != nil {
		t.Fatalf("failed to create scenario directory: %v", err)
	}
	if err := os.MkdirAll(commonDir, 0755); err != nil {
		t.Fatalf("failed to create common directory: %v", err)
	}

	// Create common values files
	commonIntegrationTest := filepath.Join(commonDir, "values-integration-test.yaml")
	if err := os.WriteFile(commonIntegrationTest, []byte("# common"), 0644); err != nil {
		t.Fatalf("failed to create file: %v", err)
	}
	commonPullSecrets := filepath.Join(commonDir, "values-integration-test-pull-secrets.yaml")
	if err := os.WriteFile(commonPullSecrets, []byte("# pull secrets"), 0644); err != nil {
		t.Fatalf("failed to create file: %v", err)
	}
	// Additional common file not in predefined list
	commonAlpha := filepath.Join(commonDir, "values-integration-test-alpha.yaml")
	if err := os.WriteFile(commonAlpha, []byte("# alpha"), 0644); err != nil {
		t.Fatalf("failed to create file: %v", err)
	}

	tests := []struct {
		name        string
		scenarioDir string
		want        []string
	}{
		{
			name:        "resolves common files in order",
			scenarioDir: scenarioBase,
			want: []string{
				commonIntegrationTest,
				commonPullSecrets,
				commonAlpha, // additional file sorted alphabetically
			},
		},
		{
			name:        "no common dir returns nil",
			scenarioDir: filepath.Join(tmpDir, "nonexistent", "chart-full-setup"),
			want:        nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := resolveCommonValues(tt.scenarioDir)
			if !slicesEqual(got, tt.want) {
				t.Errorf("resolveCommonValues() got:\n%v\n\nwant:\n%v", got, tt.want)
			}
		})
	}
}

func TestOverlayIfExists(t *testing.T) {
	tmpDir := t.TempDir()
	scenarioDir := filepath.Join(tmpDir, "scenarios")
	
	if err := os.MkdirAll(scenarioDir, 0755); err != nil {
		t.Fatalf("failed to create test directory: %v", err)
	}

	// Create a test overlay file
	existingFile := filepath.Join(scenarioDir, "values-enterprise.yaml")
	if err := os.WriteFile(existingFile, []byte("# test"), 0644); err != nil {
		t.Fatalf("failed to create test file: %v", err)
	}

	tests := []struct {
		name        string
		scenarioDir string
		fileName    string
		want        string
	}{
		{
			name:        "existing file",
			scenarioDir: scenarioDir,
			fileName:    "values-enterprise.yaml",
			want:        existingFile,
		},
		{
			name:        "non-existing file",
			scenarioDir: scenarioDir,
			fileName:    "values-nonexistent.yaml",
			want:        "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := overlayIfExists(tt.scenarioDir, tt.fileName)
			if got != tt.want {
				t.Errorf("overlayIfExists() = %v, want %v", got, tt.want)
			}
		})
	}
}

