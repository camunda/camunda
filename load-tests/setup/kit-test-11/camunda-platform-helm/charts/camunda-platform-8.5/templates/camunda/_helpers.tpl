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
If an image digest is specified without a tag, fall back to .Chart.AppVersion (e.g., “8.5.x”); otherwise use the resolved image tag.
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
[camunda-platform] Joins an arbirtary number of subpaths (e.g., contextPath+probePath) for HTTP paths.
Slashes are trimmed from the beginning and end of each part, and a single slash is inserted between parts, leading slash added at the beginning.
Usage: {{ include "camundaPlatform.joinpath" (list .Values.zeebe.contextPath .Values.zeebe.readinessProbe.probePath) }}
*/}}
{{- define "camundaPlatform.joinpath" -}}
  {{- $paths := join "/" . -}}
  {{- $pathsSanitized := regexReplaceAll "/+" $paths "/" | trimAll "/" }}
  {{- printf "/%s" $pathsSanitized -}}
{{- end -}}

{{/*
********************************************************************************
Keycloak templates.
********************************************************************************
*/}}

{{/*
[camunda-platform] Keycloak issuer public URL which used externally for Camunda apps.
*/}}
{{- define "camundaPlatform.authIssuerUrl" -}}
  {{- if .Values.global.identity.auth.issuer -}}
    {{- .Values.global.identity.auth.issuer -}}
  {{- else -}}
    {{- tpl .Values.global.identity.auth.publicIssuerUrl . -}}
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Keycloak issuer backend URL which used internally for Camunda apps.
TODO: Most of the Keycloak config is handeled in Identity sub-chart, but it should be in the main chart.
*/}}
{{- define "camundaPlatform.authIssuerBackendUrl" -}}
  {{- if .Values.global.identity.auth.issuerBackendUrl -}}
    {{- .Values.global.identity.auth.issuerBackendUrl -}}
  {{- else -}}
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
[camunda-platform] Identity auth type which used internally for Camunda apps.
*/}}
{{- define "camundaPlatform.authType" -}}
  {{- .Values.global.identity.auth.type -}}
{{- end -}}

{{/*
[camunda-platform] Keycloak auth token URL which used internally for Camunda apps.
*/}}
{{- define "camundaPlatform.authIssuerBackendUrlTokenEndpoint" -}}
  {{- if .Values.global.identity.auth.tokenUrl -}}
    {{- .Values.global.identity.auth.tokenUrl -}}
  {{- else -}}
    {{- include "camundaPlatform.authIssuerBackendUrl" . -}}/protocol/openid-connect/token
  {{- end -}}
{{- end -}}


{{/*
[camunda-platform] Keycloak auth certs URL which used internally for Camunda apps.
*/}}
{{- define "camundaPlatform.authIssuerBackendUrlCertsEndpoint" -}}
  {{- if .Values.global.identity.auth.jwksUrl -}}
    {{- .Values.global.identity.auth.jwksUrl -}}
  {{- else -}}
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
[elasticsearch] Get name of elasticsearch auth existing secret. For more details:
https://docs.bitnami.com/kubernetes/apps/keycloak/configuration/manage-passwords/
*/}}
{{- define "elasticsearch.authExistingSecret" -}}
    {{- if .Values.global.elasticsearch.auth.existingSecret }}
        {{- .Values.global.elasticsearch.auth.existingSecret -}}
    {{- else -}}
        {{ include "camundaPlatform.fullname" . }}-elasticsearch
    {{- end }}
{{- end -}}

{{/*
[elasticsearch] Get elasticsearch auth existing secret key.
*/}}
{{- define "elasticsearch.authExistingSecretKey" -}}
    {{- if .Values.global.elasticsearch.auth.existingSecretKey }}
        {{- .Values.global.elasticsearch.auth.existingSecretKey -}}
    {{- else -}}
        password
    {{- end }}
{{- end -}}

{{/*
[elasticsearch] Used as a boolean to determine whether any password is defined.
do not use this for its string value.
*/}}
{{- define "elasticsearch.passwordIsDefined" -}}
{{- (cat .Values.global.elasticsearch.auth.existingSecret .Values.global.elasticsearch.auth.password) -}}
{{- end -}}


{{/*
[opensearch] Get name of opensearch auth existing secret. For more details:
https://docs.bitnami.com/kubernetes/apps/keycloak/configuration/manage-passwords/
*/}}
{{- define "opensearch.authExistingSecret" -}}
    {{- if .Values.global.opensearch.auth.existingSecret }}
        {{- .Values.global.opensearch.auth.existingSecret -}}
    {{- else -}}
        {{ include "camundaPlatform.fullname" . }}-opensearch
    {{- end }}
{{- end -}}

