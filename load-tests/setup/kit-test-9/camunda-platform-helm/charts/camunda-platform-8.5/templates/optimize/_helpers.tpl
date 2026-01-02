{{/* vim: set filetype=mustache: */}}

{{/*
Create a default fully qualified app name.
*/}}

{{- define "optimize.fullname" -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "optimize"
        "componentValues" .Values.optimize
        "context" $
    ) -}}
{{- end -}}

{{/*
Defines extra labels for optimize.
*/}}
{{ define "optimize.extraLabels" -}}
app.kubernetes.io/component: optimize
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict "base" .Values.global "overlay" .Values.optimize "chart" .Chart) | quote }}
{{- end }}

{{/*
Define common labels for optimize, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "optimize.labels" -}}
    {{- include "camundaPlatform.labels" . }}
    {{- "\n" }}
    {{- include "optimize.extraLabels" . }}
{{- end -}}

{{/*
Defines match labels for optimize, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "optimize.matchLabels" -}}
    {{- include "camundaPlatform.matchLabels" . }}
app.kubernetes.io/component: optimize
{{- end -}}

{{/*
[optimize] Create the name of the service account to use
*/}}
{{- define "optimize.serviceAccountName" -}}
    {{- include "camundaPlatform.serviceAccountName" (dict
        "component" "optimize"
        "context" $
    ) -}}
{{- end -}}

{{/*
[optimize] Get the image pull secrets.
*/}}
{{- define "optimize.imagePullSecrets" -}}
    {{- include "camundaPlatform.imagePullSecrets" (dict
        "component" "optimize"
        "context" $
    ) -}}
{{- end }}

{{- define "optimize.authClientId" -}}
  {{- .Values.global.identity.auth.optimize.clientId -}}
{{- end -}}

{{- define "optimize.authAudience" -}}
  {{- .Values.global.identity.auth.optimize.audience | default "optimize-api" -}}
{{- end -}}
