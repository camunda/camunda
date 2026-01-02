package camunda

import (
	"testing"

	"camunda-platform/test/unit/testhelpers"
	"github.com/stretchr/testify/suite"
)

type tlsSecretsTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func (s *tlsSecretsTest) SetupTest() {
	s.chartPath = "../../../"
	s.release = "test-release"
	s.namespace = "test-namespace"
	s.templates = []string{"templates"}
}

// Elasticsearch TLS Tests
func (s *tlsSecretsTest) TestElasticsearchTLSLegacyPattern() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "elasticsearch legacy TLS secret pattern",
			Template: "templates/orchestration/statefulset.yaml",
			Values: map[string]string{
				"orchestration.enabled":                   "true",
				"global.elasticsearch.enabled":            "true",
				"global.elasticsearch.external":           "true",
				"global.elasticsearch.tls.enabled":        "true",
				"global.elasticsearch.tls.existingSecret": "legacy-es-tls-secret",
			},
			Expected: map[string]string{
				// Volume should use legacy secret name
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "legacy-es-tls-secret",
				// SubPath should default to externaldb.jks for legacy pattern
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath": "externaldb.jks",
				// MountPath should use default key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/usr/local/camunda/certificates/externaldb.jks",
				// JAVA_TOOL_OPTIONS should include trustStore with default key
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value": "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/usr/local/camunda/data -XX:ErrorFile=/usr/local/camunda/data/camunda_error%p.log -XX:+ExitOnOutOfMemoryError -Djavax.net.ssl.trustStore=/usr/local/camunda/certificates/externaldb.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestElasticsearchTLSNewPatternCustomKey() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "elasticsearch new TLS secret pattern with custom key",
			Template: "templates/orchestration/statefulset.yaml",
			Values: map[string]string{
				"orchestration.enabled":                             "true",
				"global.elasticsearch.enabled":                      "true",
				"global.elasticsearch.external":                     "true",
				"global.elasticsearch.tls.enabled":                  "true",
				"global.elasticsearch.tls.secret.existingSecret":    "new-es-tls-abc123",
				"global.elasticsearch.tls.secret.existingSecretKey": "custom-truststore.jks",
			},
			Expected: map[string]string{
				// Volume should use new secret name
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "new-es-tls-abc123",
				// SubPath should use custom key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath": "custom-truststore.jks",
				// MountPath should use custom key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/usr/local/camunda/certificates/custom-truststore.jks",
				// JAVA_TOOL_OPTIONS should include trustStore with custom key
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value": "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/usr/local/camunda/data -XX:ErrorFile=/usr/local/camunda/data/camunda_error%p.log -XX:+ExitOnOutOfMemoryError -Djavax.net.ssl.trustStore=/usr/local/camunda/certificates/custom-truststore.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestElasticsearchTLSNewPatternDefaultKey() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "elasticsearch new TLS secret pattern with default key",
			Template: "templates/orchestration/statefulset.yaml",
			Values: map[string]string{
				"orchestration.enabled":                          "true",
				"global.elasticsearch.enabled":                   "true",
				"global.elasticsearch.external":                  "true",
				"global.elasticsearch.tls.enabled":               "true",
				"global.elasticsearch.tls.secret.existingSecret": "es-secret-def456",
			},
			Expected: map[string]string{
				// Volume should use new secret name
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "es-secret-def456",
				// SubPath should use default from values.yaml (externaldb.jks)
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath": "externaldb.jks",
				// MountPath should use default key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/usr/local/camunda/certificates/externaldb.jks",
				// JAVA_TOOL_OPTIONS should include trustStore with default key
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value": "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/usr/local/camunda/data -XX:ErrorFile=/usr/local/camunda/data/camunda_error%p.log -XX:+ExitOnOutOfMemoryError -Djavax.net.ssl.trustStore=/usr/local/camunda/certificates/externaldb.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestElasticsearchTLSBothPatterns() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "elasticsearch both TLS patterns - new takes precedence",
			Template: "templates/orchestration/statefulset.yaml",
			Values: map[string]string{
				"orchestration.enabled":                             "true",
				"global.elasticsearch.enabled":                      "true",
				"global.elasticsearch.external":                     "true",
				"global.elasticsearch.tls.enabled":                  "true",
				"global.elasticsearch.tls.existingSecret":           "legacy-es-secret",
				"global.elasticsearch.tls.secret.existingSecret":    "new-es-secret-xyz",
				"global.elasticsearch.tls.secret.existingSecretKey": "new-cert.jks",
			},
			Expected: map[string]string{
				// Volume should use new secret name (not legacy)
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "new-es-secret-xyz",
				// SubPath should use new key (not default)
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath": "new-cert.jks",
				// MountPath should use new key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/usr/local/camunda/certificates/new-cert.jks",
				// JAVA_TOOL_OPTIONS should use new key
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value": "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/usr/local/camunda/data -XX:ErrorFile=/usr/local/camunda/data/camunda_error%p.log -XX:+ExitOnOutOfMemoryError -Djavax.net.ssl.trustStore=/usr/local/camunda/certificates/new-cert.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

// OpenSearch TLS Tests
func (s *tlsSecretsTest) TestOpenSearchTLSLegacyPattern() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "opensearch legacy TLS secret pattern",
			Template: "templates/orchestration/statefulset.yaml",
			Values: map[string]string{
				"orchestration.enabled":                "true",
				"global.opensearch.enabled":            "true",
				"global.opensearch.external":           "true",
				"global.opensearch.tls.enabled":        "true",
				"global.opensearch.tls.existingSecret": "legacy-os-tls-ghi789",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName":                          "legacy-os-tls-ghi789",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":                 "externaldb.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath":               "/usr/local/camunda/certificates/externaldb.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":                   "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/usr/local/camunda/data -XX:ErrorFile=/usr/local/camunda/data/camunda_error%p.log -XX:+ExitOnOutOfMemoryError -Djavax.net.ssl.trustStore=/usr/local/camunda/certificates/externaldb.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOpenSearchTLSNewPatternCustomKey() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "opensearch new TLS secret pattern with custom key",
			Template: "templates/orchestration/statefulset.yaml",
			Values: map[string]string{
				"orchestration.enabled":                          "true",
				"global.opensearch.enabled":                      "true",
				"global.opensearch.external":                     "true",
				"global.opensearch.tls.enabled":                  "true",
				"global.opensearch.tls.secret.existingSecret":    "os-certificates-jkl012",
				"global.opensearch.tls.secret.existingSecretKey": "ca-bundle-prod.jks",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName":                          "os-certificates-jkl012",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":                 "ca-bundle-prod.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath":               "/usr/local/camunda/certificates/ca-bundle-prod.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":                   "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/usr/local/camunda/data -XX:ErrorFile=/usr/local/camunda/data/camunda_error%p.log -XX:+ExitOnOutOfMemoryError -Djavax.net.ssl.trustStore=/usr/local/camunda/certificates/ca-bundle-prod.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOpenSearchTLSNewPatternDefaultKey() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "opensearch new TLS secret pattern with default key",
			Template: "templates/orchestration/statefulset.yaml",
			Values: map[string]string{
				"orchestration.enabled":                       "true",
				"global.opensearch.enabled":                   "true",
				"global.opensearch.external":                  "true",
				"global.opensearch.tls.enabled":               "true",
				"global.opensearch.tls.secret.existingSecret": "os-secret-mno345",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName":                          "os-secret-mno345",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":                 "externaldb.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath":               "/usr/local/camunda/certificates/externaldb.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":                   "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/usr/local/camunda/data -XX:ErrorFile=/usr/local/camunda/data/camunda_error%p.log -XX:+ExitOnOutOfMemoryError -Djavax.net.ssl.trustStore=/usr/local/camunda/certificates/externaldb.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOpenSearchTLSBothPatterns() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "opensearch both TLS patterns - new takes precedence",
			Template: "templates/orchestration/statefulset.yaml",
			Values: map[string]string{
				"orchestration.enabled":                          "true",
				"global.opensearch.enabled":                      "true",
				"global.opensearch.external":                     "true",
				"global.opensearch.tls.enabled":                  "true",
				"global.opensearch.tls.existingSecret":           "legacy-os-old",
				"global.opensearch.tls.secret.existingSecret":    "new-os-pqr678",
				"global.opensearch.tls.secret.existingSecretKey": "production.jks",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName":                          "new-os-pqr678",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":                 "production.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath":               "/usr/local/camunda/certificates/production.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":                   "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/usr/local/camunda/data -XX:ErrorFile=/usr/local/camunda/data/camunda_error%p.log -XX:+ExitOnOutOfMemoryError -Djavax.net.ssl.trustStore=/usr/local/camunda/certificates/production.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

// Console TLS Tests
func (s *tlsSecretsTest) TestConsoleTLSLegacyPattern() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "console legacy TLS secret pattern",
			Template: "templates/console/deployment.yaml",
			Values: map[string]string{
				"console.enabled":                 "true",
				"console.contextPath":             "/",
				"identity.enabled":                "true",
				"global.identity.auth.enabled":    "true",
				"console.tls.enabled":             "true",
				"console.tls.existingSecret":      "legacy-console-tls-stu901",
				"console.tls.certKeyFilename":     "server-ca.crt",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='console-certificates')].secret.secretName":        "legacy-console-tls-stu901",
				"spec.template.spec.containers[0].env[?(@.name=='NODE_EXTRA_CA_CERTS')].value":           "/usr/local/console/certificates/server-ca.crt",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='console-certificates')].mountPath": "/usr/local/console/certificates",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestConsoleTLSNewPattern() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "console new TLS secret pattern",
			Template: "templates/console/deployment.yaml",
			Values: map[string]string{
				"console.enabled":                    "true",
				"console.contextPath":                "/",
				"identity.enabled":                   "true",
				"global.identity.auth.enabled":       "true",
				"console.tls.enabled":                "true",
				"console.tls.secret.existingSecret":  "new-console-certs-vwx234",
				"console.tls.certKeyFilename":        "custom-root-ca.pem",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='console-certificates')].secret.secretName":        "new-console-certs-vwx234",
				"spec.template.spec.containers[0].env[?(@.name=='NODE_EXTRA_CA_CERTS')].value":           "/usr/local/console/certificates/custom-root-ca.pem",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='console-certificates')].mountPath": "/usr/local/console/certificates",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestConsoleTLSBothPatterns() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "console both TLS patterns - new takes precedence",
			Template: "templates/console/deployment.yaml",
			Values: map[string]string{
				"console.enabled":                   "true",
				"console.contextPath":               "/",
				"identity.enabled":                  "true",
				"global.identity.auth.enabled":      "true",
				"console.tls.enabled":               "true",
				"console.tls.existingSecret":        "legacy-console-old",
				"console.tls.secret.existingSecret": "new-console-bcd890",
				"console.tls.certKeyFilename":       "ca-cert.pem",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='console-certificates')].secret.secretName":        "new-console-bcd890",
				"spec.template.spec.containers[0].env[?(@.name=='NODE_EXTRA_CA_CERTS')].value":           "/usr/local/console/certificates/ca-cert.pem",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='console-certificates')].mountPath": "/usr/local/console/certificates",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

