{{/* vim: set filetype=mustache: */}}

{{/*
[zeebe-gateway] Create a default fully qualified app name.
*/}}
{{- define "zeebe.fullname.gateway" -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "zeebe-gateway"
        "componentValues" .Values.zeebeGateway
        "context" $
    ) -}}
{{- end -}}

{{/*
[zeebe-gateway] Creates a valid DNS name for the gateway.
*/}}
{{- define "zeebe.names.gateway" -}}
    {{- $name := default .Release.Name (tpl .Values.global.zeebeClusterName .) -}}
    {{- printf "%s-gateway" $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
[zeebe-gateway] Defines extra labels for zeebe.
*/}}
{{ define "zeebe.extraLabels.gateway" -}}
app.kubernetes.io/component: zeebe-gateway
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict "base" .Values.global "overlay" .Values.zeebeGateway "chart" .Chart) | quote }}
{{- end }}

{{/*
[zeebe-gateway] Define common labels for zeebe, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "zeebe.labels.gateway" -}}
    {{- include "camundaPlatform.labels" . }}
    {{- "\n" }}
    {{- include "zeebe.extraLabels.gateway" . }}
{{- end -}}

{{/*
[zeebe-gateway] Defines match labels for zeebe, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "zeebe.matchLabels.gateway" -}}
    {{- include "camundaPlatform.matchLabels" . }}
app.kubernetes.io/component: zeebe-gateway
{{- end -}}

{{/*
[zeebe-gateway] Create the name of the service account to use.
*/}}
{{- define "zeebe.serviceAccountName.gateway" -}}
    {{- if .Values.zeebeGateway.serviceAccount.enabled -}}
        {{- default (include "zeebe.fullname.gateway" .) .Values.zeebeGateway.serviceAccount.name -}}
    {{- else -}}
        {{- default "default" .Values.zeebeGateway.serviceAccount.name -}}
    {{- end -}}
{{- end -}}

{{/*
[zeebe-gateway] Get the image pull secrets.
*/}}
{{- define "zeebe.imagePullSecrets.gateway" -}}
    {{- include "camundaPlatform.imagePullSecrets" (dict
        "component" "zeebeGateway"
        "context" $
    ) -}}
{{- end }}

{{/*
[zeebe-gateway] Generate readiness probe path based on contextPath and ingress.rest.path.
*/}}
{{- define "zeebe.readinessProbePath.gateway" -}}
    {{- if eq .Values.zeebeGateway.contextPath "" -}}
        {{- if eq .Values.zeebeGateway.ingress.rest.path "/" -}}
            /{{ trimPrefix "/" (trimSuffix "/" .Values.zeebeGateway.readinessProbe.probePath) }}
        {{- else -}}
            {{- trimSuffix "/" .Values.zeebeGateway.ingress.rest.path }}/{{ trimPrefix "/" (trimSuffix "/" .Values.zeebeGateway.readinessProbe.probePath) }}
        {{- end -}}
    {{- else if eq .Values.zeebeGateway.contextPath "/" -}}
        /{{ trimPrefix "/" (trimSuffix "/" .Values.zeebeGateway.readinessProbe.probePath) }}
    {{- else -}}
        {{- trimSuffix "/" .Values.zeebeGateway.contextPath }}/{{ trimPrefix "/" (trimSuffix "/" .Values.zeebeGateway.readinessProbe.probePath) }}
    {{- end -}}
{{- end -}}
