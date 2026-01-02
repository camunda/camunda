{{- $release := ds "release" -}}
{{- $releaseHeader := conv.ToBool (getenv "VERSION_MATRIX_RELEASE_HEADER" "true") -}}
{{- $chartDir := printf "charts/camunda-platform-%s" $release.app -}}
{{- if $releaseHeader -}}
<!-- THIS FILE IS AUTO-GENERATED, DO NOT EDIT IT MANUALLY! -->
🔙 [Back to version matrix index](../)

# Camunda {{ $release.app }} Helm Chart Version Matrix

## ToC
{{ range $chartVersion := $release.charts }}
- {{ printf "[Helm chart %s](#helm-chart-%s)" $chartVersion ($chartVersion | strings.ReplaceAll "." "") }}
{{- end }}
{{- end }}

{{- range $chartVersion := $release.charts }}
{{- $gitRef := printf "camunda-platform-%s-%s" $release.app $chartVersion -}}
{{- $vars := dict
  "app_version" $release.app
  "chart_version" $chartVersion
  "chart_images_camunda" (chartImagesCamunda $chartDir $chartVersion | strings.Trim "\n")
  "chart_images_non_camunda" (chartImagesNonCamunda $chartDir $chartVersion | strings.Trim "\n")
  "helm_cli_version" (helmCLIVersion $gitRef | strings.Trim " ")
}}

{{- $helmCLIVersion := ternary
  "N/A"
  (printf "[%s](https://github.com/helm/helm/releases/tag/v%s)" $vars.helm_cli_version $vars.helm_cli_version)
  (eq $vars.helm_cli_version "")
}}

{{- if $releaseHeader -}}
{{ "\n" }}
{{ printf "## Helm chart %s" $vars.chart_version }}
{{ "\n" }}
{{- end }}

{{- with $vars -}}
Supported versions:

- Camunda applications: [{{ .app_version }}](https://github.com/camunda/camunda/releases?q=tag%3A{{ .app_version }}&expanded=true)
- Camunda version matrix: [{{ .app_version }}](https://helm.camunda.io/camunda-platform/version-matrix/camunda-{{ .app_version }})
- Helm values: [{{ .chart_version }}](https://artifacthub.io/packages/helm/camunda/camunda-platform/{{ .chart_version }}#parameters)
- Helm CLI: {{ $helmCLIVersion }}

Camunda images:

{{ .chart_images_camunda }}

Non-Camunda images:

{{ .chart_images_non_camunda }}
{{ end }}

{{- end -}}