{{/*
[opensearch] Get opensearch auth existing secret key.
*/}}
{{- define "opensearch.authExistingSecretKey" -}}
    {{- if .Values.global.opensearch.auth.existingSecretKey }}
        {{- .Values.global.opensearch.auth.existingSecretKey -}}
    {{- else -}}
        password
    {{- end }}
{{- end -}}

{{/*
********************************************************************************
Operate templates.
********************************************************************************
*/}}

{{/*
[camunda-platform] Operate internal URL.
*/}}
{{ define "camundaPlatform.operateURL" }}
  {{- if .Values.operate.enabled -}}
    {{- print "http://" -}}{{- include "operate.fullname" . -}}:{{- .Values.operate.service.port -}}
    {{- .Values.operate.contextPath -}}
  {{- end -}}
{{- end -}}


{{/*
Get the external url for a given component.
If the "overlay" values exist, they will override the "base" values, otherwise the "base" values will be used.
Usage: {{ include "camundaPlatform.getExternalURL" (dict "component" "operate" "context" .) }}
*/}}
{{- define "camundaPlatform.getExternalURL" -}}
  {{- if (index .context.Values .component "enabled") -}}
    {{- if (index .context.Values .component "ingress" "enabled") }}
      {{- $proto := ternary "https" "http" (index .context.Values .component "ingress" "tls" "enabled") -}}
      {{- printf "%s://%s" $proto (index .context.Values .component "ingress" "host") -}} 
    {{- else if $.context.Values.global.ingress.enabled -}}
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
  {{- printf "%s" (include "camundaPlatform.getExternalURL" (dict "component" "operate" "context" .)) -}}
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
Tasklist templates.
********************************************************************************
*/}}
{{/*
[camunda-platform] Tasklist external URL.
*/}}
{{- define "camundaPlatform.tasklistExternalURL" }}
  {{- printf "%s" (include "camundaPlatform.getExternalURL" (dict "component" "tasklist" "context" .)) -}}
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
    {{- $ingress := .context.Values.webModeler.ingress }}
    {{- if index $ingress "enabled" }}
      {{- $proto := ternary "https" "http" (index $ingress .component "tls" "enabled") -}}
      {{- printf "%s://%s" $proto (index $ingress .component "host") -}} 
    {{- else if $.context.Values.global.ingress.enabled -}}
      {{ $proto := ternary "https" "http" .context.Values.global.ingress.tls.enabled -}}
      {{- if eq .component "websockets" }}
        {{- printf "%s://%s%s" $proto .context.Values.global.ingress.host (include "webModeler.websocketContextPath" .context) -}} 
      {{- else -}}
        {{- printf "%s://%s%s" $proto .context.Values.global.ingress.host (index .context.Values.webModeler "contextPath") -}} 
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
Zeebe templates.
********************************************************************************
*/}}
{{/*
[camunda-platform] Zeebe Gateway external URL.
*/}}
{{- define "camundaPlatform.zeebeGatewayExternalURL" }}
  {{- if .Values.global.ingress.enabled -}}
    {{ $proto := ternary "https" "http" .Values.global.ingress.tls.enabled -}}
    {{- printf "%s://%s%s" $proto .Values.global.ingress.host .Values.zeebeGateway.contextPath -}}
  {{- else if .Values.zeebeGateway.ingress.rest.enabled -}}
    {{ $proto := ternary "https" "http" .Values.zeebeGateway.ingress.rest.tls.enabled -}}
    {{- printf "%s://%s%s" $proto .Values.zeebeGateway.ingress.rest.host .Values.zeebeGateway.contextPath -}} 
  {{- else -}}
    {{- printf "http://localhost:8088" -}}
  {{- end -}}
{{- end -}}

{{/*
[camunda-platform] Zeebe Gateway GRPC external URL.
*/}}
{{- define "camundaPlatform.zeebeGatewayGRPCExternalURL" -}}
  {{ $proto := ternary "https" "http" .Values.zeebeGateway.ingress.grpc.tls.enabled -}}
  {{- printf "%s://%s" $proto (tpl .Values.zeebeGateway.ingress.grpc.host . | default "localhost:26500") -}}
{{- end -}}

