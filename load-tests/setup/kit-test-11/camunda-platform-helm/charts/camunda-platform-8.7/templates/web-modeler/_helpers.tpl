{{/* vim: set filetype=mustache: */}}

{{/*
Get the default app name.
*/}}
{{- define "webModeler.name" -}}
web-modeler
{{- end -}}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "webModeler.fullname" -}}
  {{- include "camundaPlatform.componentFullname" (dict
      "componentName" "web-modeler"
      "componentValues" .Values.webModeler
      "context" $
  ) -}}
{{- end -}}

{{/*
Create a fully qualified name for the restapi objects.
*/}}
{{- define "webModeler.restapi.fullname" -}}
  {{- (include "webModeler.fullname" .) | trunc 55 | trimSuffix "-" -}}-restapi
{{- end -}}

{{/*
Create a fully qualified name for the webapp objects.
*/}}
{{- define "webModeler.webapp.fullname" -}}
  {{- (include "webModeler.fullname" .) | trunc 56 | trimSuffix "-" -}}-webapp
{{- end -}}

{{/*
Create a fully qualified name for the websockets objects.
*/}}
{{- define "webModeler.websockets.fullname" -}}
  {{- (include "webModeler.fullname" .) | trunc 52 | trimSuffix "-" -}}-websockets
{{- end -}}

{{/*
Define extra labels for Web Modeler.
*/}}
{{- define "webModeler.extraLabels" -}}
app.kubernetes.io/component: web-modeler
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict "base" .Values.global "overlay" .Values.webModeler "chart" .Chart) | quote }}
{{- end -}}

{{/*
Define extra labels for Web Modeler restapi.
*/}}
{{- define "webModeler.restapi.extraLabels" -}}
app.kubernetes.io/component: restapi
{{- end -}}

{{/*
Define extra labels for Web Modeler webapp.
*/}}
{{- define "webModeler.webapp.extraLabels" -}}
app.kubernetes.io/component: webapp
{{- end -}}

{{/*
Define extra labels for Web Modeler websockets.
*/}}
{{- define "webModeler.websockets.extraLabels" -}}
app.kubernetes.io/component: websockets
{{- end -}}

{{/*
Define common labels for all Web Modeler components.
*/}}
{{- define "webModeler.commonLabels" -}}
{{- $values := merge (deepCopy .Values) (dict "nameOverride" (include "webModeler.name" .) "image" .Values.webModeler.image) }}
{{- template "camundaPlatform.labels" (dict "Chart" .Chart "Release" .Release "Values" $values) }}
{{- end -}}

{{/*
Define common match labels for all Web Modeler components.
*/}}
{{- define "webModeler.commonMatchLabels" -}}
{{- $values := set (deepCopy .Values) "nameOverride" (include "webModeler.name" .) }}
{{- template "camundaPlatform.matchLabels" (dict "Chart" .Chart "Release" .Release "Values" $values) }}
{{- end -}}

{{/*
Define common labels for Web Modeler, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "webModeler.labels" -}}
{{ template "webModeler.commonLabels" . }}
{{ template "webModeler.extraLabels" . }}
{{- end -}}

{{/*
Define common labels for Web Modeler restapi, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "webModeler.restapi.labels" -}}
{{ template "webModeler.commonLabels" . }}
{{ template "webModeler.restapi.extraLabels" . }}
{{- end -}}

{{/*
Define common labels for Web Modeler webapp, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "webModeler.webapp.labels" -}}
{{ template "webModeler.commonLabels" . }}
{{ template "webModeler.webapp.extraLabels" . }}
{{- end -}}

{{/*
Define common labels for Web Modeler websockets, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "webModeler.websockets.labels" -}}
{{ template "webModeler.commonLabels" . }}
{{ template "webModeler.websockets.extraLabels" . }}
{{- end -}}

{{/*
Define match labels for Web Modeler restapi to be used in matchLabels selectors.
*/}}
{{- define "webModeler.restapi.matchLabels" -}}
{{ template "webModeler.commonMatchLabels" . }}
{{ template "webModeler.restapi.extraLabels" . }}
{{- end -}}

{{/*
Define match labels for Web Modeler webapp to be used in matchLabels selectors.
*/}}
{{- define "webModeler.webapp.matchLabels" -}}
{{ template "webModeler.commonMatchLabels" . }}
{{ template "webModeler.webapp.extraLabels" . }}
{{- end -}}

{{/*
Define match labels for Web Modeler websockets to be used in matchLabels selectors.
*/}}
{{- define "webModeler.websockets.matchLabels" -}}
{{ template "webModeler.commonMatchLabels" . }}
{{ template "webModeler.websockets.extraLabels" . }}
{{- end -}}

{{/*
[web-modeler] Get the image pull secrets.
*/}}
{{- define "webModeler.imagePullSecrets" -}}
  {{- include "camundaPlatform.subChartImagePullSecrets" (dict "Values" (set (deepCopy .Values) "image" .Values.webModeler.image)) }}
{{- end }}

