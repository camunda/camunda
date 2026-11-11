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
The name of the Kubernetes Secret used to store the Keycloak initial "admin" user.

This user has full access on Keycloak and is used by Identity to provision it.
*/}}
{{- define "load-test-setup.keycloak.admin-secret-name" -}}
keycloak-admin-user
{{- end -}}

{{/*
The name of the Kubernetes Secret used by the CNPG Operator to keep the credentials for the Camunda user in PostgreSQL.
*/}}
{{- define "load-test-setup.postgresql.cnpg-secret-name" -}}
postgresql-camunda-user
{{- end -}}

{{/*
The name of the Kubernetes Secret holding the environment variables that point Camunda's RDBMS primary
storage at the PostgreSQL cluster. Referenced by name from camunda-platform-values-primary-storage-rdbms.yaml.
*/}}
{{- define "load-test-setup.postgresql.primary-storage-secret-name" -}}
camunda-primary-storage-rdbms
{{- end -}}

{{/* vim: set filetype=gotmpl: */}}
