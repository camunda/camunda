{{/* vim: set filetype=mustache: */}}

{{/*
********************************************************************************
General.
********************************************************************************
*/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "camundaPlatform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (for example,
by the DNS naming spec). If release name contains chart name it will be used as a full name.
*/}}
{{- define "camundaPlatform.fullname" -}}
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
[camunda-platform] Create a default fully qualified app name for component.

Example:
{{ include "camundaPlatform.componentFullname" (dict "componentName" "foo" "componentValues" .Values.foo "context" $) }}
*/}}
{{- define "camundaPlatform.componentFullname" -}}
    {{- if (.componentValues).fullnameOverride -}}
        {{- .componentValues.fullnameOverride | trunc 63 | trimSuffix "-" -}}
    {{- else -}}
        {{- $name := default .componentName (.componentValues).nameOverride -}}
        {{- if contains $name .context.Release.Name -}}
            {{- .context.Release.Name | trunc 63 | trimSuffix "-" -}}
        {{- else -}}
            {{- printf "%s-%s" .context.Release.Name $name | trunc 63 | trimSuffix "-" -}}
        {{- end -}}
    {{- end -}}
{{- end -}}

{{/*
Define common labels, combining the match labels and transient labels, which might change on updating
(version depending). These labels should not be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "camundaPlatform.labels" -}}
{{- template "camundaPlatform.matchLabels" . }}
{{- if .Values.global.commonLabels }}
{{ tpl (toYaml .Values.global.commonLabels) $ }}
{{- end }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end }}

{{/*
Common match labels, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "camundaPlatform.matchLabels" -}}
{{- if .Values.global.labels -}}
{{ toYaml .Values.global.labels }}
{{- end }}
app.kubernetes.io/name: {{ template "camundaPlatform.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: camunda-platform
{{- end -}}

{{/*
Get image tag according the values of "base" or "overlay" values.
If the "overlay" values exist, they will override the "base" values, otherwise the "base" values will be used.
Usage: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.console) }}
*/}}
{{- define "camundaPlatform.imageTagByParams" -}}
    {{- .overlay.image.tag | default .base.image.tag -}}
{{- end -}}

{{/*
Get image according the values of "base" or "overlay" values.
If the "overlay" values exist, they will override the "base" values, otherwise the "base" values will be used.
Usage: {{ include "camundaPlatform.imageByParams" (dict "base" .Values.global "overlay" .Values.console) }}
*/}}
{{- define "camundaPlatform.imageByParams" -}}
  {{- $imageRegistry    := .overlay.image.registry | default .base.image.registry -}}
  {{- $imageRepository   := .overlay.image.repository | default .base.image.repository -}}
  {{- $imageDigest := .overlay.image.digest | default .base.image.digest | default "" -}}

  {{- if $imageDigest }}
    {{- /* digest‐override path */ -}}
    {{- printf "%s%s%s@%s"
        $imageRegistry
        (empty $imageRegistry | ternary "" "/")
        $imageRepository
        $imageDigest
    -}}
  {{- else }}
    {{- /* original tag path */ -}}
    {{- printf "%s%s%s:%s"
        $imageRegistry
        (empty $imageRegistry | ternary "" "/")
        $imageRepository
        (include "camundaPlatform.imageTagByParams" (dict "base" .base "overlay" .overlay))
    -}}
  {{- end }}
{{- end -}}

{{/*
Get image according the values of "global" or "subchart" values.
Usage: {{ include "camundaPlatform.image" . }}
*/}}
{{- define "camundaPlatform.image" -}}
    {{ include "camundaPlatform.imageByParams" (dict "base" .Values.global "overlay" .Values) }}
{{- end -}}

{{/*
Return the version label for resources.
If an image digest is specified without a tag, fall back to .Chart.AppVersion (e.g., "8.8.x"); otherwise use the resolved image tag.
*/}}
{{- define "camundaPlatform.versionLabel" -}}
  {{- $imageTag := include "camundaPlatform.imageTagByParams" (dict "base" .base "overlay" .overlay) -}}
  {{- $imageDigest := .overlay.image.digest | default .base.image.digest -}}
  {{- if $imageDigest }}
    {{- /* Using digest: fall back to application version for label */ -}}
    {{- .chart.AppVersion -}}
  {{- else if $imageTag }}
    {{- /* Using tag: use the tag for the label */ -}}
    {{- $imageTag -}}
  {{- else }}
    {{- /* Neither tag nor digest provided: use appVersion as default */ -}}
    {{- .chart.AppVersion -}}
  {{- end -}}
{{- end -}}

{{/*
Get imagePullSecrets according the values of global, subchart, or empty.
*/}}
{{- define "camundaPlatform.subChartImagePullSecrets" -}}
    {{- if (.Values.image.pullSecrets) -}}
        {{- .Values.image.pullSecrets | toYaml -}}
    {{- else if (.Values.global.image.pullSecrets) -}}
        {{- .Values.global.image.pullSecrets | toYaml -}}
    {{- else -}}
        {{- "[]" -}}
    {{- end -}}
{{- end -}}

