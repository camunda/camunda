{{- if .Values.zeebe.enabled -}}

{{/*
A template to handel constraints.
*/}}

{{/*
TODO: Enable for 8.7 cycle.

Fail with a message if the old refactored keys are still used and the new keys are not used.
Chart Version: 10.0.0
{{- if (index .Values "zeebe-gateway") }}
    {{- $errorMessage := printf "[zeebe-gateway] %s %s %s"
        "The Zeebe Gatway key changed from \"zeebe-gateway\" to \"zeebeGateway\"."
        "For more details, please check Camunda Helm chart documentation."
        "https://docs.camunda.io/docs/self-managed/deployment/helm/upgrade/"
    -}}
    {{ printf "\n%s" $errorMessage | trimSuffix "\n"| fail }}
{{- end }}

{{- if (.Values.zeebeGateway.ingress.enabled) }}
    {{- $errorMessage := printf "[zeebe-gateway] %s %s %s"
        "The gRPC Ingress key changed from \"zeebeGateway.ingress\" to \"zeebeGateway.ingress.grpc\"."
        "For more details, please check Camunda Helm chart documentation."
        "https://docs.camunda.io/docs/self-managed/deployment/helm/upgrade/"
    -}}
    {{ printf "\n%s" $errorMessage | trimSuffix "\n"| fail }}
{{- end }}
*/}}

{{- end }}
