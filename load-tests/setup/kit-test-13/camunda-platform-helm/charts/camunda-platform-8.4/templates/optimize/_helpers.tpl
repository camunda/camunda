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
{{- end }}

{{/*
Define common labels for optimize, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "optimize.labels" -}}
    {{- include "camundaPlatform.labels" . }}
app.kubernetes.io/version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.optimize) | quote }}
    {{- "\n" }}
    {{- include "optimize.extraLabels" . }}
{{- end -}}

{{/*
Defines match labels for optimize, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "optimize.matchLabels" -}}
    {{- include "camundaPlatform.matchLabels" . }}
    {{- "\n" }}
    {{- include "optimize.extraLabels" . }}
{{- end -}}

{{/*
[optimize] Create the name of the service account to use
*/}}
{{- define "optimize.serviceAccountName" -}}
    {{- if .Values.optimize.serviceAccount.enabled }}
        {{- default (include "optimize.fullname" .) .Values.optimize.serviceAccount.name }}
    {{- else }}
        {{- default "default" .Values.optimize.serviceAccount.name }}
    {{- end }}
{{- end }}

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