{{/*
Get imagePullSecrets for top-level components.
Usage:
{{ include "camundaPlatform.imagePullSecrets" (dict "component" "zeebe" "context" $) }}
*/}}
{{- define "camundaPlatform.imagePullSecrets" -}}
    {{- $componentValue := (index $.context.Values .component "image" "pullSecrets") -}}
    {{- $globalValue := (index $.context.Values.global "image" "pullSecrets") -}}
    {{- $componentValue | default $globalValue | default list | toYaml -}}
{{- end -}}


{{/*
[camunda-platform] Create labels for secrets shared between Identity and other components.
*/}}
{{- define "camundaPlatform.identityLabels" -}}
{{- if .Values.global.labels -}}
{{ toYaml .Values.global.labels }}
{{- end }}
app.kubernetes.io/name: identity
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: camunda-platform
helm.sh/chart: identity-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/component: identity
{{- end }}

{{/*
[camunda-platform] Create the name of the service account to use
Usage: {{ include "camundaPlatform.serviceAccountName" (dict "component" "operate" "context" $) }}
*/}}
{{- define "camundaPlatform.serviceAccountName" -}}
    {{- $values := (index .context.Values .component) -}}
    {{- if $values.serviceAccount.enabled -}}
        {{- $values.serviceAccount.name | default (include (printf "%s.fullname" .component) .context) -}}
    {{- else -}}
        {{- $values.serviceAccount.name | default "default" -}}
    {{- end -}}
{{- end -}}

{{/*
********************************************************************************
Authentication.
********************************************************************************
*/}}

{{/*
[camunda-platform] Auth issuer public URL which used externally for Camunda apps (with a fallback to publicIssuerUrl).
*/}}
{{- define "camundaPlatform.authIssuerUrlWithFallback" -}}
  {{- if .Values.global.identity.auth.issuer -}}
    {{- .Values.global.identity.auth.issuer -}}
  {{- else -}}
    {{- tpl .Values.global.identity.auth.publicIssuerUrl . -}}
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Auth issuer public URL which used externally for Camunda apps.
*/}}
{{- define "camundaPlatform.authIssuerUrl" -}}
  {{- .Values.global.identity.auth.issuer -}}
{{- end -}}

{{/*
[camunda-platform] Auth issuer backend URL which used internally for Camunda apps.
TODO: Most of the Keycloak config is handeled in Identity sub-chart, but it should be in the main chart.
*/}}
{{- define "camundaPlatform.authIssuerBackendUrl" -}}
  {{- if .Values.global.identity.auth.issuerBackendUrl -}}
    {{- tpl .Values.global.identity.auth.issuerBackendUrl . -}}
  {{- else if eq (include "camundaPlatform.authIssuerType" .) "KEYCLOAK" -}}
    {{- if .Values.global.identity.keycloak.url -}}
      {{-
        printf "%s://%s:%v%s%s"
          .Values.global.identity.keycloak.url.protocol
          .Values.global.identity.keycloak.url.host
          .Values.global.identity.keycloak.url.port
          .Values.global.identity.keycloak.contextPath
          .Values.global.identity.keycloak.realm
      -}}
    {{- else -}}
      {{- include "identity.keycloak.url" . -}}{{- .Values.global.identity.keycloak.realm -}}
    {{- end -}}
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Auth type which used internally for Camunda apps.
NOTE: This is for Management Identity config, all new types will be supported via OIDC.
*/}}
{{- define "camundaPlatform.authIssuerType" -}}
  {{- upper .Values.global.identity.auth.type -}}
{{- end -}}

{{/*
[camunda-platform] Auth URL which used externally by the user.
*/}}
{{- define "camundaPlatform.authIssuerUrlEndpointAuth" -}}
  {{- if or .Values.global.identity.auth.authUrl -}}
    {{- .Values.global.identity.auth.authUrl -}}
  {{- else if eq (include "camundaPlatform.authIssuerType" .) "KEYCLOAK" -}}
    {{- include "camundaPlatform.authIssuerUrlWithFallback" . -}}/protocol/openid-connect/auth
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Auth token URL which used internally for Camunda apps.
*/}}
{{- define "camundaPlatform.authIssuerBackendUrlEndpointToken" -}}
  {{- if .Values.global.identity.auth.tokenUrl -}}
    {{- .Values.global.identity.auth.tokenUrl -}}
  {{- else if eq (include "camundaPlatform.authIssuerType" .) "KEYCLOAK" -}}
    {{- include "camundaPlatform.authIssuerBackendUrl" . -}}/protocol/openid-connect/token
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Auth certs URL which used internally for Camunda apps.
*/}}
{{- define "camundaPlatform.authIssuerBackendUrlEndpointCerts" -}}
  {{- if .Values.global.identity.auth.jwksUrl -}}
    {{- .Values.global.identity.auth.jwksUrl -}}
  {{- else if eq (include "camundaPlatform.authIssuerType" .) "KEYCLOAK" -}}
    {{- include "camundaPlatform.authIssuerBackendUrl" . -}}/protocol/openid-connect/certs
  {{- end -}}
{{- end -}}

{{/*
Get the external url for keycloak
*/}}
{{- define "camundaPlatform.keycloakExternalURL" -}}
  {{ if .Values.identityKeycloak.ingress.enabled -}}
    {{- $proto := ternary "https" "http" .Values.identityKeycloak.ingress.tls -}}
    {{- printf "%s://%s%s" $proto .Values.identityKeycloak.ingress.hostname .Values.identityKeycloak.httpRelativePath -}}
  {{ else if .Values.identityKeycloak.enabled -}}
    {{- $proto := ternary "https" "http" .Values.global.ingress.tls.enabled -}}
    {{- printf "%s://%s%s" $proto (.Values.global.ingress.host | default "localhost:18080") .Values.global.identity.keycloak.contextPath -}}
  {{- end -}}
{{- end -}}


