{{ define "commonLabels" -}}
{{- toYaml .Values.global.labels -}}
{{ end }}

{{ define "commonAnnotations" -}}
camunda.cloud/created-by: "{{ .Values.git.repoUrl }}/blob/{{ .Values.git.branch }}/.ci/{{ .Template.Name }}"
{{ end }}

{{ define "ingress.domain" -}}
{{ printf "%s.%s" .Release.Name .Values.ingress.domain | trimPrefix "ztl-" }}{{ end }}

{{ define "operate-ingress.domain" -}}
{{ printf "%s-operate.%s" .Release.Name .Values.ingress.domain  | trimPrefix "ztl-" }}{{ end }}