// Disabled State Tests
func (s *tlsSecretsTest) TestElasticsearchTLSEnabledNoSecret() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "elasticsearch TLS enabled but no secret provided",
			Template: "templates/orchestration/statefulset.yaml",
			Values: map[string]string{
				"orchestration.enabled":                "true",
				"global.elasticsearch.enabled":         "true",
				"global.elasticsearch.external":        "true",
				"global.elasticsearch.tls.enabled":     "true",
			},
			Expected: map[string]string{
				// Volume should not exist when no secret is provided
				"spec.template.spec.volumes[?(@.name=='keystore')]": "null",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestConsoleTLSEnabledNoSecret() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "console TLS enabled but no secret provided",
			Template: "templates/console/deployment.yaml",
			Values: map[string]string{
				"console.enabled":              "true",
				"console.contextPath":          "/",
				"identity.enabled":             "true",
				"global.identity.auth.enabled": "true",
				"console.tls.enabled":          "true",
				"console.tls.certKeyFilename":  "ca.crt",
			},
			Expected: map[string]string{
				// Volume should not exist when no secret is provided
				"spec.template.spec.volumes[?(@.name=='console-certificates')]": "null",
				// But NODE_EXTRA_CA_CERTS should still be rendered
				"spec.template.spec.containers[0].env[?(@.name=='NODE_EXTRA_CA_CERTS')].value": "/usr/local/console/certificates/ca.crt",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestConsoleTLSDisabled() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "console TLS disabled - env var still rendered",
			Template: "templates/console/deployment.yaml",
			Values: map[string]string{
				"console.enabled":              "true",
				"console.contextPath":          "/",
				"identity.enabled":             "true",
				"global.identity.auth.enabled": "true",
				"console.tls.enabled":          "false",
				"console.tls.certKeyFilename":  "ca.crt",
			},
			Expected: map[string]string{
				// Volume should not exist when TLS is disabled
				"spec.template.spec.volumes[?(@.name=='console-certificates')]": "null",
				// But NODE_EXTRA_CA_CERTS should still be rendered (reference doc says "always rendered")
				"spec.template.spec.containers[0].env[?(@.name=='NODE_EXTRA_CA_CERTS')].value": "/usr/local/console/certificates/ca.crt",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

// Optimize TLS Tests (uses same global ES/OS config)
func (s *tlsSecretsTest) TestOptimizeElasticsearchTLSLegacyPattern() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "optimize elasticsearch legacy TLS secret pattern",
			Template: "templates/optimize/deployment.yaml",
			Values: map[string]string{
				"optimize.enabled":                        "true",
				"identity.enabled":                        "true",
				"global.identity.auth.enabled":            "true",
				"global.elasticsearch.enabled":            "true",
				"global.elasticsearch.external":           "true",
				"global.elasticsearch.tls.enabled":        "true",
				"global.elasticsearch.tls.existingSecret": "legacy-optimize-es-tls",
			},
			Expected: map[string]string{
				// Volume should use legacy secret name
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "legacy-optimize-es-tls",
				// Init container: SubPath should default to externaldb.jks
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].subPath": "externaldb.jks",
				// Init container: MountPath should use default key
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/externaldb.jks",
				// Init container: JAVA_TOOL_OPTIONS should include trustStore with default key
				"spec.template.spec.initContainers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value": "-Djavax.net.ssl.trustStore=/optimize/certificates/externaldb.jks",
				// Main container: SubPath should default to externaldb.jks
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath": "externaldb.jks",
				// Main container: MountPath should use default key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/externaldb.jks",
				// Main container: JAVA_TOOL_OPTIONS should include trustStore with default key
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value": "-Djavax.net.ssl.trustStore=/optimize/certificates/externaldb.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOptimizeElasticsearchTLSNewPatternCustomKey() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "optimize elasticsearch new TLS secret pattern with custom key",
			Template: "templates/optimize/deployment.yaml",
			Values: map[string]string{
				"optimize.enabled":                                  "true",
				"identity.enabled":                                  "true",
				"global.identity.auth.enabled":                      "true",
				"global.elasticsearch.enabled":                      "true",
				"global.elasticsearch.external":                     "true",
				"global.elasticsearch.tls.enabled":                  "true",
				"global.elasticsearch.tls.secret.existingSecret":    "new-optimize-es-uvw345",
				"global.elasticsearch.tls.secret.existingSecretKey": "optimize-truststore.jks",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "new-optimize-es-uvw345",
				// Init container with custom key
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].subPath":   "optimize-truststore.jks",
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/optimize-truststore.jks",
				"spec.template.spec.initContainers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/optimize-truststore.jks",
				// Main container with custom key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":   "optimize-truststore.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/optimize-truststore.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/optimize-truststore.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOptimizeOpenSearchTLSNewPatternCustomKey() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "optimize opensearch new TLS secret pattern with custom key",
			Template: "templates/optimize/deployment.yaml",
			Values: map[string]string{
				"optimize.enabled":                               "true",
				"identity.enabled":                               "true",
				"global.identity.auth.enabled":                   "true",
				"global.opensearch.enabled":                      "true",
				"global.opensearch.external":                     "true",
				"global.opensearch.tls.enabled":                  "true",
				"global.opensearch.tls.secret.existingSecret":    "new-optimize-os-xyz678",
				"global.opensearch.tls.secret.existingSecretKey": "opensearch-certs.jks",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "new-optimize-os-xyz678",
				// Init container with custom key
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].subPath":   "opensearch-certs.jks",
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/opensearch-certs.jks",
				"spec.template.spec.initContainers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/opensearch-certs.jks",
				// Main container with custom key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":   "opensearch-certs.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/opensearch-certs.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/opensearch-certs.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOptimizeElasticsearchTLSNewPatternDefaultKey() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "optimize elasticsearch new TLS secret pattern with default key",
			Template: "templates/optimize/deployment.yaml",
			Values: map[string]string{
				"optimize.enabled":                               "true",
				"identity.enabled":                               "true",
				"global.identity.auth.enabled":                   "true",
				"global.elasticsearch.enabled":                   "true",
				"global.elasticsearch.external":                  "true",
				"global.elasticsearch.tls.enabled":               "true",
				"global.elasticsearch.tls.secret.existingSecret": "new-optimize-es-default-key",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "new-optimize-es-default-key",
				// Init container with default key (externaldb.jks)
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].subPath":   "externaldb.jks",
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/externaldb.jks",
				"spec.template.spec.initContainers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/externaldb.jks",
				// Main container with default key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":   "externaldb.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/externaldb.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/externaldb.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOptimizeElasticsearchTLSBothPatterns() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "optimize elasticsearch both TLS patterns - new takes precedence",
			Template: "templates/optimize/deployment.yaml",
			Values: map[string]string{
				"optimize.enabled":                                  "true",
				"identity.enabled":                                  "true",
				"global.identity.auth.enabled":                      "true",
				"global.elasticsearch.enabled":                      "true",
				"global.elasticsearch.external":                     "true",
				"global.elasticsearch.tls.enabled":                  "true",
				"global.elasticsearch.tls.existingSecret":           "legacy-optimize-es-both",
				"global.elasticsearch.tls.secret.existingSecret":    "new-optimize-es-both",
				"global.elasticsearch.tls.secret.existingSecretKey": "new-pattern.jks",
			},
			Expected: map[string]string{
				// Should use NEW pattern secret name
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "new-optimize-es-both",
				// Init container with NEW pattern custom key
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].subPath":   "new-pattern.jks",
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/new-pattern.jks",
				"spec.template.spec.initContainers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/new-pattern.jks",
				// Main container with NEW pattern custom key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":   "new-pattern.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/new-pattern.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/new-pattern.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOptimizeOpenSearchTLSLegacyPattern() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "optimize opensearch legacy TLS secret pattern",
			Template: "templates/optimize/deployment.yaml",
			Values: map[string]string{
				"optimize.enabled":                        "true",
				"identity.enabled":                        "true",
				"global.identity.auth.enabled":            "true",
				"global.opensearch.enabled":               "true",
				"global.opensearch.external":              "true",
				"global.opensearch.tls.enabled":           "true",
				"global.opensearch.tls.existingSecret":    "legacy-optimize-os-tls",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "legacy-optimize-os-tls",
				// Init container with default key
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].subPath":   "externaldb.jks",
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/externaldb.jks",
				"spec.template.spec.initContainers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/externaldb.jks",
				// Main container with default key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":   "externaldb.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/externaldb.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/externaldb.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOptimizeOpenSearchTLSNewPatternDefaultKey() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "optimize opensearch new TLS secret pattern with default key",
			Template: "templates/optimize/deployment.yaml",
			Values: map[string]string{
				"optimize.enabled":                            "true",
				"identity.enabled":                            "true",
				"global.identity.auth.enabled":                "true",
				"global.opensearch.enabled":                   "true",
				"global.opensearch.external":                  "true",
				"global.opensearch.tls.enabled":               "true",
				"global.opensearch.tls.secret.existingSecret": "new-optimize-os-default-key",
			},
			Expected: map[string]string{
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "new-optimize-os-default-key",
				// Init container with default key
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].subPath":   "externaldb.jks",
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/externaldb.jks",
				"spec.template.spec.initContainers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/externaldb.jks",
				// Main container with default key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":   "externaldb.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/externaldb.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/externaldb.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *tlsSecretsTest) TestOptimizeOpenSearchTLSBothPatterns() {
	testCases := []testhelpers.TestCase{
		{
			Name:     "optimize opensearch both TLS patterns - new takes precedence",
			Template: "templates/optimize/deployment.yaml",
			Values: map[string]string{
				"optimize.enabled":                               "true",
				"identity.enabled":                               "true",
				"global.identity.auth.enabled":                   "true",
				"global.opensearch.enabled":                      "true",
				"global.opensearch.external":                     "true",
				"global.opensearch.tls.enabled":                  "true",
				"global.opensearch.tls.existingSecret":           "legacy-optimize-os-both",
				"global.opensearch.tls.secret.existingSecret":    "new-optimize-os-both",
				"global.opensearch.tls.secret.existingSecretKey": "new-os-pattern.jks",
			},
			Expected: map[string]string{
				// Should use NEW pattern secret name
				"spec.template.spec.volumes[?(@.name=='keystore')].secret.secretName": "new-optimize-os-both",
				// Init container with NEW pattern custom key
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].subPath":   "new-os-pattern.jks",
				"spec.template.spec.initContainers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/new-os-pattern.jks",
				"spec.template.spec.initContainers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/new-os-pattern.jks",
				// Main container with NEW pattern custom key
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].subPath":   "new-os-pattern.jks",
				"spec.template.spec.containers[0].volumeMounts[?(@.name=='keystore')].mountPath": "/optimize/certificates/new-os-pattern.jks",
				"spec.template.spec.containers[0].env[?(@.name=='JAVA_TOOL_OPTIONS')].value":     "-Djavax.net.ssl.trustStore=/optimize/certificates/new-os-pattern.jks",
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func TestTLSSecretsTestSuite(t *testing.T) {
	suite.Run(t, new(tlsSecretsTest))
}
