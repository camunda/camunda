package deployer

import (
	"context"
	"fmt"
	"path/filepath"
	"scripts/camunda-core/pkg/helm"
	"scripts/camunda-deployer/pkg/types"
	"strings"
)

// upgradeInstall builds and executes helm upgrade --install with deployer's opinionated policies
func upgradeInstall(ctx context.Context, o types.Options) error {
	var args []string
	if o.Chart != "" {
	args = []string{
		"upgrade", "--install",
		o.ReleaseName,
		o.Chart,
		"-n", o.Namespace,
	}
	} else {
	args = []string{
		"upgrade", "--install",
		o.ReleaseName,
		filepath.Clean(o.ChartPath),
		"-n", o.Namespace,
	}
}

	// When using a repository chart name, allow pinning the chart version
	if o.Chart != "" && strings.TrimSpace(o.Version) != "" {
		args = append(args, "--version", o.Version)
	}

	// Deployer policy: always create namespace
	args = append(args, "--create-namespace")

	// Kubernetes connection
	args = append(args, composeKubeArgs(o.Kubeconfig, o.KubeContext)...)

	// Deployment behavior
	if o.Wait {
		args = append(args, "--wait")
	}
	if o.Atomic {
		args = append(args, "--atomic")
	}
	if o.Timeout > 0 {
		args = append(args, "--timeout", fmt.Sprintf("%ds", int(o.Timeout.Seconds())))
	}

	// Deployer convention: set global.ingress.host for Camunda Platform
	if o.IngressHost != "" {
		args = append(args, "--set", "global.ingress.host="+o.IngressHost)
	}

	// Optional post-renderer
	if o.PostRendererPath != "" {
		args = append(args, "--post-renderer", o.PostRendererPath)
	}

	// Values files in order
	for _, v := range o.ValuesFiles {
		args = append(args, "-f", v)
	}

	// Set pairs - deployer uses map[string]string, format as key=value
	if len(o.SetPairs) > 0 {
		// Sort keys for determinism
		keys := make([]string, 0, len(o.SetPairs))
		for k := range o.SetPairs {
			keys = append(keys, k)
		}
		// Note: intentionally not sorting to preserve user order
		for k, v := range o.SetPairs {
			args = append(args, "--set", fmt.Sprintf("%s=%s", k, v))
		}
	}

	// Extra args last (allow override)
	if len(o.ExtraArgs) > 0 {
		args = append(args, o.ExtraArgs...)
	}

	// Execute via thin helm wrapper
	err := helm.Run(ctx, args, "")
	if err != nil {
		return fmt.Errorf("helm upgrade --install failed: command: helm %s: %w", formatArgs(args), err)
	}
	return nil
}

// composeKubeArgs builds kubeconfig and context arguments
func composeKubeArgs(kubeconfig, context string) []string {
	var args []string
	if kubeconfig != "" {
		args = append(args, "--kubeconfig", kubeconfig)
	}
	if context != "" {
		args = append(args, "--kube-context", context)
	}
	return args
}

// formatArgs formats command arguments for error messages, escaping special characters
func formatArgs(args []string) string {
	var parts []string
	for _, arg := range args {
		// If arg contains spaces or special chars, quote it
		if strings.ContainsAny(arg, " \t\n\"'") {
			parts = append(parts, fmt.Sprintf("%q", arg))
		} else {
			parts = append(parts, arg)
		}
	}
	return strings.Join(parts, " ")
}

