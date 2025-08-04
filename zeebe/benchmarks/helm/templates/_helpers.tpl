{{/*
Expand the name of the chart.
*/}}
{{ define "bench.name" -}}
  {{- default .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{ define "bench.fullname" -}}
  {{- $name := default .Chart.Name }}
  {{- if contains $name .Release.Name }}
    {{- .Release.Name | trunc 63 | trimSuffix "-" }}
  {{- else }}
    {{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
  {{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{ define "bench.chart" -}}
  {{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Define common labels, combining the match labels and transient labels, which might change on updating
(version depending). These labels should not be used on matchLabels selector, since the selectors are immutable.
*/}}
{{ define "bench.labels" -}}
{{- template "bench.matchLabels" . }}
{{- if .Values.labels }}
{{ tpl (toYaml .Values.labels) $ }}
{{- end }}
helm.sh/chart: {{ include "bench.chart" . }}
{{- end }}

{{/*
Common match labels, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{ define "bench.matchLabels" -}}
app.kubernetes.io/name: {{ include "bench.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: camunda-benchmark
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{ define "bench.serviceAccountName" -}}
  {{- if .Values.serviceAccount }}
    {{- .Values.serviceAccount }}
  {{- else }}
    {{- default "default" .Values.serviceAccount }}
  {{- end }}
{{- end }}

{{/*
Returns the actuator address for a gateway, only supported for SM clusters.
*/}}
{{ define "bench.gatewayActuatorAddress" -}}
  {{- if (eq .Values.cluster.type "sm") -}}
    {{- urlJoin (dict
      "host" (printf "%s:%v" .Values.cluster.sm.host .Values.cluster.sm.actuatorPort)
      "path" (printf "%s/actuator" (trimSuffix "/" .Values.cluster.sm.contextPath))
      "scheme" .Values.cluster.sm.scheme
    ) -}}
  {{- else }}
    {{- fail (cat "Actuator address is only support for 'sm' cluster types, but got:" .Values.cluster.type) }}
  {{- end }}
{{- end }}

{{/*
Returns the REST API address for a gateway.
*/}}
{{ define "bench.gatewayRestAddress" -}}
  {{- if eq .Values.cluster.type "sm" -}}
    {{- urlJoin (dict
      "host" (printf "%s:%v" .Values.cluster.sm.host .Values.cluster.sm.restPort)
      "path" .Values.cluster.sm.contextPath
      "scheme" .Values.cluster.sm.scheme
    ) -}}
  {{- else if eq .Values.cluster.type "saas" -}}
    {{- urlJoin (dict
      "host" (printf "%s.%s" .Values.cluster.saas.region (include "bench.saasAudience" .))
      "path" .Values.cluster.saas.clusterId
      "scheme" "https"
    ) -}}
  {{- else }}
    {{- fail "Expected a cluster type of 'sm' or 'saas', but got: " .Values.cluster.type }}
  {{- end }}
{{- end }}

{{/*
Returns the REST API address for a gateway.
*/}}
{{ define "bench.gatewayGrpcAddress" -}}
  {{- if eq .Values.cluster.type "sm" -}}
    {{- urlJoin (dict
      "host" (printf "%s:%v" .Values.cluster.sm.host .Values.cluster.sm.grpcPort)
      "scheme" .Values.cluster.sm.scheme
    ) -}}
  {{- else if eq .Values.cluster.type "saas" -}}
    {{- urlJoin (dict
      "host" (printf "%s.%s.%s" .Values.cluster.saas.clusterId .Values.cluster.saas.region (include "bench.saasAudience" .))
      "scheme" "https"
    ) -}}
  {{- else }}
    {{- fail "Expected a cluster type of 'sm' or 'saas', but got: " .Values.cluster.type }}
  {{- end }}
{{- end }}

{{/*
Returns the authorization server URL for a SaaS cluster.
*/}}
{{ define "bench.saasAuthzServerUrl" -}}
  {{- if eq .Values.cluster.type "saas" -}}
    {{- if eq .Values.cluster.saas.stage "prod" -}}
      {{- "https://weblogin.cloud.camunda.io/oauth/token" -}}
    {{- else if eq .Values.cluster.saas.stage "int" -}}
      {{- "https://weblogin.cloud.ultrawombat.com/oauth/token" -}}
    {{- else if eq .Values.cluster.saas.stage "dev" -}}
      {{- "https://weblogin.cloud.dev.ultrawombat.com/oauth/token" -}}
    {{- else }}
      {{- fail (cat "Expected a SaaS stage of 'prod', 'int', or 'dev', but got:" .Values.cluster.saas.stage) }}
    {{- end }}
  {{- else }}
    {{- fail "Expected a cluster type of 'saas', but got: " .Values.cluster.type }}
  {{- end }}
{{- end }}

{{/*
Returns the audience for a SaaS cluster.
*/}}
{{ define "bench.saasAudience" -}}
  {{- if eq .Values.cluster.type "saas" -}}
    {{- if eq .Values.cluster.saas.stage "prod" -}}
      {{- "zeebe.camunda.io" -}}
    {{- else if eq .Values.cluster.saas.stage "int" -}}
      {{- "zeebe.ultrawombat.com" -}}
    {{- else if eq .Values.cluster.saas.stage "dev" -}}
      {{- "zeebe.dev.ultrawombat.com" -}}
    {{- else }}
      {{- fail (cat "Expected a SaaS stage of 'prod', 'int', or 'dev', but got:" .Values.cluster.saas.stage) }}
    {{- end }}
  {{- else }}
    {{- fail "Expected a cluster type of 'saas', but got: " .Values.cluster.type }}
  {{- end }}
{{- end }}

{{ define "bench.tlsEnabled" -}}
{{- if eq .Values.cluster.type "saas" -}}
true
{{- else if eq .Values.cluster.type "sm" -}}
{{- eq "https" .Values.cluster.sm.scheme -}}
{{- else -}}
{{- fail (cat "Expected cluster.type to be one of 'saas', or 'sm', but got:" .Values.cluster.type) -}}
{{- end -}}
{{- end -}}
