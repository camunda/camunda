{{/* vim: set filetype=mustache: */}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "zeebe-gateway.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s-gateway" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Defines extra labels for Zeebe gateway.
*/}}
{{- define "zeebe.extraLabels.gateway" -}}
app.kubernetes.io/component: zeebe-gateway
{{- end -}}

{{/*
Define common labels for Zeebe gateway, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "zeebe.labels.gateway" -}}
{{- template "camundaPlatform.labels" . }}
{{ template "zeebe.extraLabels.gateway" . }}
{{- end -}}

{{/*
Defines match labels for Zeebe gateway, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "zeebe.matchLabels.gateway" -}}
{{- template "camundaPlatform.matchLabels" . }}
{{ template "zeebe.extraLabels.gateway" . }}
{{- end -}}

{{/*
Creates a valid DNS name for the gateway
*/}}
{{- define "zeebe.names.gateway" -}}
{{- $name := default .Release.Name (tpl .Values.global.zeebeClusterName .) -}}
{{- printf "%s-gateway" $name | trunc 63 | trimSuffix "-" | quote -}}
{{- end -}}

{{/*
[zeebe-gateway] Create the name of the service account to use
*/}}
{{- define "zeebe-gateway.serviceAccountName" -}}
{{- if .Values.serviceAccount.enabled }}
{{- default (include "zeebe-gateway.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