{{/*
[web-modeler] Get the full name (<registry>/<repository>:<tag>) of the restapi Docker image
*/}}
{{- define "webModeler.restapi.image" -}}
  {{- include "camundaPlatform.imageByParams" (dict "base" .Values.global "overlay" (dict "image" (deepCopy .Values.webModeler.image | merge .Values.webModeler.restapi.image))) }}
{{- end }}

{{/*
[web-modeler] Get the full name (<registry>/<repository>:<tag>) of the webapp Docker image
*/}}
{{- define "webModeler.webapp.image" -}}
  {{- include "camundaPlatform.imageByParams" (dict "base" .Values.global "overlay" (dict "image" (deepCopy .Values.webModeler.image | merge .Values.webModeler.webapp.image))) }}
{{- end }}

{{/*
[web-modeler] Get the full name (<registry>/<repository>:<tag>) of the websockets Docker image
*/}}
{{- define "webModeler.websockets.image" -}}
  {{- include "camundaPlatform.imageByParams" (dict "base" .Values.global "overlay" (dict "image" (deepCopy .Values.webModeler.image | merge .Values.webModeler.websockets.image))) }}
{{- end }}

{{/*
[web-modeler] Create the name of the service account to use
*/}}
{{- define "webModeler.serviceAccountName" -}}
    {{- include "camundaPlatform.serviceAccountName" (dict
        "component" "webModeler"
        "context" $
    ) -}}
{{- end -}}

{{/*

[web-modeler] Get the database JDBC url, depending on whether the postgresql dependency chart is enabled.
*/}}
{{- define "webModeler.restapi.databaseUrl" -}}
  {{- if .Values.postgresql.enabled -}}
    {{- printf "jdbc:postgresql://%s:5432/%s"
        (include "webModeler.postgresql.fullname" .)
        (.Values.postgresql.auth.database)
      -}}
  {{- else if .Values.webModeler.restapi.externalDatabase.url -}}
    {{- .Values.webModeler.restapi.externalDatabase.url -}}
  {{- else if .Values.webModeler.restapi.externalDatabase.host -}}
    {{- printf "jdbc:postgresql://%s:%s/%s"
        .Values.webModeler.restapi.externalDatabase.host
        (toString (.Values.webModeler.restapi.externalDatabase.port))
        (.Values.webModeler.restapi.externalDatabase.database)
      -}}
  {{- end -}}
{{- end -}}

{{/*
[web-modeler] Get the database user, depending on whether the postgresql dependency chart is enabled.
*/}}
{{- define "webModeler.restapi.databaseUser" -}}
  {{- .Values.postgresql.enabled | ternary .Values.postgresql.auth.username .Values.webModeler.restapi.externalDatabase.user -}}
{{- end -}}

{{/*
[web-modeler] Get the name of the secret that contains the database password, depending on whether the postgresql dependency chart is enabled.
*/}}
{{- define "webModeler.restapi.databaseSecretName" -}}
  {{- if .Values.postgresql.enabled }}
    {{- .Values.postgresql.auth.existingSecret | default (include "webModeler.postgresql.fullname" .) }}
  {{- else }}
    {{- if or (typeIs "string" .Values.webModeler.restapi.externalDatabase.existingSecret) .Values.webModeler.restapi.externalDatabase.password }}
      {{- include "webModeler.restapi.fullname" . }}
    {{- else if typeIs "map[string]interface {}" .Values.webModeler.restapi.externalDatabase.existingSecret }}
      {{- .Values.webModeler.restapi.externalDatabase.existingSecret.name | default (include "webModeler.restapi.fullname" .) }}
    {{- end }}
  {{- end }}
{{- end -}}

{{/*
[web-modeler] Get the name of the database password key in the secret, depending on whether the postgresql dependency chart is enabled.
*/}}
{{- define "webModeler.restapi.databaseSecretKey" -}}
  {{- if .Values.postgresql.enabled }}
    {{- if .Values.postgresql.auth.existingSecret }}
      {{- .Values.postgresql.auth.secretKeys.userPasswordKey }}
    {{- else -}}
      password
    {{- end }}
  {{- else }}
    {{- $defaultSecretKey := "database-password" -}}
    {{- if .Values.webModeler.restapi.externalDatabase.existingSecret }}
      {{- .Values.webModeler.restapi.externalDatabase.existingSecretPasswordKey | default $defaultSecretKey }}
    {{- else }}
      {{- $defaultSecretKey }}
    {{- end }}
  {{- end }}
{{- end -}}

{{/*
[web-modeler] Get the name of the secret resource that contains the SMTP password.
*/}}
{{- define "webModeler.restapi.smtpSecretName" -}}
  {{- if or (typeIs "string" .Values.webModeler.restapi.mail.existingSecret) .Values.webModeler.restapi.mail.smtpPassword }}
      {{- (include "webModeler.restapi.fullname" .) }}
  {{- else if and (typeIs "map[string]interface {}" .Values.webModeler.restapi.mail.existingSecret) .Values.webModeler.restapi.mail.existingSecret.name }}
      {{- .Values.webModeler.restapi.mail.existingSecret.name }}
  {{- end }}
{{- end -}}

