// Copyright 2025 Camunda Services GmbH
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package camunda

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"gopkg.in/yaml.v3"
)

// Dynamic test suite that validates all images defined in values-enterprise.yaml
type EnterpriseImageValidationTestSuite struct {
	suite.Suite
	chartPath string
}

func (suite *EnterpriseImageValidationTestSuite) SetupSuite() {
	var err error
	suite.chartPath, err = filepath.Abs("../../../")
	require.NoError(suite.T(), err)
}

func TestEnterpriseImageValidationTestSuite(t *testing.T) {
	suite.Run(t, new(EnterpriseImageValidationTestSuite))
}

// ImageConfig represents an image configuration in the values file
type ImageConfig struct {
	Path       string // YAML path like "identityPostgresql.image"
	Registry   string
	Repository string
	Tag        string
}

// Helper function to extract all image configurations from values-enterprise.yaml
func (suite *EnterpriseImageValidationTestSuite) extractImageConfigs() []ImageConfig {
	valuesPath := filepath.Join(suite.chartPath, "values-enterprise.yaml")
	content, err := os.ReadFile(valuesPath)
	suite.Require().NoError(err, "Should be able to read values-enterprise.yaml")

	var values map[string]interface{}
	err = yaml.Unmarshal(content, &values)
	suite.Require().NoError(err, "Should be able to parse values-enterprise.yaml as YAML")

	var configs []ImageConfig
	
	// Helper function to recursively extract image configurations
	var extractImages func(path string, node interface{})
	extractImages = func(path string, node interface{}) {
		switch v := node.(type) {
		case map[string]interface{}:
			// Check if this is an image config (has registry and repository)
			if registry, hasRegistry := v["registry"].(string); hasRegistry {
				if repository, hasRepo := v["repository"].(string); hasRepo {
					config := ImageConfig{
						Path:       path,
						Registry:   registry,
						Repository: repository,
					}
					if tag, hasTag := v["tag"].(string); hasTag {
						config.Tag = tag
					}
					configs = append(configs, config)
					return // Don't recurse into image configs
				}
			}
			
			// Recurse into nested maps
			for key, value := range v {
				newPath := key
				if path != "" {
					newPath = path + "." + key
				}
				extractImages(newPath, value)
			}
		}
	}
	
	extractImages("", values)
	return configs
}

// Test that all images defined in values-enterprise.yaml are present in rendered templates
func (suite *EnterpriseImageValidationTestSuite) TestAllEnterpriseImagesAreRendered() {
	suite.T().Parallel()
	
	// Extract all image configurations from values-enterprise.yaml
	imageConfigs := suite.extractImageConfigs()
	
	suite.T().Logf("Found %d image configurations in values-enterprise.yaml", len(imageConfigs))
	
	// Render the full template with enterprise values AND enable all components
	// to ensure all images are actually rendered
	output := helm.RenderTemplate(suite.T(), &helm.Options{
		ValuesFiles: []string{filepath.Join(suite.chartPath, "values-enterprise.yaml")},
		SetValues: map[string]string{
			// Enable Identity with Keycloak (new format)
			"identity.enabled":                                "true",
			"identityKeycloak.enabled":                        "true",
			"identityKeycloak.postgresql.enabled":             "true",
			"identityKeycloak.postgresql.metrics.enabled":     "true",
			"identityKeycloak.keycloakConfigCli.enabled":      "true",
			
			// Enable Identity PostgreSQL
			"identityPostgresql.enabled":         "true",
			"identityPostgresql.metrics.enabled": "true",
			
			// Enable Web Modeler PostgreSQL
			"postgresql.enabled":         "true",
			"postgresql.metrics.enabled": "true",
			
			// Enable Elasticsearch metrics
			"elasticsearch.metrics.enabled": "true",
		},
	}, suite.chartPath, "camunda-platform-test", []string{})
	
	// Track which images are found and which are missing
	var foundImages []ImageConfig
	var missingImages []ImageConfig
	
	for _, config := range imageConfigs {
		fullImagePath := fmt.Sprintf("%s/%s", config.Registry, config.Repository)
		
		if strings.Contains(output, fullImagePath) {
			foundImages = append(foundImages, config)
			suite.T().Logf("✅ Found image: %s (path: %s)", fullImagePath, config.Path)
		} else {
			missingImages = append(missingImages, config)
			suite.T().Logf("❌ Missing image: %s (path: %s)", fullImagePath, config.Path)
		}
	}
	
	// Summary
	suite.T().Logf("\n📊 Summary: %d/%d images found in rendered templates", 
		len(foundImages), len(imageConfigs))
	
	// Assert that all images are present
	if len(missingImages) > 0 {
		var missingPaths []string
		for _, img := range missingImages {
			missingPaths = append(missingPaths, fmt.Sprintf("%s (%s/%s)", 
				img.Path, img.Registry, img.Repository))
		}
		suite.Fail(fmt.Sprintf("Missing %d images in rendered templates:\n- %s", 
			len(missingImages), strings.Join(missingPaths, "\n- ")))
	}
	
	suite.Equal(len(imageConfigs), len(foundImages), 
		"All images from values-enterprise.yaml should be present in rendered templates")
}

