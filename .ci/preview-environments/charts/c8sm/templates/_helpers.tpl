
{{ define "commonLabels" -}}
{{- toYaml .Values.global.labels -}}
{{ end }}

{{- define "commonAnnotations" -}}
camunda.cloud/created-by: "{{ .Values.global.preview.git.repoUrl }}/blob/{{ .Values.global.preview.git.branch }}/.ci/{{ .Template.Name }}"
{{- if .Values.global.annotations }}
{{ toYaml .Values.global.annotations -}}
{{- end }}
{{- end }}

{{- define "ingress.domain" -}}
{{- $baseName := .Release.Name | trimPrefix "camunda-" -}}
{{- printf "%s.%s" $baseName .Values.global.preview.ingress.domain -}}
{{- end -}}

