{{/*
This file defines constants shared between multiple templates.
*/}}

{{/*
The name of the Kubernetes Secret used by the CNPG Operator to keep the credentials for the Keycloak user in PostgreSQL.
*/}}
{{- define "load-test-setup.keycloak.postgresql.cnpg-secret-name" -}}
postgresql-keycloak-user
{{- end -}}

{{/*
The name of the Kubernetes Secret used by Keycloak to keep the credentials used to connect into PostgreSQL.

This secret is stored inside the `keycloak-operator` namespace, and is prefixed
by the current load test name to prevent collision with other load tests.

This secret will contain the same content as the
"load-test-setup.keycloak.postgresql.cnpg-secret-name" secret, but not in the
same namespace.
*/}}
{{- define "load-test-setup.keycloak.postgresql.keycloak-secret-name" -}}
{{ printf "%s-postgresql" .Release.Namespace }}
{{- end -}}

{{/*
The name of the Kubernetse Secret used to store the Keycloak initial "admin" user.

This user has full access on Keycloak and is used by Identity to provision it.
This secret is stored inside the `keycloak-operator` namespace, and is prefixed
by the current load test name to prevent collision with other load tests.
*/}}
{{- define "load-test-setup.keycloak.admin-secret-name" -}}
{{ printf "%s-admin" .Release.Namespace }}
{{- end -}}

{{/* vim: set filetype=gotmpl: */}}
