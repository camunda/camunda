{{/* vim: set filetype=mustache: */}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}

{{- define "tasklist.fullname" -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "tasklist"
        "componentValues" .Values.tasklist
        "context" $
    ) -}}
{{- end -}}

{{/*
Defines extra labels for tasklist.
*/}}
{{ define "tasklist.extraLabels" -}}
app.kubernetes.io/component: tasklist
{{- end }}

{{/*
Define common labels for tasklist, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "tasklist.labels" -}}
    {{- include "camundaPlatform.labels" . }}
app.kubernetes.io/version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.tasklist) | quote }}
    {{- "\n" }}
    {{- include "tasklist.extraLabels" . }}
{{- end -}}

{{/*
Defines match labels for tasklist, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "tasklist.matchLabels" -}}
    {{- include "camundaPlatform.matchLabels" . }}
    {{- "\n" }}
    {{- include "tasklist.extraLabels" . }}
{{- end -}}

{{/*
[tasklist] Create the name of the service account to use
*/}}
{{- define "tasklist.serviceAccountName" -}}
    {{- if .Values.tasklist.serviceAccount.enabled }}
        {{- default (include "tasklist.fullname" .) .Values.tasklist.serviceAccount.name }}
    {{- else }}
        {{- default "default" .Values.tasklist.serviceAccount.name }}
    {{- end }}
{{- end }}

{{/*
[tasklist] Get the image pull secrets.
*/}}
{{- define "tasklist.imagePullSecrets" -}}
    {{- include "camundaPlatform.imagePullSecrets" (dict
        "component" "tasklist"
        "context" $
    ) -}}
{{- end }}

{{- define "tasklist.authClientId" -}}
  {{- .Values.global.identity.auth.tasklist.clientId -}}
{{- end -}}

{{- define "tasklist.authAudience" -}}
  {{- .Values.global.identity.auth.tasklist.audience | default "tasklist-api" -}}
{{- end -}}