{{/*
********************************************************************************
Elasticsearch and Opensearch templates.
********************************************************************************
*/}}

{{/*
[camunda-platform] Elasticsearch URL which could be external.
*/}}

{{- define "camundaPlatform.elasticsearchHost" -}}
  {{- tpl .Values.global.elasticsearch.url.host $ -}}
{{- end -}}

{{- define "camundaPlatform.elasticsearchURL" -}}
    {{ .Values.global.elasticsearch.url.protocol }}://{{ include "camundaPlatform.elasticsearchHost" . }}:{{ .Values.global.elasticsearch.url.port }}
{{- end -}}

{{- define "camundaPlatform.opensearchHost" -}}
  {{- tpl .Values.global.opensearch.url.host $ -}}
{{- end -}}

{{- define "camundaPlatform.opensearchURL" -}}
    {{ .Values.global.opensearch.url.protocol }}://{{ include "camundaPlatform.opensearchHost" . }}:{{ .Values.global.opensearch.url.port }}
{{- end -}}





{{/*
********************************************************************************
Operate templates.
********************************************************************************
*/}}

{{/*
Get the external url for a given component.
If the "overlay" values exist, they will override the "base" values, otherwise the "base" values will be used.
Usage: {{ include "camundaPlatform.getExternalURL" (dict "component" "operate" "context" .) }}
*/}}
{{- define "camundaPlatform.getExternalURL" -}}
  {{- if (index .context.Values .component "enabled") -}}
    {{- if $.context.Values.global.ingress.enabled -}}
      {{ $proto := ternary "https" "http" .context.Values.global.ingress.tls.enabled -}}
      {{- printf "%s://%s%s" $proto .context.Values.global.ingress.host (index .context.Values .component "contextPath") -}}
    {{- else -}}
      {{- $portMapping := (dict
      "operate" "8081"
      "identity" "8080"
      "tasklist" "8082"
      "optimize" "8083"
      "webapp" "8084"
      "websockets" "8085"
      "console" "8087"
      "connectors" "8086"
      "zeebeGateway" "26500"
      ) -}}
      {{- printf "http://localhost:%s" (get $portMapping .component) -}}
    {{- end -}}
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Operate external URL.
*/}}
{{- define "camundaPlatform.operateExternalURL" }}
  {{- printf "%s/operate" (include "camundaPlatform.orchestrationExternalURL" . | trimSuffix "/") -}}
{{- end -}}


{{/*
********************************************************************************
Optimize templates.
********************************************************************************
*/}}
{{/*
[camunda-platform] Optimize external URL.
*/}}
{{- define "camundaPlatform.optimizeExternalURL" }}
  {{- printf "%s" (include "camundaPlatform.getExternalURL" (dict "component" "optimize" "context" .)) -}}
{{- end -}}

{{/*
********************************************************************************
Connectors templates.
********************************************************************************
*/}}
{{/*
[camunda-platform] Connectors external URL.
*/}}
{{- define "camundaPlatform.connectorsExternalURL" }}
  {{- printf "%s" (include "camundaPlatform.getExternalURL" (dict "component" "connectors" "context" .)) -}}
{{- end -}}

{{/*
********************************************************************************
Tasklist templates.
********************************************************************************
*/}}

{{/*
[camunda-platform] Tasklist external URL.
*/}}
{{- define "camundaPlatform.tasklistExternalURL" }}
  {{- printf "%s/tasklist" (include "camundaPlatform.orchestrationExternalURL" . | trimSuffix "/") -}}
{{- end -}}


{{/*
********************************************************************************
Orchestration Identity templates.
********************************************************************************
*/}}

{{/*
[camunda-platform] Orchestration Identity external URL.
*/}}
{{- define "camundaPlatform.orchestrationIdentityExternalURL" }}
  {{- printf "%s/identity" (include "camundaPlatform.orchestrationExternalURL" . | trimSuffix "/") -}}
{{- end -}}


{{/*
********************************************************************************
Web Modeler templates.
********************************************************************************
*/}}
{{/*
[camunda-platform] Web Modeler external URL.
*/}}

{{- define "camundaPlatform.getExternalURLModeler" -}}
  {{- if .context.Values.webModeler.enabled -}}
    {{- if $.context.Values.global.ingress.enabled -}}
      {{ $proto := ternary "https" "http" .context.Values.global.ingress.tls.enabled -}}
      {{- if eq .component "websockets" }}
        {{- printf "%s://%s%s" $proto .context.Values.global.ingress.host (include "webModeler.websocketContextPath" .context) -}}
      {{- else -}}
        {{- printf "%s://%s%s" $proto .context.Values.global.ingress.host (index .context.Values.webModeler "contextPath") -}}
      {{- end -}}
    {{- else -}}
      {{- if eq .component "websockets" -}}
        {{- printf "http://localhost:8085" -}}
      {{- else -}}
        {{- printf "http://localhost:8084" -}}
      {{- end -}}
    {{- end -}}
  {{- end -}}
{{- end -}}