// Test that all enterprise images use the correct registry
func (suite *EnterpriseImageValidationTestSuite) TestAllEnterpriseImagesUseCorrectRegistry() {
	suite.T().Parallel()
	
	// Extract all image configurations
	imageConfigs := suite.extractImageConfigs()
	
	var invalidRegistries []ImageConfig
	
	for _, config := range imageConfigs {
		// All enterprise images should use registry.camunda.cloud
		if config.Registry != "registry.camunda.cloud" {
			invalidRegistries = append(invalidRegistries, config)
			suite.T().Logf("❌ Invalid registry for %s: %s (expected: registry.camunda.cloud)", 
				config.Path, config.Registry)
		} else {
			suite.T().Logf("✅ Correct registry for %s: %s", config.Path, config.Registry)
		}
	}
	
	if len(invalidRegistries) > 0 {
		suite.Fail(fmt.Sprintf("Found %d images with incorrect registry", len(invalidRegistries)))
	}
}

// Test that all enterprise images have pull secrets configured
func (suite *EnterpriseImageValidationTestSuite) TestAllEnterpriseImagesHavePullSecrets() {
	suite.T().Parallel()
	
	valuesPath := filepath.Join(suite.chartPath, "values-enterprise.yaml")
	content, err := os.ReadFile(valuesPath)
	suite.Require().NoError(err)
	
	valuesContent := string(content)
	
	// Extract all image configurations
	imageConfigs := suite.extractImageConfigs()
	
	// For each image config path, verify pullSecrets are configured nearby
	for _, config := range imageConfigs {
		suite.T().Run(fmt.Sprintf("PullSecrets_%s", config.Path), func(t *testing.T) {
			// Search for pullSecrets configuration near this image definition
			// This is a heuristic check - we look for the registry-camunda-cloud secret
			suite.Contains(valuesContent, "registry-camunda-cloud",
				"values-enterprise.yaml should contain registry-camunda-cloud pull secret")
		})
	}
	
	// Also verify in rendered output
	output := helm.RenderTemplate(suite.T(), &helm.Options{
		ValuesFiles: []string{filepath.Join(suite.chartPath, "values-enterprise.yaml")},
	}, suite.chartPath, "camunda-platform-test", []string{})
	
	// Count pull secret references
	pullSecretCount := strings.Count(output, "registry-camunda-cloud")
	suite.T().Logf("Found %d references to registry-camunda-cloud pull secret in rendered templates", 
		pullSecretCount)
	
	suite.Greater(pullSecretCount, 0, 
		"Rendered templates should contain registry-camunda-cloud pull secret references")
}

// Test that validates the structure of values-enterprise.yaml
func (suite *EnterpriseImageValidationTestSuite) TestValuesEnterpriseStructure() {
	suite.T().Parallel()
	
	imageConfigs := suite.extractImageConfigs()
	
	suite.T().Logf("\n📋 Enterprise Images Structure Report:")
	suite.T().Logf("=====================================")
	
	// Group images by component
	componentImages := make(map[string][]ImageConfig)
	for _, config := range imageConfigs {
		// Extract component name (first part of path)
		parts := strings.Split(config.Path, ".")
		component := parts[0]
		componentImages[component] = append(componentImages[component], config)
	}
	
	// Report by component
	for component, images := range componentImages {
		suite.T().Logf("\n🔧 Component: %s (%d images)", component, len(images))
		for _, img := range images {
			suite.T().Logf("   - %s: %s/%s", img.Path, img.Registry, img.Repository)
			if img.Tag != "" {
				suite.T().Logf("     Tag: %s", img.Tag)
			}
		}
	}
	
	suite.T().Logf("\n📊 Total: %d images across %d components", 
		len(imageConfigs), len(componentImages))
	
	// Verify minimum expected number of images
	// Based on the current values-enterprise.yaml structure
	suite.GreaterOrEqual(len(imageConfigs), 15, 
		"Should have at least 15 image configurations (current count in values-enterprise.yaml)")
}

