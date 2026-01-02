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
  {{- include "zeebe.names.gateway" . | replace "\"" "" -}}:{{- .Values.zeebeGateway.service.grpcPort -}}
{{- end -}}

{{- define "connectors.fullname" -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "connectors"
        "componentValues" .Values.connectors
        "context" $
    ) -}}
{{- end -}}

{{/*
Defines extra labels for connectors.
*/}}
{{- define "connectors.extraLabels" -}}
app.kubernetes.io/component: connectors
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict "base" .Values.global "overlay" .Values.connectors "chart" .Chart) | quote }}
{{- end -}}

{{/*
Define common labels for connectors, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "connectors.labels" -}}
{{- template "camundaPlatform.labels" . }}
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
    {{- include "camundaPlatform.serviceAccountName" (dict
        "component" "connectors"
        "context" $
    ) -}}
{{- end -}}

{{/*
[connectors] Create the name of the auth credentials
*/}}
{{- define "connectors.authCredentialsSecretName" -}}
{{- $name := .Release.Name -}}
{{- printf "%s-connectors-auth-credentials" $name | trunc 63 | trimSuffix "-" | quote -}}
{{- end }}

{{/*
[connectors] Defines the auth client
*/}}
{{- define "connectors.authClientId" -}}
  {{- .Values.global.identity.auth.connectors.clientId -}}
{{- end }}

{{/*
[connectors] Get the image pull secrets.
*/}}
{{- define "connectors.imagePullSecrets" -}}
{{- include "camundaPlatform.subChartImagePullSecrets" (dict "Values" (set (deepCopy .Values) "image" .Values.connectors.image)) }}
{{- end }}
