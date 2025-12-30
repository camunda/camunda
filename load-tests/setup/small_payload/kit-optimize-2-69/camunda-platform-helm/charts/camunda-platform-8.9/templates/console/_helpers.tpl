{{/*
Expand the name of the chart.
*/}}
{{- define "console.name" -}}
    {{- default .Chart.Name .Values.console.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "console.fullname" -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "console"
        "componentValues" .Values.console
        "context" $
    ) -}}
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
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict "base" .Values.global "overlay" .Values.console "chart" .Chart) | quote }}
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
app.kubernetes.io/component: console
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "console.serviceAccountName" -}}
    {{- include "camundaPlatform.serviceAccountName" (dict
        "component" "console"
        "context" $
    ) -}}
{{- end -}}

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