{{- define "camundaPlatform.webModelerWebSocketsExternalURL" }}
  {{- printf "%s" (include "camundaPlatform.getExternalURLModeler" (dict "component" "websockets" "context" .)) -}}
{{- end -}}

{{- define "camundaPlatform.webModelerWebAppExternalURL" }}
  {{- printf "%s" (include "camundaPlatform.getExternalURLModeler" (dict "component" "webapp" "context" .)) -}}
{{- end -}}


{{/*
********************************************************************************
Identity templates.
********************************************************************************
*/}}

{{- define "identity.authAudience" -}}
  {{- .Values.global.identity.auth.identity.audience | default "camunda-identity-resource-server" -}}
{{- end -}}

{{- define "identity.authClientId" -}}
  {{- .Values.global.identity.auth.identity.clientId | default "camunda-identity" -}}
{{- end -}}


{{/*
Create a default fully qualified app name.
*/}}

{{- define "identity.fullname" -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "identity"
        "componentValues" .Values.identity
        "context" $
    ) -}}
{{- end -}}

{{/*
[camunda-platform] Identity internal URL.
*/}}
{{ define "camundaPlatform.identityURL" }}
  {{- if .Values.global.identity.service.url -}}
    {{- .Values.global.identity.service.url -}}
  {{- else -}}
    {{-
      printf "http://%s:%v%s"
        (include "identity.fullname" .)
        .Values.identity.service.port
        (.Values.identity.contextPath | default "")
    -}}
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Create the name of the Identity secret for components.
Usage: {{ include "camundaPlatform.identitySecretName" (dict "context" . "component" "zeebe") }}
*/}}
{{- define "camundaPlatform.identitySecretName" -}}
  {{- $releaseName := .context.Release.Name | trunc 63 | trimSuffix "-" -}}
  {{- printf "%s-%s-identity-secret" $releaseName .component -}}
{{- end }}

{{/*
[camunda-platform] Identity external URL.
*/}}
{{- define "camundaPlatform.identityExternalURL" }}
  {{- printf "%s" (include "camundaPlatform.getExternalURL" (dict "component" "identity" "context" .)) -}}
{{- end -}}


{{/*
********************************************************************************
Identity Auth.
********************************************************************************
*/}}

{{- define "camundaPlatform.authAudienceOptimize" -}}
  {{- .Values.global.identity.auth.optimize.audience | default "optimize-api" -}}
{{- end -}}


{{/*
********************************************************************************
Console templates.
********************************************************************************
*/}}
{{/*
[camunda-platform] Console external URL.
*/}}
{{- define "camundaPlatform.consoleExternalURL" }}
  {{- printf "%s" (include "camundaPlatform.getExternalURL" (dict "component" "console" "context" .)) -}}
{{- end -}}


{{/*
********************************************************************************
Orchestration templates.
********************************************************************************
*/}}

{{/*
[orchestration] Get the image pull secrets.
*/}}
{{- define "orchestration.imagePullSecrets" -}}
    {{- include "camundaPlatform.imagePullSecrets" (dict
        "component" "orchestration"
        "context" $
    ) -}}
{{- end }}

{{/*
********************************************************************************
Zeebe templates.
********************************************************************************
*/}}
{{/*
[camunda-platform] Zeebe Gateway external URL.
*/}}
{{- define "camundaPlatform.orchestrationExternalURL" }}
  {{- if .Values.global.ingress.enabled -}}
    {{ $proto := ternary "https" "http" .Values.global.ingress.tls.enabled -}}
    {{- printf "%s://%s%s" $proto .Values.global.ingress.host (include "camundaPlatform.joinpath" (list .Values.orchestration.contextPath)) -}}
  {{- else -}}
    {{- printf "http://localhost:8088" -}}
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Zeebe Gateway GRPC external URL.
*/}}
{{- define "camundaPlatform.orchestrationGRPCExternalURL" -}}
  {{ $proto := ternary "https" "http" .Values.orchestration.ingress.grpc.tls.enabled -}}
  {{- printf "%s://%s" $proto (tpl .Values.orchestration.ingress.grpc.host . | default "localhost:26500") -}}
{{- end -}}

{{/*
[camunda-platform] Zeebe Gateway REST internal URL.
*/}}
{{ define "camundaPlatform.orchestrationHTTPInternalURL" }}
  {{- if .Values.orchestration.enabled -}}
    {{-
      printf "http://%s%s"
        (include "orchestration.serviceNameHTTP" .)
        (.Values.orchestration.contextPath | default "")
    -}}
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Zeebe Gateway GRPC internal URL.
*/}}
{{ define "camundaPlatform.orchestrationGRPCInternalURL" }}
  {{- if .Values.orchestration.enabled -}}
    {{-
      printf "grpc://%s"
        (include "orchestration.serviceNameGRPC" .)
    -}}
  {{- end -}}
{{- end -}}


{{/*
********************************************************************************
Release templates.
********************************************************************************
*/}}