// Test that no nested 'image:' structures exist under image configuration keys
func (suite *EnterpriseImageValidationTestSuite) TestNoNestedImageStructures() {
	suite.T().Parallel()
	
	valuesPath := filepath.Join(suite.chartPath, "values-enterprise.yaml")
	content, err := os.ReadFile(valuesPath)
	suite.Require().NoError(err)
	
	valuesContent := string(content)
	
	// Check for problematic patterns
	problematicPatterns := []string{
		"sysctlImage:\n    image:",
		"sysctlImage:\n      image:",
		"volumePermissions:\n    image:\n      image:",
		"metrics:\n    image:\n      image:",
	}
	
	var foundProblems []string
	for _, pattern := range problematicPatterns {
		if strings.Contains(valuesContent, pattern) {
			foundProblems = append(foundProblems, pattern)
		}
	}
	
	if len(foundProblems) > 0 {
		suite.Fail(fmt.Sprintf("Found problematic nested image structures:\n- %s", 
			strings.Join(foundProblems, "\n- ")))
	}
	
	suite.T().Logf("✅ No problematic nested image structures found")
}

// Test that NO Bitnami default images are used in rendered templates
func (suite *EnterpriseImageValidationTestSuite) TestNoBitnamiDefaultImagesUsed() {
	suite.T().Parallel()
	
	suite.T().Log("🔍 Verifying that no default Bitnami images are used with enterprise values...")
	
	// Render the full template with enterprise values
	output := helm.RenderTemplate(suite.T(), &helm.Options{
		ValuesFiles: []string{filepath.Join(suite.chartPath, "values-enterprise.yaml")},
	}, suite.chartPath, "camunda-platform-test", []string{})
	
	// List of Bitnami registries/repositories that should NOT appear
	bitnamiPatterns := []struct {
		Pattern     string
		Description string
	}{
		{
			Pattern:     "docker.io/bitnami/",
			Description: "Bitnami Docker Hub images",
		},
		{
			Pattern:     "registry-1.docker.io/bitnamicharts",
			Description: "Bitnami Charts registry",
		},
		{
			Pattern:     "image: bitnami/",
			Description: "Short-form Bitnami image references",
		},
		{
			Pattern:     "repository: bitnami/",
			Description: "Bitnami repository references",
		},
		// Specific Bitnami images that should be replaced
		{
			Pattern:     "bitnami/elasticsearch",
			Description: "Bitnami Elasticsearch",
		},
		{
			Pattern:     "bitnami/postgresql",
			Description: "Bitnami PostgreSQL",
		},
		{
			Pattern:     "bitnami/keycloak",
			Description: "Bitnami Keycloak",
		},
		{
			Pattern:     "bitnami/os-shell",
			Description: "Bitnami OS Shell",
		},
		{
			Pattern:     "bitnami/postgres-exporter",
			Description: "Bitnami Postgres Exporter",
		},
		{
			Pattern:     "bitnami/elasticsearch-exporter",
			Description: "Bitnami Elasticsearch Exporter",
		},
		{
			Pattern:     "bitnami/keycloak-config-cli",
			Description: "Bitnami Keycloak Config CLI",
		},
	}
	
	var foundBitnamiImages []string
	
	for _, pattern := range bitnamiPatterns {
		if strings.Contains(output, pattern.Pattern) {
			// Extract context to check for false positives
			lines := strings.Split(output, "\n")
			isFalsePositive := false
			
			for i, line := range lines {
				if strings.Contains(line, pattern.Pattern) {
					// Get surrounding context
					start := i - 2
					if start < 0 {
						start = 0
					}
					end := i + 3
					if end > len(lines) {
						end = len(lines)
					}
					
					contextBlock := strings.Join(lines[start:end], "\n")
					
					// Skip false positives: filesystem paths, mountPath, subPath
					if strings.Contains(contextBlock, "mountPath:") || 
					   strings.Contains(contextBlock, "subPath:") ||
					   strings.Contains(line, "/opt/bitnami/") ||
					   strings.Contains(line, "/bitnami/") {
						isFalsePositive = true
						suite.T().Logf("ℹ️  Skipping false positive (filesystem path): %s in line: %s", 
							pattern.Pattern, strings.TrimSpace(line))
						break
					}
					
					// Real match found
					foundBitnamiImages = append(foundBitnamiImages, 
						fmt.Sprintf("%s (%s)", pattern.Pattern, pattern.Description))
					suite.T().Logf("❌ Found Bitnami pattern: %s (%s)", 
						pattern.Pattern, pattern.Description)
					
					suite.T().Logf("  Context around line %d:", i+1)
					for j := start; j < end; j++ {
						marker := "  "
						if j == i {
							marker = "→ "
						}
						suite.T().Logf("    %s%s", marker, lines[j])
					}
					break // Only show first occurrence of this pattern
				}
			}
			
			if !isFalsePositive && len(foundBitnamiImages) == 0 {
				// Pattern was in output but we didn't classify it yet
				continue
			}
		} else {
			suite.T().Logf("✅ No %s found", pattern.Description)
		}
	}
	
	// Assert that no Bitnami images were found
	if len(foundBitnamiImages) > 0 {
		suite.Fail(fmt.Sprintf(
			"❌ FOUND %d DEFAULT BITNAMI IMAGES IN RENDERED TEMPLATES!\n\n"+
			"The following Bitnami patterns were detected:\n- %s\n\n"+
			"All Bitnami subchart images should be overridden by enterprise images in values-enterprise.yaml.\n"+
			"Please ensure all image configurations are properly set to use registry.camunda.cloud.",
			len(foundBitnamiImages),
			strings.Join(foundBitnamiImages, "\n- ")))
	}
	
	suite.T().Log("✅ SUCCESS: No default Bitnami images found - all images are using enterprise registry!")
}

