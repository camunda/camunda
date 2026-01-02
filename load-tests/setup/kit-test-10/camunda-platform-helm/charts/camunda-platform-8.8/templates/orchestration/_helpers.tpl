{{/* vim: set filetype=mustache: */}}

{{/*
[orchestration] Create a default fully qualified app name.
*/}}
{{- define "orchestration.fullname" -}}
    {{- /* NOTE: The value is set to "zeebe" for backward compatibility between 8.7 and 8.8. */ -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "zeebe"
        "componentValues" .Values.orchestration
        "context" $
    ) -}}
{{- end -}}

{{/*
[orchestration] Defines extra labels for orchestration.
*/}}

{{ define "orchestration.componentName" -}}
orchestration
{{- end }}

{{ define "orchestration.brokerName" -}}
{{- /*
    NOTE: The value is set to "zeebe-broker" for backward compatibility between 8.7 and 8.8,
*/ -}}
zeebe-broker
{{- end }}

{{ define "orchestration.gatewayName" -}}
{{- /*
    NOTE: The value is set to "zeebe-gateway" for backward compatibility between 8.7 and 8.8
*/ -}}
zeebe-gateway
{{- end }}

{{- /*
    NOTE: The gateway and broker labels are for backward compatibility between 8.7 and 8.8.
*/ -}}
{{ define "orchestration.gatewayLabel" -}}
app.kubernetes.io/component: {{ include "orchestration.gatewayName" . }}
{{- end }}

{{ define "orchestration.brokerLabel" -}}
app.kubernetes.io/component: {{ include "orchestration.brokerName" . }}
{{- end }}

{{ define "orchestration.versionLabel" -}}
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict
    "base" .Values.global
    "overlay" .Values.orchestration
    "chart" .Chart
) | quote }}
{{- end }}

{{ define "orchestration.extraLabelsGatewayService" -}}
    {{- include "orchestration.gatewayLabel" . }}
    {{- "\n" }}
    {{- include "orchestration.versionLabel" . }}
{{- end }}

{{ define "orchestration.extraLabelsBrokerServiceHeadless" -}}
    {{- include "orchestration.brokerLabel" . }}
    {{- "\n" }}
    {{- include "orchestration.versionLabel" . }}
{{- end }}

{{/*
[orchestration] Defines extra labels for orchestration.
*/}}
{{ define "orchestration.extraLabelsMigration" -}}
app.kubernetes.io/component: {{ printf "%s-migration" (include "orchestration.componentName" .) }}
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict
    "base" .Values.global
    "overlay" .Values.orchestration
    "chart" .Chart
) | quote }}
{{- end }}

{{/*
[orchestration] Defines extra labels for orchestration importer.
*/}}
{{ define "orchestration.extraLabelsImporter" -}}
app.kubernetes.io/component: {{ printf "%s-importer" (include "orchestration.componentName" .) }}
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict
    "base" .Values.global
    "overlay" .Values.orchestration
    "chart" .Chart
) | quote }}
{{- end }}

{{/*
[orchestration] Define common labels for orchestration, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "orchestration.labels" -}}
    {{- include "camundaPlatform.labels" . }}
    {{- "\n" }}
    {{- include "orchestration.brokerLabel" . }}
    {{- "\n" }}
    {{- include "orchestration.versionLabel" . }}
{{- end -}}

{{/*
[orchestration] Define common labels for orchestration cluster migrations, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "orchestration.labelsMigration" -}}
    {{- include "camundaPlatform.labels" . }}
    {{- "\n" }}
    {{- include "orchestration.extraLabelsMigration" . }}
{{- end -}}

{{/*
[orchestration] Define common labels for orchestration importer, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "orchestration.labelsImporter" -}}
    {{- include "camundaPlatform.labels" . }}
    {{- "\n" }}
    {{- include "orchestration.extraLabelsImporter" . }}
{{- end -}}

{{/*
[orchestration] Defines match labels for orchestration, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "orchestration.matchLabels" -}}
    {{- include "camundaPlatform.matchLabels" . }}
    {{- "\n" -}}
    {{/*    For backward compatibility, the component label is set to "zeebe-broker".*/}}
    {{- include "orchestration.brokerLabel" . }}
{{- end -}}

