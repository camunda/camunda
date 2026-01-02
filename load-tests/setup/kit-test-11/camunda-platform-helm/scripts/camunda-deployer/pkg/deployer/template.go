package deployer

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"scripts/camunda-core/pkg/helm"
	"scripts/camunda-core/pkg/logging"
	"scripts/camunda-deployer/pkg/types"
)

// renderTemplates builds and executes `helm template` to render manifests to disk.
func renderTemplates(ctx context.Context, o types.Options) error {
	var chartArg string
	if o.Chart != "" {
		chartArg = o.Chart
	} else {
		chartArg = filepath.Clean(o.ChartPath)
	}

	args := []string{
		"template",
		o.ReleaseName,
		chartArg,
		"-n", o.Namespace,
	}

	// Include CRDs by default unless explicitly disabled
	if o.IncludeCRDs {
		args = append(args, "--include-crds")
	}

	// Values files in order
	for _, v := range o.ValuesFiles {
		args = append(args, "-f", v)
	}

	// Optional post-renderer
	if o.PostRendererPath != "" {
		args = append(args, "--post-renderer", o.PostRendererPath)
	}

	// Determine output dir (default: ./rendered/<release>)
	outputDir := o.RenderOutputDir
	if outputDir == "" {
		outputDir = filepath.Join(".", "rendered", o.ReleaseName)
	}
	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return fmt.Errorf("failed to create render output dir %q: %w", outputDir, err)
	}
	args = append(args, "--output-dir", outputDir)
	
	args = append(args, "--debug")

	logging.Logger.Info().
		Str("outputDir", outputDir).
		Str("release", o.ReleaseName).
		Str("namespace", o.Namespace).
		Msg("Rendering Helm templates to directory")

	if err := helm.Run(ctx, args, ""); err != nil {
		return fmt.Errorf("helm template failed: %w", err)
	}

	logging.Logger.Info().Str("outputDir", outputDir).Msg("Templates rendered successfully")
	return nil
}