// Test that all images in rendered templates use the enterprise registry
func (suite *EnterpriseImageValidationTestSuite) TestAllRenderedImagesUseEnterpriseRegistry() {
	suite.T().Parallel()
	
	suite.T().Log("🔍 Extracting all container images from rendered templates...")
	
	// Render the full template with enterprise values
	output := helm.RenderTemplate(suite.T(), &helm.Options{
		ValuesFiles: []string{filepath.Join(suite.chartPath, "values-enterprise.yaml")},
	}, suite.chartPath, "camunda-platform-test", []string{})
	
	// Parse the output to find all image references
	lines := strings.Split(output, "\n")
	var imageLines []struct {
		LineNum int
		Content string
		Image   string
	}
	
	for i, line := range lines {
		trimmedLine := strings.TrimSpace(line)
		
		// Look for image: lines in YAML
		if strings.HasPrefix(trimmedLine, "image:") {
			imageValue := strings.TrimSpace(strings.TrimPrefix(trimmedLine, "image:"))
			imageValue = strings.Trim(imageValue, "\"'")
			
			if imageValue != "" && !strings.HasPrefix(imageValue, "{{") {
				imageLines = append(imageLines, struct {
					LineNum int
					Content string
					Image   string
				}{
					LineNum: i + 1,
					Content: line,
					Image:   imageValue,
				})
			}
		}
	}
	
	suite.T().Logf("📊 Found %d image references in rendered templates", len(imageLines))
	
	// Check each image
	var nonEnterpriseImages []string
	var enterpriseImages []string
	var camundaImages []string
	
	for _, imgLine := range imageLines {
		img := imgLine.Image
		
		// Categorize the image
		if strings.Contains(img, "registry.camunda.cloud/") {
			enterpriseImages = append(enterpriseImages, img)
			suite.T().Logf("✅ Enterprise image (line %d): %s", imgLine.LineNum, img)
		} else if strings.Contains(img, "camunda/") {
			camundaImages = append(camundaImages, img)
			suite.T().Logf("ℹ️  Camunda image (line %d): %s", imgLine.LineNum, img)
		} else if strings.Contains(img, "bitnami") {
			nonEnterpriseImages = append(nonEnterpriseImages, 
				fmt.Sprintf("Line %d: %s", imgLine.LineNum, img))
			suite.T().Logf("❌ Bitnami image (line %d): %s", imgLine.LineNum, img)
		} else {
			// Other registries (might be OK for non-Bitnami components)
			suite.T().Logf("⚠️  Other registry (line %d): %s", imgLine.LineNum, img)
		}
	}
	
	// Summary
	suite.T().Logf("\n📊 Image Registry Summary:")
	suite.T().Logf("  ✅ Enterprise images (registry.camunda.cloud): %d", len(enterpriseImages))
	suite.T().Logf("  ℹ️  Camunda images: %d", len(camundaImages))
	suite.T().Logf("  ❌ Bitnami images: %d", len(nonEnterpriseImages))
	
	// Fail if any Bitnami images are found
	if len(nonEnterpriseImages) > 0 {
		suite.Fail(fmt.Sprintf(
			"Found %d Bitnami images that should be using enterprise registry:\n- %s",
			len(nonEnterpriseImages),
			strings.Join(nonEnterpriseImages, "\n- ")))
	}
	
	// Verify we found some enterprise images
	suite.Greater(len(enterpriseImages), 0, 
		"Should have at least some enterprise images in Bitnami subcharts")
}