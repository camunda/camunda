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
Constant that defines the orchestration cluster secret key, used by the several
deployments (Camunda Platform Helm Chart, load tests, etc.)
*/}}
{{- define "oc-secret-name" -}}
orchestration-security-authentication-oidc-secret
{{- end -}}

{{/* vim: set filetype=gotmpl: */}}