{{ define "camundaPlatform.releaseInfo" -}}
- name: {{ .Release.Name }}
  namespace: {{ .Release.Namespace }}
  version: {{ .Chart.Version }}
  tags:
  - dev
  custom-properties: []
  components:
  {{- $proto := ternary "https" "http" .Values.global.ingress.tls.enabled -}}
  {{- $baseURL := printf "%s://%s" $proto .Values.global.ingress.host }}

  {{- if .Values.console.enabled }}
  {{-  $proto := (lower .Values.console.readinessProbe.scheme) -}}
  {{- $baseURLInternal := printf "%s://%s.%s:%v" $proto (include "console.fullname" .) .Release.Namespace .Values.console.service.managementPort }}
  - name: Console
    id: console
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.console) }}
    url: {{ include "camundaPlatform.consoleExternalURL" . }}
    readiness: {{ printf "%s%s" $baseURLInternal .Values.console.readinessProbe.probePath }}
    metrics: {{ printf "%s%s" $baseURLInternal .Values.console.metrics.prometheus }}
  {{- end }}
  {{ if .Values.identity.enabled -}}
  {{-  $proto := (lower .Values.identity.readinessProbe.scheme) -}}
  {{- $baseURLInternal := printf "%s://%s.%s:%v" $proto (include "identity.fullname" .) .Release.Namespace .Values.identity.service.metricsPort -}}
  - name: Keycloak
    id: keycloak
    version: {{ .Values.identityKeycloak.image.tag }}
    url: {{ include "camundaPlatform.keycloakExternalURL" . }}
  - name: Identity
    id: identity
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.identity) }}
    url: {{ include "camundaPlatform.identityExternalURL" . }}
    readiness: {{ printf "%s%s" $baseURLInternal .Values.identity.readinessProbe.probePath }}
    metrics: {{ printf "%s%s" $baseURLInternal .Values.identity.metrics.prometheus }}
  {{- end }}

  {{- if .Values.webModeler.enabled }}
  {{-  $proto := (lower .Values.webModeler.webapp.readinessProbe.scheme) -}}
  {{- $baseURLInternal := printf "%s://%s.%s:%v" $proto (include "webModeler.webapp.fullname" .) .Release.Namespace .Values.webModeler.webapp.service.managementPort }}
  - name: WebModeler WebApp
    id: webModelerWebApp
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.webModeler) }}
    url: {{ include "camundaPlatform.webModelerWebAppExternalURL" . }}
    readiness: {{ printf "%s%s" $baseURLInternal .Values.webModeler.webapp.readinessProbe.probePath }}
    metrics: {{ printf "%s%s" $baseURLInternal .Values.webModeler.webapp.metrics.prometheus }}
  {{- end }}

  {{- if .Values.optimize.enabled }}
  {{-  $proto := (lower .Values.optimize.readinessProbe.scheme) -}}
  {{- $baseURLInternal := printf "%s://%s.%s" $proto (include "optimize.fullname" .) .Release.Namespace }}
  - name: Optimize
    id: optimize
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.optimize) }}
    url: {{ include "camundaPlatform.optimizeExternalURL" . }}
    readiness: {{ printf "%s:%v%s" $baseURLInternal .Values.optimize.service.port (include "camundaPlatform.joinpath" (list .Values.optimize.contextPath .Values.optimize.readinessProbe.probePath)) }}
    metrics: {{ printf "%s:%v%s" $baseURLInternal .Values.optimize.service.managementPort .Values.optimize.metrics.prometheus }}
  {{- end }}

  {{- if .Values.connectors.enabled }}
  {{-  $proto := (lower .Values.connectors.readinessProbe.scheme) -}}
  {{- $baseURLInternal := printf "%s://%s.%s" $proto (include "connectors.serviceName" .) .Release.Namespace }}
  - name: Connectors
    id: connectors
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.connectors) }}
    url: {{ include "camundaPlatform.connectorsExternalURL" . }}
    readiness: {{ printf "%s:%v%s" $baseURLInternal .Values.connectors.service.serverPort (include "camundaPlatform.joinpath" (list .Values.connectors.contextPath .Values.connectors.readinessProbe.probePath)) }}
    metrics: {{ printf "%s:%v%s" $baseURLInternal .Values.connectors.service.serverPort .Values.connectors.metrics.prometheus }}
  {{- end }}

  {{- if .Values.orchestration.enabled }}
  {{-  $proto := (lower .Values.orchestration.readinessProbe.scheme) -}}
  {{- $baseURLInternal := printf "%s://%s.%s:%v" $proto (include "orchestration.fullname" . | trimAll "\"") .Release.Namespace .Values.orchestration.service.managementPort }}
  - name: Operate
    id: operate
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.orchestration) }}
    url: {{ include "camundaPlatform.operateExternalURL" . }}
    readiness: {{ printf "%s%s" $baseURLInternal (include "camundaPlatform.joinpath" (list .Values.orchestration.contextPath .Values.orchestration.readinessProbe.probePath)) }}
    metrics: {{ printf "%s%s" $baseURLInternal (include "camundaPlatform.joinpath" (list .Values.orchestration.contextPath .Values.orchestration.metrics.prometheus)) }}
  - name: Tasklist
    id: tasklist
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.orchestration) }}
    url: {{ include "camundaPlatform.tasklistExternalURL" . }}
    readiness: {{ printf "%s%s" $baseURLInternal (include "camundaPlatform.joinpath" (list .Values.orchestration.contextPath .Values.orchestration.readinessProbe.probePath)) }}
    metrics: {{ printf "%s%s" $baseURLInternal (include "camundaPlatform.joinpath" (list .Values.orchestration.contextPath .Values.orchestration.metrics.prometheus)) }}
  - name: Orchestration Identity
    id: orchestrationIdentity
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.orchestration) }}
    url: {{ include "camundaPlatform.orchestrationIdentityExternalURL" . }}
    readiness: {{ printf "%s%s" $baseURLInternal (include "camundaPlatform.joinpath" (list .Values.orchestration.contextPath .Values.orchestration.readinessProbe.probePath)) }}
    metrics: {{ printf "%s%s" $baseURLInternal (include "camundaPlatform.joinpath" (list .Values.orchestration.contextPath .Values.orchestration.metrics.prometheus)) }}

  - name: Orchestration Cluster
    id: orchestration
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.orchestration) }}
    urls:
      grpc: {{ include "camundaPlatform.orchestrationGRPCExternalURL" . }}
      http: {{ include "camundaPlatform.orchestrationExternalURL" . }}
    readiness: {{ printf "%s%s" $baseURLInternal (include "camundaPlatform.joinpath" (list .Values.orchestration.contextPath .Values.orchestration.readinessProbe.probePath)) }}
    metrics: {{ printf "%s%s" $baseURLInternal (include "camundaPlatform.joinpath" (list .Values.orchestration.contextPath .Values.orchestration.metrics.prometheus)) }}
  {{- end }}
{{- end -}}

