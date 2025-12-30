// Package testhelpers provides utilities for testing Helm charts.
// To enable verbose logging, set the VERBOSE_TEST_LOGGING environment variable to "true".
// Example: VERBOSE_TEST_LOGGING=true go test ./...
package testhelpers

import (
	"os"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/k8s"
	"github.com/gruntwork-io/terratest/modules/logger"
	"github.com/stretchr/testify/require"
	"gopkg.in/yaml.v3"
	corev1 "k8s.io/api/core/v1"
)

type CaseTemplate struct {
	Templates []string
}

// TestCase represents a single test scenario for Helm chart testing.
// It encapsulates all the necessary configuration and validation logic for a test.
type TestCase struct {
	// Name is the descriptive name of the test case, used for identification in test output
	Name string

	// HelmOptionsExtraArgs contains additional arguments to pass to the Helm command
	// The key spaecifies the Helm command (e.g., "install", "upgrade"), and the value is a slice of arguments
	// This allows customizing the Helm command behavior for specific test cases
	HelmOptionsExtraArgs map[string][]string

	// RenderTemplateExtraArgs contains additional arguments for template rendering
	// These are passed to the template rendering process
	RenderTemplateExtraArgs []string

	// When provided, this function is called to get the templates to render. This overrides the
	// templates set in the test suite
	CaseTemplates *CaseTemplate

	// Values represents the Helm chart values to set for this test case
	// These are equivalent to values passed with --set flag in Helm CLI
	Values map[string]string

	// Expected contains key-value pairs that should be present in the rendered output
	// For error tests, it should contain an "ERROR" key with the expected error message
	// For ConfigMap tests, keys can be direct data keys or dot-notation paths into application.yaml
	Expected map[string]string

	// Verifier is a custom function for complex validation scenarios
	// When provided, it overrides the default validation logic
	// It receives the rendered output and any error that occurred during rendering
	Verifier func(t *testing.T, output string, err error)
}

// quietLogger returns a logger that only logs errors
func quietLogger() *logger.Logger {
	// Check if verbose logging is enabled via environment variable
	if os.Getenv("VERBOSE_TEST_LOGGING") == "true" {
		return logger.Default
	}
	// Create a logger that discards all output
	return logger.Discard
}

func setupHelmOptions(namespace string, values map[string]string, helmOptionsExtraArgs map[string][]string) *helm.Options {
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", namespace),
		Logger:         quietLogger(), // Use quiet logger to reduce verbosity
		ExtraArgs:      helmOptionsExtraArgs,
	}
	return options
}

func renderTemplateE(t *testing.T, chartPath, release string, namespace string, templates []string, values map[string]string, extraArgs map[string][]string, renderTemplateExtraArgs []string) (string, error) {
	options := setupHelmOptions(namespace, values, extraArgs)

	output, err := helm.RenderTemplateE(t, options, chartPath, release, templates, renderTemplateExtraArgs...)
	return output, err
}

func RunTestCasesE(t *testing.T, chartPath, release, namespace string, templates []string, testCases []TestCase) {
	for _, tc := range testCases {
		t.Run(tc.Name, func(t *testing.T) {
			var caseTemplates []string
			if tc.CaseTemplates != nil {
				caseTemplates = tc.CaseTemplates.Templates
			} else {
				caseTemplates = templates
			}
			output, err := renderTemplateE(t, chartPath, release, namespace, caseTemplates, tc.Values, tc.HelmOptionsExtraArgs, tc.RenderTemplateExtraArgs)
			if tc.Verifier != nil {
				tc.Verifier(t, output, err)
			} else {
				require.ErrorContains(t, err, tc.Expected["ERROR"])
			}
		})
	}
}

// renderTemplate renders the specified Helm templates into a Kubernetes ConfigMap
func renderTemplate(t *testing.T, chartPath, release string, namespace string, templates []string, values map[string]string) corev1.ConfigMap {
	options := setupHelmOptions(namespace, values, nil)

	output := helm.RenderTemplate(t, options, chartPath, release, templates)
	var configmap corev1.ConfigMap
	helm.UnmarshalK8SYaml(t, output, &configmap)
	return configmap
}

// RunTestCases executes multiple test cases using the provided Helm chart and ConfigMap validation
func RunTestCases(t *testing.T, chartPath, release, namespace string, templates []string, testCases []TestCase) {
	for _, tc := range testCases {
		t.Run(tc.Name, func(t *testing.T) {
			configmap := renderTemplate(t, chartPath, release, namespace, templates, tc.Values)
			verifyConfigMap(t, tc.Name, configmap, tc.Expected)
		})
	}
}

// verifyConfigMap checks whether the generated ConfigMap contains the expected key-value pairs
func verifyConfigMap(t *testing.T, testCase string, configmap corev1.ConfigMap, expectedValues map[string]string) {
	for keyPath, expectedValue := range expectedValues {
		var actualValue string
		if strings.HasPrefix(keyPath, "configmapApplication.") {
			var configmapApplication map[string]any
			err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
			require.NoError(t, err)
			actualValue = getConfigMapFieldValue(configmapApplication, strings.Split(keyPath, ".")[1:])
		} else {
			actualValue = strings.TrimSpace(configmap.Data[keyPath])
		}
		require.Equal(t, expectedValue, actualValue, "Test case '%s': Expected key '%s' to have value '%s', but got '%s'", testCase, keyPath, expectedValue, actualValue)
	}
}

// getConfigMapFieldValue function traverses a nested map structure based on a given key path.
// It handles maps with both interface{} and string keys, converting them as necessary to retrieve the desired value.
// If the key is not found or the final value is not a string, the function returns an empty string.
func getConfigMapFieldValue(configmapApplication map[string]any, keyPath []string) string {
	var current any = configmapApplication

	for _, key := range keyPath {
		if nestedMap, ok := current.(map[any]any); ok {
			// Convert map[interface{}]any to map[string]any
			stringMap := make(map[string]any)
			for k, v := range nestedMap {
				if strKey, isString := k.(string); isString {
					stringMap[strKey] = v
				}
			}
			// Move to the next level in the map
			current = stringMap[key]
		} else if nestedMap, ok := current.(map[string]any); ok {
			// If the current level is already a map with string keys, move to the next level
			current = nestedMap[key]
		} else {
			// If the key is not found, return an empty string
			return ""
		}
	}

	// If the final value is a string, return it
	if value, ok := current.(string); ok {
		return value
	}
	// If the final value is not a string, return an empty string
	return ""
}
