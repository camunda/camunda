{{/*
Expand the name of the chart.
*/}}
{{- define "console.name" -}}
    {{- default .Chart.Name .Values.console.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "console.fullname" -}}
{{- /* TODO: Refactor this when more sub-charts are flatten and moved to the main chart. */}}
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
app.kubernetes.io/version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.console) | quote }}
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
    {{- if .Values.console.serviceAccount.enabled }}
        {{- default (include "console.fullname" .) .Values.console.serviceAccount.name }}
    {{- else }}
        {{- default "default" .Values.console.serviceAccount.name }}
    {{- end }}
{{- end }}

{{/*
Get the image pull secrets.
*/}}
{{- define "console.imagePullSecrets" -}}
    {{- include "camundaPlatform.imagePullSecrets" (dict
        "component" "console"
        "context" $
    ) -}}
{{- end }}

{{/*
[console] Define variables related to authentication.
*/}}
{{- define "console.authAudience" -}}
  {{- .Values.global.identity.auth.console.audience | default "console-api" -}}
{{- end -}}