{{/*
normalizeSecretConfiguration
Normalizes secret configuration from various input formats to a standardized output format.
Supports both new-style (>= 8.8: secret.existingSecret/secret.inlineSecret) and legacy formats (< 8.8).
Returns a dict with "ref" and "plaintext" keys.
- "ref": dict with "name" and "key" fields for Kubernetes secret reference, or nil if not using secret
- "plaintext": string value for inline plaintext, or empty string if using secret reference
Usage:
  {{ include "camundaPlatform.normalizeSecretConfiguration" (dict
      "config" .Values.identity.firstUser
      "plaintextKey" "password"
      "legacyKeyField" "existingSecretKey"
      "defaultSecretName" "my-default-secret"
      "defaultSecretKey" "password"
  ) }}
*/}}
{{- define "camundaPlatform.normalizeSecretConfiguration" -}}
{{- $config := .config | default dict -}}
{{- $plaintextKey := .plaintextKey | default "password" -}}
{{- $legacyKeyField := .legacyKeyField | default "existingSecretKey" -}}
{{- $defName := .defaultSecretName | default "" -}}
{{- $defKey := .defaultSecretKey | default "password" -}}

{{- $result := dict "ref" nil "plaintext" "" -}}

{{/* New (>= 8.8): existingSecret + existingSecretKey */}}
{{- if and $config.secret $config.secret.existingSecret $config.secret.existingSecretKey -}}
  {{- $_ := set $result "ref" (dict "name" $config.secret.existingSecret "key" $config.secret.existingSecretKey) -}}

{{/* New (>= 8.8): inlineSecret for plaintext values */}}
{{- else if and $config.secret $config.secret.inlineSecret -}}
  {{- $_ := set $result "plaintext" $config.secret.inlineSecret -}}

{{/* Legacy (< 8.8): string + keyField => secret reference */}}
{{- else if and
    (hasKey $config "existingSecret")
    (kindIs "string" $config.existingSecret)
    $config.existingSecret
    (hasKey $config $legacyKeyField)
    (ne (get $config $legacyKeyField | default "") "")
-}}
  {{- $_ := set $result "ref" (dict "name" $config.existingSecret "key" (get $config $legacyKeyField)) -}}

{{/* Legacy (< 8.8): object form for secret reference */}}
{{- else if and
    (hasKey $config "existingSecret")
    (kindIs "map" $config.existingSecret)
    (ne ($config.existingSecret.name | default "") "")
-}}
  {{- $_ := set $result "ref" (dict "name" $config.existingSecret.name "key" (get $config $legacyKeyField)) -}}

{{/* Legacy (< 8.8): string fallback for plaintext values */}}
{{- else if and
    (hasKey $config "existingSecret")
    (kindIs "string" $config.existingSecret)
    $config.existingSecret
-}}
  {{- $_ := set $result "plaintext" $config.existingSecret -}}

{{/* Fallback: direct plaintext key */}}
{{- else if (hasKey $config $plaintextKey) -}}
  {{- $_ := set $result "plaintext" (get $config $plaintextKey | default "") -}}
{{- end }}

{{/* Final fallback to the caller‑supplied default */}}
{{- if and (not $result.ref) (not $result.plaintext) $defName -}}
  {{- $_ := set $result "ref" (dict "name" $defName "key" $defKey) -}}
{{- end }}

{{- toYaml $result -}}
{{- end -}}

