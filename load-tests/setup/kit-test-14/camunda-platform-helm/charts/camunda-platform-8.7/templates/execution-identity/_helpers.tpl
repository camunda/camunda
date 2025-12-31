{{/*
Get the default app name.
*/}}
{{- define "executionIdentity.name" -}}
execution-identity
{{- end }}

{{- define "executionIdentity.fullname" -}}
    {{- include "camundaPlatform.componentFullname" (dict
        "componentName" "execution-identity"
        "componentValues" .Values.executionIdentity
        "context" $
    ) -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "executionIdentity.chart" -}}
    {{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Defines extra labels for executionIdentity.
*/}}
{{- define "executionIdentity.extraLabels" -}}
app.kubernetes.io/component: execution-identity
app.kubernetes.io/version: {{ include "camundaPlatform.versionLabel" (dict "base" .Values.global "overlay" .Values.executionIdentity "chart" .Chart) | quote }}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "executionIdentity.labels" -}}
{{- template "camundaPlatform.labels" . }}
{{ template "executionIdentity.extraLabels" . }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "executionIdentity.matchLabels" -}}
{{- template "camundaPlatform.matchLabels" . }}
app.kubernetes.io/component: execution-identity
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "executionIdentity.serviceAccountName" -}}
    {{- include "camundaPlatform.serviceAccountName" (dict
        "component" "executionIdentity"
        "context" $
    ) -}}
{{- end -}}

{{/*
Get the image pull secrets.
*/}}
{{- define "executionIdentity.imagePullSecrets" -}}
    {{- include "camundaPlatform.imagePullSecrets" (dict
        "component" "executionIdentity"
        "context" $
    ) -}}
{{- end }}
