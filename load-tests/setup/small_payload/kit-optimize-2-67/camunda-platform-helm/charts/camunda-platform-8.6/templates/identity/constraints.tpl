{{- if .Values.identity.enabled -}}

{{/*
A template to handel constraints.
*/}}

{{/*
Show a deprecation messages for using ".global.identity.keycloak.fullname".
*/}}

{{- if (.Values.global.identity.keycloak.fullname) }}
    {{- $errorMessage := printf "[identity][deprecation] %s %s"
        "The var \"global.identity.keycloak.fullname\" is deprecated in favour of \".global.identity.keycloak.url\"."
        "For more details, please check Camunda Helm chart documentation."
    -}}
    {{ printf "\n%s" $errorMessage | trimSuffix "\n"| fail }}
{{- end }}


{{/*
Show an error message if both internal and external databases are enabled at the same time.
*/}}

{{- if and .Values.identityPostgresql.enabled .Values.identity.externalDatabase.enabled }}
    {{- $errorMessage := printf "[identity][error] %s %s"
        "The values \"identityPostgresql.enabled\" and \"identity.externalDatabase.enabled\""
        "are mutually exclusive and cannot be enabled together. Only use one of either."
    -}}
    {{ printf "\n%s" $errorMessage | trimSuffix "\n"| fail }}
{{- end }}

{{/*
TODO: Enable for 8.7 cycle.

Fail with a message if the old refactored keys are still used and the new keys are not used.
Chart Version: 10.0.0
{{- if (.Values.identity.keycloak) }}
    {{- $errorMessage := printf "[identity][error] %s %s %s"
        "The Keycloak key changed from \"identity.keycloak\" to \"identityKeycloak\"."
        "For more details, please check Camunda Helm chart documentation."
        "https://docs.camunda.io/docs/self-managed/deployment/helm/upgrade/"
    -}}
    {{ printf "\n%s" $errorMessage | trimSuffix "\n"| fail }}
{{- end }}

{{- if (.Values.identity.postgresql) }}
    {{- $errorMessage := printf "[identity][error] %s %s %s"
        "The PostgreSQL key changed from \"identity.postgresq\" to \"identityPostgresql\"."
        "For more details, please check Camunda Helm chart documentation."
        "https://docs.camunda.io/docs/self-managed/deployment/helm/upgrade/"
    -}}
    {{ printf "\n%s" $errorMessage | trimSuffix "\n"| fail }}
{{- end }}
*/}}

{{- end }}
