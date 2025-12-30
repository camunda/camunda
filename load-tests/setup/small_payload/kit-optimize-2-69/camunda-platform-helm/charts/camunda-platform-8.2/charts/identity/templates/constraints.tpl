{{/*
A template to handel constraints.
*/}}

{{/*
Show deprecation messages for using ".global.identity.keycloak.fullname".
*/}}

{{- if (.Values.global.identity.keycloak.fullname) }}
{{- $globalIdentityKeycloakFullnameMessage := `
[identity][deprecation] The var ".global.identity.keycloak.fullname" is deprecated in favour of
".global.identity.keycloak.url".
For more details, please check Camunda Platform Helm chart documentation.
` -}}
    {{ printf "\n%s" $globalIdentityKeycloakFullnameMessage | trimSuffix "\n"| fail }}
{{- end }}
