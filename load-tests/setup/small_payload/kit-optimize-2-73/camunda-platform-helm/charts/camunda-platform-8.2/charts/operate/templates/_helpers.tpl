{{/* vim: set filetype=mustache: */}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "operate.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Defines extra labels for operate.
*/}}
{{- define "operate.extraLabels" -}}
app.kubernetes.io/component: operate
{{- end -}}

{{/*
Define common labels for operate, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "operate.labels" -}}
{{- template "camundaPlatform.labels" . }}
{{ template "operate.extraLabels" . }}
{{- end -}}

{{/*
Defines match labels for operate, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "operate.matchLabels" -}}
{{- template "camundaPlatform.matchLabels" . }}
{{ template "operate.extraLabels" . }}
{{- end -}}

{{/*
[operate] Create the name of the service account to use
*/}}
{{- define "operate.serviceAccountName" -}}
{{- if .Values.serviceAccount.enabled }}
{{- default (include "operate.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
