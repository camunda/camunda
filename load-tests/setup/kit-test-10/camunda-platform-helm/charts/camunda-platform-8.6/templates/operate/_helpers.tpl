{{/* vim: set filetype=mustache: */}}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "operate.fullname" -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "operate"
        "componentValues" .Values.operate
        "context" $
    ) -}}
{{- end -}}

{{/*
Defines extra labels for operate.
*/}}
{{ define "operate.extraLabels" -}}
app.kubernetes.io/component: operate
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict "base" .Values.global "overlay" .Values.operate "chart" .Chart) | quote }}
{{- end }}

{{/*
Define common labels for operate, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "operate.labels" -}}
    {{- include "camundaPlatform.labels" . }}
    {{- "\n" }}
    {{- include "operate.extraLabels" . }}
{{- end -}}

{{/*
Defines match labels for operate, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "operate.matchLabels" -}}
    {{- include "camundaPlatform.matchLabels" . }}
app.kubernetes.io/component: operate
{{- end -}}

{{/*
[operate] Create the name of the service account to use
*/}}
{{- define "operate.serviceAccountName" -}}
    {{- include "camundaPlatform.serviceAccountName" (dict
        "component" "operate"
        "context" $
    ) -}}
{{- end -}}

{{/*
[operate] Get the image pull secrets.
*/}}
{{- define "operate.imagePullSecrets" -}}
    {{- include "camundaPlatform.imagePullSecrets" (dict
        "component" "operate"
        "context" $
    ) -}}
{{- end }}

{{- define "operate.authClientId" -}}
  {{- .Values.global.identity.auth.operate.clientId -}}
{{- end -}}

{{- define "operate.authAudience" -}}
  {{- .Values.global.identity.auth.operate.audience | default "operate-api" -}}
{{- end -}}
