{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "c8-optimize.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "c8-optimize.fullname" -}}
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
Create chart name and version as used by the chart label.
*/}}
{{- define "c8-optimize.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}


{{ define "commonLabels" -}}
{{- toYaml .Values.global.labels -}}
{{ end }}

{{ define "commonAnnotations" -}}
camunda.cloud/created-by: "{{ .Values.git.repoUrl }}/blob/{{ .Values.git.branch }}/.ci/{{ .Template.Name }}"
{{ end }}

{{- define "ingress.domain" -}}
{{- printf "%s-%s.%s" .Release.Name "c8" .Values.ingress.domain | trimPrefix "c8-optimize-" -}}
{{- end -}}
