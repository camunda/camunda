{{/*
The image to use for the metrics exporter
*/}}
{{- define "metrics-exporter.image" -}}
{{ $image := .Values.metricsExporter.image }}
{{- printf "%s/%s:%s" $image.registry $image.repository $image.tag }}
{{- end }}

{{/*
Generate a new secret based on the input.

The input is expected to be a dict with:
* "namespace": the namespace we generate the secret for
* "user": the username the secret is for

The "derivePassword" function is not well documented, by conventions, we use:

* The "counter" input is the same for all the password.
* The generated passwords will be "long" passwords: 14 characters with a mix of random characters
* The "password" input is always set to "secret"
* The "user" input is the key we generate the input for ; it's not the actual username but something unique across all the users with need
* The "site" is the name of namespace: this is actually the only "random" value seeded into the password generator. It guarantees that:
  1. Different namespaces will produce different passwords
  2. The same namespace will produce the same passwords (as long as the other inputs don't change).
*/}}
{{- define "generate-secret" -}}
{{- derivePassword 1 "long" "secret" .user .namespace -}}
{{- end -}}

{{/*
The authentication server URL for the load test clients using OIDC.

Either set a specific authentication server, or it assumes Keycloak is enabled
and will build the URL to the Keycloak instance.

Since we deploy Keycloak in a different namespace than the current load test
namespace, the final URL to reach Keycloak requires computing Keycloak's full
name.
*/}}
{{- define "load-test-setup.load-test.client.auth-server-url" -}}
{{- if .Values.loadTest.client.oidc.authServer -}}
  {{- .Values.loadTest.client.oidc.authServer -}}
{{- else -}}
  {{- if not .Values.keycloak.enabled -}}
  {{/* Always fail this check in this case */}}
  {{- required "If the load test client OIDC auth server is not specified, Keycloak must be enabled!" nil -}}
  {{- end -}}
http://{{ .Release.Namespace }}.{{ .Values.keycloak.namespace }}.svc.cluster.local:{{ .Values.keycloak.http.port }}/auth/realms/camunda-platform/protocol/openid-connect/token
{{- end -}}
{{- end -}}

{{/* vim: set filetype=gotmpl: */}}
