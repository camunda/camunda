{{/*
Expand the name of the chart.
*/}}
{{- define "console.name" -}}
{{- default .Chart.Name .Values.console.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "console.fullname" -}}
{{/* TODO: Refactor this when more sub-charts are flatten and moved to the main chart. */}}
    {{- $consoleValues := deepCopy . -}}
    {{- $_ := set $consoleValues.Values "nameOverride" "console" -}}
    {{- include "camundaPlatform.fullname" $consoleValues -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "console.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Defines extra labels for console.
*/}}
{{- define "console.extraLabels" -}}
app.kubernetes.io/component: console
{{- end -}}

{{/*
Common labels
*/}}
{{- define "console.labels" -}}
{{- template "camundaPlatform.labels" . }}
{{ template "console.extraLabels" . }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "console.matchLabels" -}}
{{- template "camundaPlatform.matchLabels" . }}
{{ template "console.extraLabels" . }}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "console.serviceAccountName" -}}
{{- if .Values.console.serviceAccount.create }}
{{- default (include "console.fullname" .) .Values.console.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.console.serviceAccount.name }}
{{- end }}
{{- end }}