{{/*
emitEnvVarFromSecretConfig
Usage:
  {{ include "camundaPlatform.emitEnvVarFromSecretConfig" (dict
      "envName" "VALUES_IDENTITY_FIRSTUSER_PASSWORD"
      "config"  .Values.identity.firstUser
      "plaintextKey" "password"
      "legacyKeyField" "existingSecretKey"
  ) }}
*/}}
{{- define "camundaPlatform.emitEnvVarFromSecretConfig" -}}
{{- $norm := include "camundaPlatform.normalizeSecretConfiguration" . | fromYaml -}}
{{- if or $norm.ref $norm.plaintext -}}
- name: {{ .envName }}
{{- if $norm.ref }}
  valueFrom:
    secretKeyRef:
      name: {{ $norm.ref.name }}
      key: {{ $norm.ref.key }}
{{- else }}
  value: {{ $norm.plaintext | quote }}
{{- end }}
{{- end -}}
{{- end -}}

{{/*
hasSecretConfig
Returns a string indicating whether there is a valid secret configuration.
Named templates don't return bools, only strings [1].
Usage:
  {{ if eq (include "camundaPlatform.hasSecretConfig" (dict
      "config"  .Values.identity.firstUser
      "plaintextKey" "password"
      "legacyKeyField" "existingSecretKey"
  )) "true" }}

[1] https://github.com/helm/helm/issues/11231
*/}}
{{- define "camundaPlatform.hasSecretConfig" -}}
{{- $norm := include "camundaPlatform.normalizeSecretConfiguration" . | fromYaml -}}
{{- if or $norm.ref $norm.plaintext -}}
true
{{- else -}}
false
{{- end -}}
{{- end -}}

{{/*
emitAwsDocumentStoreSecret
Emits AWS Document Store environment variable handling both legacy and new secret patterns.
Prioritizes new pattern over legacy pattern.
Usage:
  - name: AWS_ACCESS_KEY_ID
    {{ include "camundaPlatform.emitAwsDocumentStoreSecret" (dict "secretType" "accessKeyId" "context" .) }}
  - name: AWS_SECRET_ACCESS_KEY
    {{ include "camundaPlatform.emitAwsDocumentStoreSecret" (dict "secretType" "secretAccessKey" "context" .) }}
*/}}
{{- define "camundaPlatform.emitAwsDocumentStoreSecret" -}}
{{- $root := .context -}}
{{- if $root.Values.global.documentStore.type.aws.enabled -}}
{{- $awsConfig := $root.Values.global.documentStore.type.aws -}}
{{- $secretType := .secretType -}}
{{- $legacyKey := "" -}}
{{- if eq $secretType "accessKeyId" -}}
  {{- $legacyKey = $awsConfig.accessKeyIdKey -}}
{{- else if eq $secretType "secretAccessKey" -}}
  {{- $legacyKey = $awsConfig.secretAccessKeyKey -}}
{{- end -}}
{{/* New pattern - prioritize over legacy */}}
{{- $secretConfig := (index $awsConfig $secretType) | default dict -}}
{{- if and $secretConfig.secret (or $secretConfig.secret.existingSecret $secretConfig.secret.inlineSecret) -}}
{{- if and $secretConfig.secret.existingSecret $secretConfig.secret.existingSecretKey -}}
valueFrom:
  secretKeyRef:
    name: {{ $secretConfig.secret.existingSecret }}
    key: {{ $secretConfig.secret.existingSecretKey }}
{{- else if $secretConfig.secret.inlineSecret -}}
value: {{ $secretConfig.secret.inlineSecret | quote }}
{{- end -}}
{{/* Legacy pattern - fallback */}}
{{- else if and $awsConfig.existingSecret $legacyKey -}}
valueFrom:
  secretKeyRef:
    name: {{ $awsConfig.existingSecret | quote }}
    key: {{ $legacyKey | quote }}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
emitVolumeFromSecretConfig
Emits volume definition using normalized secret configuration.
Usage:
  {{ include "camundaPlatform.emitVolumeFromSecretConfig" (dict
      "volumeName" "gcp-credentials-volume"
      "config" .Values.global.documentStore.type.gcp
      "legacyKeyField" "credentialsKey"
      "fileName" (.Values.global.documentStore.type.gcp.fileName | default "service-account.json")
  ) }}
*/}}
{{- define "camundaPlatform.emitVolumeFromSecretConfig" -}}
{{- $norm := include "camundaPlatform.normalizeSecretConfiguration" . | fromYaml -}}
{{- if $norm.ref }}
- name: {{ .volumeName }}
  secret:
    secretName: {{ $norm.ref.name | quote }}
    items:
      - key: {{ $norm.ref.key | quote }}
        path: {{ .fileName | quote }}
{{- end }}
{{- end -}}

{{/*
shouldAutogenerateSecret
Determines whether a secret should be autogenerated for a given component configuration.
Returns "true" if autogeneration should occur, "false" otherwise.

This function handles both legacy (< 8.8) and new (>= 8.8) secret configuration patterns:
1. If the component has no secret configuration at all -> autogenerate
2. If the component explicitly references the autogenerated secret name -> autogenerate
3. Otherwise -> do not autogenerate (user has their own secret config)

Note: This helper assumes it's called within the context where global autogeneration is enabled,
since the secret template only renders when .Values.global.secrets.autoGenerated is true.

Usage:
  {{ if eq (include "camundaPlatform.shouldAutogenerateSecret" (dict
      "config" .Values.identity.firstUser
      "autogeneratedSecretName" .Values.global.secrets.name
      "plaintextKey" "password"
      "legacyKeyField" "existingSecretKey"
  )) "true" }}

Parameters:
- config: The component's configuration object
- autogeneratedSecretName: The name of the autogenerated secret
- plaintextKey: The key to check for plaintext values (optional)
- legacyKeyField: The legacy key field name (optional, defaults to "existingSecretKey")
*/}}
{{- define "camundaPlatform.shouldAutogenerateSecret" -}}
{{- $config := .config | default dict -}}
{{- $autogenSecretName := .autogeneratedSecretName | default "" -}}
{{- $plaintextKey := .plaintextKey | default "password" -}}
{{- $legacyKeyField := .legacyKeyField | default "existingSecretKey" -}}

