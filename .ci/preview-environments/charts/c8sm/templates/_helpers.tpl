
{{ define "commonLabels" -}}
{{- toYaml .Values.global.labels -}}
{{ end }}

{{- define "commonAnnotations" -}}
camunda.cloud/created-by: "{{ .Values.global.preview.git.repoUrl }}/blob/{{ .Values.global.preview.git.branch }}/.ci/{{ .Template.Name }}"
{{- if .Values.global.annotations }}
{{ toYaml .Values.global.annotations -}}
{{- end }}
{{- end }}

{{- define "ingress.domain" -}}
{{- printf "%s.%s" .Release.Name .Values.global.preview.ingress.domain | trimPrefix "camunda-" -}}
{{- end -}}

# Temporary override the following (existing) template blocks
# TODO: remove these workarounds once the corresponding issues are resolved

# Fallback to default .publicHost if ingress is disabled
{{- define "webModeler.publicWebsocketHost" -}}
  {{- if and .Values.global.ingress.enabled .Values.webModeler.contextPath }}
    {{- .Values.global.ingress.host }}
  {{- else }}
    {{- .Values.webModeler.websockets.publicHost }}
  {{- end }}
{{- end -}}

# Disable TLS for WebSocket
{{- define "webModeler.websocketTlsEnabled" -}}
  {{- if and .Values.global.ingress.enabled .Values.webModeler.contextPath }}
    {{- .Values.global.ingress.tls.enabled }}
  {{- else }}
    {{- false }}
  {{- end }}
{{- end -}}

# Custom auth annotations helper that conditionally uses either:
# - LDAP Basic Auth (when .Values.global.preview.ingress.ldapAuth.enabled is true)
# - Vouch Proxy (default, via infrapreviewenvironmentsingress.annotations)
# 
# LDAP Basic Auth bypasses Vouch Proxy and authenticates directly against LDAP.
# This is useful for automated/programmatic access using service accounts.
{{- define "c8sm.authAnnotations" }}
{{- if .Values.global.preview.ingress.ldapAuth.enabled }}
# LDAP Basic Auth - bypasses Vouch Proxy
nginx.ingress.kubernetes.io/configuration-snippet: |
  include /etc/nginx/require-auth.conf;
  # LDAP base DN for authentication
  set $ldapbasedn "{{ required "global.preview.ingress.ldapAuth.baseDn is required when LDAP auth is enabled" .Values.global.preview.ingress.ldapAuth.baseDn }}";
{{ else }}
# Vouch Proxy authentication (default)
{{ include "infrapreviewenvironmentsingress.annotations" $ }}
{{- end }}
{{- end }}
