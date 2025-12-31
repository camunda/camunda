{{/* vim: set filetype=mustache: */}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}

{{/*Pre-validate that inbound mode contains correct values*/}}
{{- $inboundMode := .Values.connectors.inbound.mode -}}
{{- if not (has $inboundMode (list "disabled" "credentials" "oauth")) }}
  {{ fail "Not supported inbound mode" }}
{{- end -}}

{{ define "connectors.zeebeEndpoint" }}
  {{- include "zeebe.names.gateway" . | replace "\"" "" -}}:{{- index .Values "zeebe-gateway" "service" "gatewayPort" -}}
{{- end -}}

{{- define "connectors.fullname" -}}
{{/* TODO: Refactor this when more sub-charts are flatten and moved to the main chart. */}}
    {{- $connectorsValues := deepCopy . -}}
    {{- $_ := set $connectorsValues.Values "nameOverride" "connectors" -}}
    {{- include "camundaPlatform.fullname" $connectorsValues -}}
{{- end -}}

{{/*
Defines extra labels for connectors.
*/}}
{{- define "connectors.extraLabels" -}}
app.kubernetes.io/version: {{ .Chart.Version | quote }}
app.kubernetes.io/component: connectors
{{- end -}}

{{/*
Define common labels for connectors, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "connectors.labels" -}}
{{- template "camundaPlatform.matchLabels" . }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{ template "connectors.extraLabels" . }}
{{- end -}}
{{/*
Defines match labels for connectors, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "connectors.matchLabels" -}}
{{- template "camundaPlatform.matchLabels" . }}
app.kubernetes.io/component: connectors
{{- end -}}
{{/*
[connectors] Create the name of the service account to use
*/}}
{{- define "connectors.serviceAccountName" -}}
{{- if .Values.connectors.serviceAccount.enabled }}
{{- default (include "connectors.fullname" .) .Values.connectors.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.connectors.serviceAccount.name }}
{{- end }}

{{- end }}

{{/*
[connectors] Create the name of the auth credentials
*/}}
{{- define "connectors.authCredentialsSecretName" -}}
{{- $name := .Release.Name -}}
{{- printf "%s-connectors-auth-credentials" $name | trunc 63 | trimSuffix "-" | quote -}}
{{- end }}

{{/*
[connectors] Get the image pull secrets.
*/}}
{{- define "connectors.imagePullSecrets" -}}
{{- include "camundaPlatform.imagePullSecrets" (dict "Values" (set (deepCopy .Values) "image" .Values.connectors.image)) }}
{{- end }}