{{/*
[orchestration] Defines match labels for orchestration importer, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "orchestration.matchLabelsImporter" -}}
    {{- include "camundaPlatform.matchLabels" . }}
    {{- "\n" -}}
    app.kubernetes.io/component: {{ printf "%s-importer" (include "orchestration.componentName" .) }}
{{- end -}}

{{/*
[orchestration] Define variables related to multitenancy checks
*/}}
{{- define "orchestration.multitenancyChecksEnabled" -}}
  {{- if .Values.orchestration.multitenancy.checks.enabled -}}
    {{ .Values.orchestration.multitenancy.checks.enabled }}
  {{- else if .Values.global.multitenancy.enabled -}}
    {{ .Values.global.multitenancy.enabled }}
  {{- else -}}
    false
  {{- end -}}
{{- end -}}

{{/*
[orchestration] Define variables related to multitenancy api
*/}}
{{- define "orchestration.multitenancyApiEnabled" -}}
  {{- if .Values.orchestration.multitenancy.api.enabled -}}
    {{ .Values.orchestration.multitenancy.api.enabled }}
  {{- else if .Values.global.multitenancy.enabled -}}
    {{ .Values.global.multitenancy.enabled }}
  {{- else -}}
    false
  {{- end -}}
{{- end -}}

{{/*
[orchestration] Create the name of the service account to use.
*/}}
{{- define "orchestration.serviceAccountName" -}}
    {{- if .Values.orchestration.serviceAccount.enabled -}}
        {{- default (include "orchestration.fullname" .) .Values.orchestration.serviceAccount.name -}}
    {{- else -}}
        {{- default "default" .Values.orchestration.serviceAccount.name -}}
    {{- end -}}
{{- end -}}


{{/*
********************************************************************************
Authentication.
********************************************************************************
*/}}

{{/*
[orchestration] Define variables related to authentication.
*/}}

{{- define "orchestration.authMethod" -}}
    {{- if .Values.orchestration.enabled -}}
        {{- .Values.orchestration.security.authentication.method | default (
            .Values.global.security.authentication.method | default "none"
        ) -}}
    {{- else -}}
        {{- "none" -}}
    {{- end -}}
{{- end -}}

{{- define "orchestration.authEnabled" -}}
    {{- if has (include "orchestration.authMethod" .) (list "oidc" "basic") -}}
        true
    {{- else -}}
        false
    {{- end -}}
{{- end -}}

{{- define "orchestration.authIssuerType" -}}
    {{- .Values.orchestration.security.authentication.oidc.type | default (
        include "camundaPlatform.authIssuerType" .
    ) -}}
{{- end -}}

{{- define "orchestration.authIssuerUrl" -}}
  {{- if .Values.orchestration.security.authentication.oidc.issuer -}}
    {{- .Values.orchestration.security.authentication.oidc.issuer -}}
  {{- else -}}
    {{- include "camundaPlatform.authIssuerUrl" . -}}
  {{- end -}}
{{- end -}}

{{- define "orchestration.authIssuerUrlEndpointAuth" -}}
  {{- if .Values.orchestration.security.authentication.oidc.authUrl -}}
    {{- .Values.orchestration.security.authentication.oidc.authUrl -}}
  {{- else -}}
    {{- include "camundaPlatform.authIssuerUrlEndpointAuth" . -}}
  {{- end -}}
{{- end -}}

{{- define "orchestration.authIssuerBackendUrlEndpointCerts" -}}
    {{- .Values.orchestration.security.authentication.oidc.jwksUrl | default (
        include "camundaPlatform.authIssuerBackendUrlEndpointCerts" .
    ) -}}
{{- end -}}

{{- define "orchestration.authIssuerBackendUrlEndpointToken" -}}
    {{- .Values.orchestration.security.authentication.oidc.tokenUrl | default (
        include "camundaPlatform.authIssuerBackendUrlEndpointToken" .
    ) -}}
{{- end -}}

{{- define "orchestration.authClientId" -}}
    {{- .Values.orchestration.security.authentication.oidc.clientId | default "orchestration" -}}
{{- end -}}

