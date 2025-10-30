
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
{{- printf "%s.%s" .Release.Name .Values.global.preview.ingress.domain | trimPrefix "camunda-" -}}
{{- end -}}