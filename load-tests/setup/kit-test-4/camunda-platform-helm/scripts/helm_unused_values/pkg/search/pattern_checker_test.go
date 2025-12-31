package search_test

import (
	"testing"

	"camunda.com/helmunusedvalues/pkg/output"
	"camunda.com/helmunusedvalues/pkg/patterns"
	"camunda.com/helmunusedvalues/pkg/search"
)

func TestSearchKeyInTemplates(t *testing.T) {
	// Create test cases
	tests := []struct {
		name           string
		key            string
		mockMatches    []string
		expectedFound  bool
		expectedLength int
	}{
		{
			name:           "Key found directly",
			key:            "foo",
			expectedFound:  true,
			expectedLength: 1,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			// Create a test registry
			registry := patterns.New()

			// Create the test finder
			finder := &search.Finder{
				TemplatesDir: "./testdata",
				Registry:     registry,
				UseRipgrep:   true,
				Display:      output.NewDisplay(true, true, true),
			}

			found, matches := finder.SearchForDirectUsageOfKeyAcrossAllTemplates(tc.key)

			// Check results
			if found != tc.expectedFound {
				t.Errorf("Expected found = %v, got %v", tc.expectedFound, found)
			}
			if len(matches) != tc.expectedLength {
				t.Errorf("Expected %d matches, got %d", tc.expectedLength, len(matches))
			}
		})
	}
}

func TestSearchKeyByPatternInTemplates(t *testing.T) {
	// Create test cases
	tests := []struct {
		name           string
		key            string
		patternName    string
		expectedFound  bool
		expectedLength int
	}{
		{
			name:           "Key found in toYaml",
			key:            "toYamlTest",
			patternName:    "toyaml",
			expectedFound:  true,
			expectedLength: 1,
		},
		{
			name:           "Key found in securityContext",
			key:            "test.containerSecurityContext",
			patternName:    "security_context",
			expectedFound:  true,
			expectedLength: 1,
		},
		{
			name:           "Key found in securityContext with keycloak rewrite",
			key:            "identityKeycloak.containerSecurityContext",
			patternName:    "security_context",
			expectedFound:  true,
			expectedLength: 1,
		},
		{
			name:           "SubKey found in securityContext with keycloak rewrite on containerSecurityContext",
			key:            "identityKeycloak.containerSecurityContext.capabilities.drop.0",
			patternName:    "security_context",
			expectedFound:  true,
			expectedLength: 1,
		},
		{
			name:           "SubKey found in securityContext with keycloak rewrite on podSecurityContext",
			key:            "identityKeycloak.podSecurityContext.capabilities.drop.0",
			patternName:    "security_context",
			expectedFound:  true,
			expectedLength: 1,
		},
		{
			name:           "Key found in subChartImagePullSecrets",
			key:            "connectors.image",
			patternName:    "subChartImagePullSecrets",
			expectedFound:  true,
			expectedLength: 1,
		},
		{
			name:           "Fails to find key as it need image sub property in tasklist",
			key:            "tasklist",
			patternName:    "imageByParams",
			expectedFound:  false,
			expectedLength: 0,
		},
		{
			name:           "Finds tasklist",
			key:            "tasklist.image",
			patternName:    "imageByParams",
			expectedFound:  true,
			expectedLength: 1,
		},
		{
			name:           "Finds with context annotations",
			key:            "identityKeycloak.ingress.annotations.nginx.ingress.kubernetes.io/proxy-buffer-size",
			patternName:    "with_context",
			expectedFound:  true,
			expectedLength: 1,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			// Create a test registry
			registry := patterns.New()
			registry.RegisterBuiltins()

			// Create the test finder
			finder := &search.Finder{
				TemplatesDir: "./testdata",
				Registry:     registry,
				UseRipgrep:   true,
				Display:      output.NewDisplay(true, true, true),
			}

			found, _, matches := finder.IsKeyUsedWithPattern(tc.key, tc.patternName)

			// Check results
			if found != tc.expectedFound {
				t.Errorf("Expected found = %v, got %v", tc.expectedFound, found)
			}
			if len(matches) != tc.expectedLength {
				t.Errorf("Expected %d matches, got %d", tc.expectedLength, len(matches))
			}
		})
	}
}
