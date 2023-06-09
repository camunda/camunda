{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "c7-optimize.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "c7-optimize.fullname" -}}
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
{{- define "c7-optimize.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}


{{ define "commonLabels" -}}
{{- toYaml .Values.global.labels -}}
{{ end }}

{{ define "commonAnnotations" -}}
camunda.cloud/created-by: "{{ .Values.git.repoUrl }}/blob/{{ .Values.git.branch }}/.ci/{{ .Template.Name }}"
{{ end }}

{{- define "ingress.domain" -}}
{{- printf "%s.%s" .Release.Name .Values.ingress.domain | trimPrefix "optimize-" -}}
{{- end -}}

# defining environment variables for stage environment
# This way the deployment scripts are cleaner and simpler
# This also was the solution after trying to pass the env var from the command line and with the different special
# Characters that we have here, it was hard to do it using bash.

{{- define "javaOpts" -}}
-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m
{{- end -}}


{{- define "postgresUrl" -}}
stage-postgres.optimize:5432
{{- end -}}

{{- define "elasticsearchUrl" -}}
localhost:9300
{{- end -}}