{{/*
[web-modeler] Get the name of the secret key that contains the SMTP password.
*/}}
{{- define "webModeler.restapi.smtpSecretKey" -}}
  {{- $defaultSecretKey := "smtp-password" -}}
  {{- if (typeIs "string" .Values.webModeler.restapi.mail.existingSecret) }}
    {{- $defaultSecretKey }}
  {{- else if .Values.webModeler.restapi.mail.existingSecret }}
    {{- .Values.webModeler.restapi.mail.existingSecretPasswordKey | default $defaultSecretKey }}
  {{- else if .Values.webModeler.restapi.mail.smtpPassword }}
    {{- $defaultSecretKey }}
  {{- end }}
{{- end -}}

{{/*
[web-modeler] Check if username and password is provided for the SMTP server
*/}}
{{- define "webModeler.restapi.mail.authEnabled" -}}
  {{- $authEnabled := false -}}
  {{- if and (typeIs "string" .Values.webModeler.restapi.mail.smtpUser) (ne .Values.webModeler.restapi.mail.smtpUser "") }}
    {{- if or (and (typeIs "string" .Values.webModeler.restapi.mail.smtpPassword) (ne .Values.webModeler.restapi.mail.smtpPassword "")) .Values.webModeler.restapi.mail.existingSecret }}
      {{- $authEnabled = true }}
    {{- end }}
  {{- end }}
  {{- $authEnabled -}}
{{- end -}}

{{/*
[web-modeler] Get the authentication type for the configured cluster
*/}}
{{- define "webModeler.restapi.clusterAuthentication" -}}
  {{- if .Values.global.identity.auth.enabled -}}
    {{- eq (include "camundaPlatform.authType" .) "MICROSOFT" | ternary "CLIENT_CREDENTIALS" "BEARER_TOKEN" -}}
  {{- else -}}
    NONE
  {{- end -}}
{{- end -}}

{{/*
[web-modeler] Get the full name of the Kubernetes objects from the postgresql dependency chart
*/}}
{{- define "webModeler.postgresql.fullname" -}}
  {{- include "common.names.dependency.fullname" (dict "chartName" "postgresql" "chartValues" .Values.postgresql "context" $) -}}
{{- end -}}

{{/*
[web-modeler] Create the context path for the WebSocket app (= configured context path for the webapp + suffix "-ws").
*/}}
{{- define "webModeler.websocketContextPath" -}}
  {{- .Values.webModeler.contextPath }}-ws
{{- end -}}

{{/*
[web-modeler] Get the host name on which the WebSocket server is reachable from the client.
*/}}
{{- define "webModeler.publicWebsocketHost" -}}
  {{- if and .Values.global.ingress.enabled .Values.webModeler.contextPath }}
    {{- .Values.global.ingress.host }}
  {{- else }}
    {{- .Values.webModeler.ingress.enabled | ternary .Values.webModeler.ingress.websockets.host .Values.webModeler.websockets.publicHost }}
  {{- end }}
{{- end -}}

{{/*
[web-modeler] Get the port number on which the WebSocket server is reachable from the client.
*/}}
{{- define "webModeler.publicWebsocketPort" -}}
  {{- if and .Values.global.ingress.enabled .Values.webModeler.contextPath }}
    {{- .Values.global.ingress.tls.enabled | ternary "443" "80" }}
  {{- else }}
    {{- if .Values.webModeler.ingress.enabled }}
      {{- .Values.webModeler.ingress.websockets.tls.enabled | ternary "443" "80" }}
    {{- else }}
      {{- .Values.webModeler.websockets.publicPort }}
    {{- end }}
  {{- end }}
{{- end -}}

{{/*
[web-modeler] Check if TLS must be enabled for WebSocket connections from the client.
*/}}
{{- define "webModeler.websocketTlsEnabled" -}}
  {{- if and .Values.global.ingress.enabled .Values.webModeler.contextPath }}
    {{- .Values.global.ingress.tls.enabled }}
  {{- else }}
    {{- and .Values.webModeler.ingress.enabled .Values.webModeler.ingress.websockets.tls.enabled }}
  {{- end }}
{{- end -}}

{{/*
[web-modeler] Define variables related to authentication.
*/}}
{{- define "webModeler.authClientId" -}}
  {{- .Values.global.identity.auth.webModeler.clientId | default "web-modeler" -}}
{{- end -}}

{{- define "webModeler.authClientApiAudience" -}}
  {{- .Values.global.identity.auth.webModeler.clientApiAudience | default "web-modeler-api" -}}
{{- end -}}

{{- define "webModeler.authPublicApiAudience" -}}
  {{- .Values.global.identity.auth.webModeler.publicApiAudience | default "web-modeler-public-api" -}}
{{- end -}}
