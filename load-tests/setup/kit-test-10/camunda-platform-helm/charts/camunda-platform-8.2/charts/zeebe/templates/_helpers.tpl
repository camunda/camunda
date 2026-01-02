{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "zeebe.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "zeebe.fullname" -}}
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
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "zeebe-gateway.fullname" -}}
{{- if .Values.gateway.fullnameOverride -}}
{{- .Values.gateway.fullnameOverride | trunc 63 | trimSuffix "-" -}}
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
Defines extra labels for Zeebe broker.
*/}}
{{- define "zeebe.extraLabels.broker" -}}
app.kubernetes.io/component: zeebe-broker
{{- end -}}

{{/*
Define common labels for Zeebe broker, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "zeebe.labels.broker" -}}
{{- template "camundaPlatform.labels" . }}
{{ template "zeebe.extraLabels.broker" . }}
{{- end -}}

{{/*
Defines match labels for Zeebe broker, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "zeebe.matchLabels.broker" -}}
{{- template "camundaPlatform.matchLabels" . }}
{{ template "zeebe.extraLabels.broker" . }}
{{- end -}}

{{/*
Common names
*/}}
{{- define "zeebe.names.broker" -}}
{{- if .Values.global.zeebeClusterName -}}
{{- tpl .Values.global.zeebeClusterName . | trunc 63 | trimSuffix "-" | quote -}}
{{- else -}}
{{- printf "%s-broker" .Release.Name | trunc 63 | trimSuffix "-" | quote -}}
{{- end -}}
{{- end -}}

{{/*
[zeebe] Create the name of the service account to use
*/}}
{{- define "zeebe.serviceAccountName" -}}
{{- if .Values.serviceAccount.enabled }}
{{- default (include "zeebe.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