{{- define "orchestration.authAudience" -}}
    {{- .Values.orchestration.security.authentication.oidc.audience | default "orchestration-api" -}}
{{- end -}}

{{- define "orchestration.enabledProfiles" -}}
    {{- $enabledProfiles := list -}}
    {{- range $key, $value := .Values.orchestration.profiles }}
        {{- if eq $value true }}
            {{- $enabledProfiles = append $enabledProfiles $key }}
        {{- end }}
    {{- end }}
    {{- join "," $enabledProfiles }}
{{- end -}}

{{- define "orchestration.enabledProfilesWithIdentity" -}}
    {{- if or
        (eq (include "orchestration.authMethod" .) "oidc")
        (eq (include "orchestration.authMethod" .) "basic")
    }}
        {{- printf "%s,%s" (include "orchestration.enabledProfiles" .) "consolidated-auth" -}}
    {{- else }}
        {{- include "orchestration.enabledProfiles" . | replace "identity" "auth" -}}
    {{- end }}
{{- end -}}

{{- define "orchestration.secondaryStorage" -}}
    {{- if .Values.global.noSecondaryStorage -}}
        none
    {{- else if .Values.global.elasticsearch.enabled -}}
        elasticsearch
    {{- else if .Values.global.opensearch.enabled -}}
        opensearch
    {{- else -}}
        {{- fail "Please enable a secondary storage type. Either Elasticsearch or OpenSearch" -}}
    {{- end -}}
{{- end -}}

{{- define "orchestration.persistentSessionsEnabled" -}}
    {{- not .Values.global.noSecondaryStorage -}}
{{- end -}}


{{/*
********************************************************************************
Service names.
********************************************************************************
*/}}

{{/*
[orchestration] Define Orchestration Cluster service name - Main.
*/}}
{{- define "orchestration.serviceName" }}
    {{- include "orchestration.fullname" . -}}-gateway
{{- end -}}

{{/*
[orchestration] Define Orchestration Cluster service name - Main - gRPC.
*/}}
{{- define "orchestration.serviceNameGRPC" }}
    {{- include "orchestration.serviceName" . -}}:{{ .Values.orchestration.service.grpcPort }}
{{- end -}}

{{/*
[orchestration] Define Orchestration Cluster service name - Main - HTTP.
*/}}
{{- define "orchestration.serviceNameHTTP" }}
    {{- include "orchestration.serviceName" . -}}:{{ .Values.orchestration.service.httpPort }}
{{- end -}}

{{/*
[orchestration] Define Orchestration Cluster service name - Headless.
*/}}
{{- define "orchestration.serviceNameHeadless" }}
    {{- include "orchestration.fullname" . -}}
{{- end -}}

{{/*
[orchestration] Define Orchestration Cluster service name - Headless - gRPC.
*/}}
{{- define "orchestration.serviceNameHeadlessGRPC" }}
    {{- include "orchestration.serviceNameHeadless" . -}}:{{ .Values.orchestration.service.grpcPort }}
{{- end -}}

{{/*
********************************************************************************
Service labels.
********************************************************************************
*/}}

{{/*
[orchestration] Define Orchestration Cluster service labels - Main.
*/}}
{{- define "orchestration.serviceLabels" }}
    {{- include "camundaPlatform.labels" . }}
    {{- "\n" }}
    {{- include "orchestration.extraLabelsGatewayService" . }}
{{- end -}}

{{/*
[orchestration] Define Orchestration Cluster service labels - Headless.
*/}}
{{- define "orchestration.serviceLabelsHeadless" }}
    {{- include "camundaPlatform.labels" . }}
    {{- "\n" }}
    {{- include "orchestration.extraLabelsBrokerServiceHeadless" . }}
{{- end -}}

{{/*
********************************************************************************
URIs.
********************************************************************************
*/}}

{{/*
[orchestration] Orchestration Cluster Redirect URI.
*/}}
{{- define "orchestration.RedirectURI" -}}
    {{- $redirectURIDefault := include "orchestration.serviceNameHTTP" . -}}
    {{- tpl .Values.orchestration.security.authentication.oidc.redirectUrl . | default $redirectURIDefault -}}
{{- end -}}
