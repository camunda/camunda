{{/* vim: set filetype=mustache: */}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}

{{ define "connectors.zeebeEndpoint" }}
  {{- include "orchestration.fullname" . | replace "\"" "" -}}:{{- .Values.orchestration.service.grpcPort -}}
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
[connectors] Get the image pull secrets.
*/}}
{{- define "connectors.imagePullSecrets" -}}
{{- include "camundaPlatform.subChartImagePullSecrets" (dict "Values" (set (deepCopy .Values) "image" .Values.connectors.image)) }}
{{- end }}

{{/*
[connectors] Service name.
*/}}
{{- define "connectors.serviceName" -}}
  {{ include "connectors.fullname" . }}
{{- end }}

{{- define "connectors.serviceHeadlessName" -}}
  {{ include "connectors.fullname" . }}-headless
{{- end }}


{{/*
********************************************************************************
Authentication.
********************************************************************************
*/}}

{{/*
[connectors] Define variables related to authentication.
*/}}

{{- define "connectors.authMethod" -}}
    {{- if not .Values.connectors.enabled -}}
        none
    {{- else -}}
        {{- .Values.connectors.security.authentication.method | default (
            .Values.global.security.authentication.method | default "none"
        ) -}}
    {{- end -}}
{{- end -}}

{{/*
[connectors] Defines the auth client
*/}}
{{- define "connectors.authClientId" -}}
    {{- .Values.connectors.security.authentication.oidc.clientId -}}
{{- end }}

{{- define "connectors.authAudience" -}}
    {{- .Values.connectors.security.authentication.oidc.audience |
      default (include "orchestration.authAudience" .)
    -}}
{{- end -}}

{{- define "connectors.authTokenScope" -}}
    {{- .Values.connectors.security.authentication.oidc.tokenScope -}}
{{- end -}}