{{/*
[camunda-platform] Zeebe Gateway REST internal URL.
*/}}
{{ define "camundaPlatform.zeebeGatewayRESTURL" }}
  {{- if .Values.zeebe.enabled -}}
    {{-
      printf "http://%s:%v%s"
        (include "zeebe.fullname.gateway" .)
        .Values.zeebeGateway.service.restPort
        (.Values.zeebeGateway.contextPath | default "")
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
  components:
  {{- $proto := ternary "https" "http" .Values.global.ingress.tls.enabled -}}
  {{- $baseURL := printf "%s://%s" $proto .Values.global.ingress.host }}

  {{- if .Values.console.enabled }}
  {{- $baseURLInternal := printf "http://%s.%s:%v" (include "console.fullname" .) .Release.Namespace .Values.console.service.managementPort }}
  - name: Console
    id: console
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.console) }}
    url: {{ include "camundaPlatform.consoleExternalURL" . }}
    readiness: {{ printf "%s%s" $baseURLInternal .Values.console.readinessProbe.probePath }}
    metrics: {{ printf "%s%s" $baseURLInternal .Values.console.metrics.prometheus }}
  {{- end }}
  {{ if .Values.identity.enabled -}}
  {{- $baseURLInternal := printf "http://%s.%s:%v" (include "identity.fullname" .) .Release.Namespace .Values.identity.service.metricsPort -}}
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

  {{- if .Values.operate.enabled }}
  {{- $baseURLInternal := printf "http://%s.%s:%v" (include "operate.fullname" .) .Release.Namespace .Values.operate.service.port }}
  - name: Operate
    id: operate
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.operate) }}
    url: {{ include "camundaPlatform.operateExternalURL" . }}
    readiness: {{ printf "%s%s%s" $baseURLInternal .Values.operate.contextPath .Values.operate.readinessProbe.probePath }}
    metrics: {{ printf "%s%s%s" $baseURLInternal .Values.operate.contextPath .Values.operate.metrics.prometheus }}
  {{- end }}

  {{- if .Values.optimize.enabled }}
  {{- $baseURLInternal := printf "http://%s.%s" (include "optimize.fullname" .) .Release.Namespace }}
  - name: Optimize
    id: optimize
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.optimize) }}
    url: {{ include "camundaPlatform.optimizeExternalURL" . }}
    readiness: {{ printf "%s:%v%s%s" $baseURLInternal .Values.optimize.service.port .Values.optimize.contextPath .Values.optimize.readinessProbe.probePath }}
    metrics: {{ printf "%s:%v%s" $baseURLInternal .Values.optimize.service.managementPort .Values.optimize.metrics.prometheus }}
  {{- end }}

  {{- if .Values.tasklist.enabled }}
  {{- $baseURLInternal := printf "http://%s.%s:%v" (include "tasklist.fullname" .) .Release.Namespace .Values.tasklist.service.port }}
  - name: Tasklist
    id: tasklist
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.tasklist) }}
    url: {{ include "camundaPlatform.tasklistExternalURL" . }}
    readiness: {{ printf "%s%s%s" $baseURLInternal .Values.tasklist.contextPath .Values.tasklist.readinessProbe.probePath }}
    metrics: {{ printf "%s%s%s" $baseURLInternal .Values.tasklist.contextPath .Values.tasklist.metrics.prometheus }}
  {{- end }}

  {{- if .Values.webModeler.enabled }}
  {{- $baseURLInternal := printf "http://%s.%s:%v" (include "webModeler.webapp.fullname" .) .Release.Namespace .Values.webModeler.webapp.service.managementPort }}
  - name: WebModeler WebApp
    id: webModelerWebApp
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.webModeler) }}
    url: {{ include "camundaPlatform.webModelerWebAppExternalURL" . }}
    readiness: {{ printf "%s%s" $baseURLInternal .Values.webModeler.webapp.readinessProbe.probePath }}
    metrics: {{ printf "%s%s" $baseURLInternal .Values.webModeler.webapp.metrics.prometheus }}
  {{- end }}

  {{- if .Values.zeebe.enabled }}
  {{- $baseURLInternal := printf "http://%s.%s:%v" (include "zeebe.names.gateway" . | trimAll "\"") .Release.Namespace .Values.zeebeGateway.service.httpPort }}
  - name: Zeebe Gateway
    id: zeebeGateway
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.zeebe) }}
    urls:
      grpc: {{ include "camundaPlatform.zeebeGatewayGRPCExternalURL" . }}
      http: {{ include "camundaPlatform.zeebeGatewayExternalURL" . }}
    readiness: {{ printf "%s%s%s" $baseURLInternal .Values.zeebeGateway.contextPath .Values.zeebeGateway.readinessProbe.probePath }}
    metrics: {{ printf "%s%s%s" $baseURLInternal .Values.zeebeGateway.contextPath .Values.zeebeGateway.metrics.prometheus }}
  {{- $baseURLInternal := printf "http://%s.%s:%v" (include "zeebe.names.broker" . | trimAll "\"") .Release.Namespace .Values.zeebe.service.httpPort }}
  - name: Zeebe
    id: zeebe
    version: {{ include "camundaPlatform.imageTagByParams" (dict "base" .Values.global "overlay" .Values.zeebeGateway) }}
    readiness: {{ printf "%s%s" $baseURLInternal .Values.zeebe.readinessProbe.probePath }}
    metrics: {{ printf "%s%s" $baseURLInternal .Values.zeebe.metrics.prometheus }}
  {{- end }}
{{- end -}}
