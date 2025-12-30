package deployer

import (
	"context"
	"encoding/base64"
	"fmt"
	"os"
	"scripts/camunda-core/pkg/kube"
	"scripts/camunda-core/pkg/logging"
	"strings"
)

// labelAndAnnotateNamespace adds Camunda/GitHub-specific labels and annotations
func labelAndAnnotateNamespace(ctx context.Context, kubeClient *kube.Client, namespace, identifier, flow, ttl string, ghRunID string, ghJobID string, ghOrg string, ghRepo string, workflowURL string) error {
	// Build labels map
	labels := make(map[string]string)
	if strings.TrimSpace(identifier) != "" {
		labels["github-id"] = identifier
	}
	if strings.TrimSpace(flow) != "" {
		labels["test-flow"] = flow
	}
	if strings.TrimSpace(ghRunID) != "" {
		labels["github-run-id"] = ghRunID
	}
	if strings.TrimSpace(ghJobID) != "" {
		labels["github-job-id"] = ghJobID
	}
	if strings.TrimSpace(ghOrg) != "" {
		labels["github-org"] = ghOrg
	}
	if strings.TrimSpace(ghRepo) != "" {
		labels["github-repo"] = ghRepo
	}

	// Build annotations map
	if strings.TrimSpace(ttl) == "" {
		ttl = "1h"
	}
	annotations := map[string]string{
		"cleaner/ttl":             ttl,
		"janitor/ttl":             ttl,
		"camunda.cloud/ephemeral": "true",
	}
	if strings.TrimSpace(workflowURL) != "" {
		annotations["github-workflow-run-url"] = workflowURL
	}

	// Use generic method to apply
	return kubeClient.SetLabelsAndAnnotations(ctx, namespace, labels, annotations)
}

// applyIntegrationTestCredentials applies integration test credentials from environment variable
func applyIntegrationTestCredentials(ctx context.Context, kubeClient *kube.Client, namespace string) error {
	b64 := strings.TrimSpace(os.Getenv("INTEGRATION_TEST_CREDENTIALS"))
	if b64 == "" {
		logging.Logger.Debug().Str("namespace", namespace).Msg("skipping integration-test credentials (env not present)")
		return nil
	}

	logging.Logger.Debug().Str("namespace", namespace).Msg("applying integration-test credentials from env")

	decoded, err := base64.StdEncoding.DecodeString(b64)
	if err != nil {
		return fmt.Errorf("failed to decode INTEGRATION_TEST_CREDENTIALS: %w", err)
	}

	// Use generic method to apply manifest
	return kubeClient.ApplyManifest(ctx, namespace, decoded)
}
