{{/* vim: set filetype=mustache: */}}

{{/*
[zeebe] Create a default fully qualified app name.
*/}}
{{- define "zeebe.fullname.broker" -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "zeebe"
        "componentValues" .Values.zeebe
        "context" $
    ) -}}
{{- end -}}

{{/*
[zeebe] Common names.
*/}}
{{- define "zeebe.names.broker" -}}
    {{- if .Values.global.zeebeClusterName -}}
        {{- tpl .Values.global.zeebeClusterName . | trunc 63 | trimSuffix "-" | quote -}}
    {{- else -}}
        {{- printf "%s-broker" .Release.Name | trunc 63 | trimSuffix "-" | quote -}}
    {{- end -}}
{{- end -}}

{{/*
[zeebe] Defines extra labels for zeebe.
*/}}
{{ define "zeebe.extraLabels.broker" -}}
app.kubernetes.io/component: zeebe-broker
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict "base" .Values.global "overlay" .Values.zeebe "chart" .Chart) | quote }}
{{- end }}

{{/*
[zeebe] Define common labels for zeebe, combining the match labels and transient labels, which might change on updating
(version depending). These labels shouldn't be used on matchLabels selector, since the selectors are immutable.
*/}}
{{- define "zeebe.labels.broker" -}}
    {{- include "camundaPlatform.labels" . }}
    {{- "\n" }}
    {{- include "zeebe.extraLabels.broker" . }}
{{- end -}}

{{/*
[zeebe] Defines match labels for zeebe, which are extended by sub-charts and should be used in matchLabels selectors.
*/}}
{{- define "zeebe.matchLabels.broker" -}}
    {{- include "camundaPlatform.matchLabels" . }}
app.kubernetes.io/component: zeebe-broker
{{- end -}}

{{/*
[zeebe] Create the name of the service account to use.
*/}}
{{- define "zeebe.serviceAccountName.broker" -}}
    {{- if .Values.zeebe.serviceAccount.enabled -}}
        {{- default (include "zeebe.fullname.broker" .) .Values.zeebe.serviceAccount.name -}}
    {{- else -}}
        {{- default "default" .Values.zeebe.serviceAccount.name -}}
    {{- end -}}
{{- end -}}

{{/*
[zeebe] Get the image pull secrets.
*/}}
{{- define "zeebe.imagePullSecrets.broker" -}}
    {{- include "camundaPlatform.imagePullSecrets" (dict
        "component" "zeebe"
        "context" $
    ) -}}
{{- end }}

{{- define "zeebe.authAudience" }}
    {{- .Values.global.identity.auth.zeebe.audience | default "zeebe-api" -}}
{{- end -}}

{{- define "zeebe.authTokenScope" }}
    {{- .Values.global.identity.auth.zeebe.tokenScope -}}
{{- end -}}