{{- $result := "false" -}}

{{/* Check if component has no secret configuration */}}
{{- $hasSecretConfig := include "camundaPlatform.hasSecretConfig" (dict
    "config" $config
    "plaintextKey" $plaintextKey
    "legacyKeyField" $legacyKeyField
) -}}

{{- if eq $hasSecretConfig "false" -}}
  {{/* No secret config found -> autogenerate */}}
  {{- $result = "true" -}}
{{- else -}}
  {{/* Check if component explicitly references the autogenerated secret name in any possible field */}}
  {{- if or 
      (and $config.existingSecret (kindIs "string" $config.existingSecret) (eq (toString $config.existingSecret) (toString $autogenSecretName)))
      (and $config.existingSecret (kindIs "map" $config.existingSecret) (eq (toString $config.existingSecret.name) (toString $autogenSecretName)))
      (and $config.secret $config.secret.existingSecret (eq (toString $config.secret.existingSecret) (toString $autogenSecretName)))
      (and $config.auth $config.auth.existingSecret (eq (toString $config.auth.existingSecret) (toString $autogenSecretName)))
      (and $config.postgresql $config.postgresql.auth $config.postgresql.auth.existingSecret (eq (toString $config.postgresql.auth.existingSecret) (toString $autogenSecretName)))
  -}}
    {{- $result = "true" -}}
  {{- end -}}
{{- end -}}

{{- $result -}}
{{- end -}}

{{/*
emitTlsVolumeFromSecretConfig
Emits volume definition for TLS secrets.
Handles both legacy (< 8.9) and new (>= 8.9) secret patterns.
Usage:
  {{ include "camundaPlatform.emitTlsVolumeFromSecretConfig" (dict
      "volumeName" "keystore"
      "config" .Values.global.elasticsearch.tls
  ) }}
*/}}
{{- define "camundaPlatform.emitTlsVolumeFromSecretConfig" -}}
{{- $config := .config | default dict -}}
{{- $secretName := "" -}}

{{/* New (>= 8.9): config.secret.existingSecret */}}
{{- if and $config.secret $config.secret.existingSecret -}}
  {{- $secretName = $config.secret.existingSecret -}}
{{/* Legacy (< 8.9): config.existingSecret */}}
{{- else if and $config.existingSecret (kindIs "string" $config.existingSecret) -}}
  {{- $secretName = $config.existingSecret -}}
{{- end -}}

{{- if $secretName }}
- name: {{ .volumeName }}
  secret:
    secretName: {{ $secretName | quote }}
    optional: false
{{- end }}
{{- end -}}

{{/*
getTlsSecretKey
Returns the secret key name from TLS config.
New pattern uses config.secret.existingSecretKey, legacy defaults to "externaldb.jks".
Accepts root context (.) and uses the enabled database type (ES or OS).
Usage:
  {{ include "camundaPlatform.getTlsSecretKey" . }}
  {{ include "camundaPlatform.getTlsSecretKey" (dict "config" .Values.global.elasticsearch.tls) }}
*/}}
{{- define "camundaPlatform.getTlsSecretKey" -}}
{{- $config := dict -}}

{{/* If caller passes .config dict, use it directly for backwards compatibility */}}
{{- if .config -}}
  {{- $config = .config -}}
{{/* Otherwise, determine which database TLS config to use from root context */}}
{{- else if .Values -}}
  {{/* Use OpenSearch if enabled, otherwise Elasticsearch */}}
  {{- if .Values.global.opensearch.enabled -}}
    {{- $config = .Values.global.opensearch.tls -}}
  {{- else -}}
    {{- $config = .Values.global.elasticsearch.tls -}}
  {{- end -}}
{{- end -}}

{{- $secretKey := "" -}}
{{/* New (>= 8.9): config.secret.existingSecretKey */}}
{{- if and $config.secret $config.secret.existingSecretKey -}}
  {{- $secretKey = $config.secret.existingSecretKey -}}
{{/* Legacy (< 8.9): config.existingSecret - use hardcoded default */}}
{{- else if and $config.existingSecret (kindIs "string" $config.existingSecret) -}}
  {{- $secretKey = "externaldb.jks" -}}
{{- end -}}
{{- $secretKey -}}
{{- end -}}

{{/*
********************************************************************************
Release highlights.
********************************************************************************
*/}}

{{- define "camundaPlatform.ReleaseHighlights" }}
## [info] Helm chart release highlights
- Some values have been renamed or moved in the new chart structure.
- When upgraded from 8.7 to 8.8, manual adjustments may be required for some cases like custom configurations.
- Please refer to the official docs for more details.
https://docs.camunda.io/docs/next/self-managed/installation-methods/helm/upgrade/upgrade-hc-870-880/
{{- end -}}
