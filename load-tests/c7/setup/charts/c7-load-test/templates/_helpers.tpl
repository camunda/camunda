{{- define "c7.labels" -}}
app.kubernetes.io/part-of: c7-benchmark
camunda.io/purpose: load-test
{{- with .Values.author }}
camunda.io/created-by: {{ . | quote }}
{{- end }}
{{- end -}}

{{- define "c7.image" -}}
{{ .root.Values.global.image.registry }}/{{ .image }}:{{ .root.Values.global.image.tag }}
{{- end -}}

{{/* Pod scheduling and image pull settings shared by engine, starter and worker. */}}
{{- define "c7.podSpecCommon" -}}
{{- with .Values.global.image.pullSecrets }}
imagePullSecrets:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- if or .Values.global.nodeSelector .Values.topologyZone }}
nodeSelector:
  {{- with .Values.global.nodeSelector }}
  {{- toYaml . | nindent 2 }}
  {{- end }}
  {{- with .Values.topologyZone }}
  topology.kubernetes.io/zone: {{ . | quote }}
  {{- end }}
{{- end }}
{{- with .Values.global.tolerations }}
tolerations:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- end -}}

{{- define "c7.engineUrl" -}}
http://engine:8080
{{- end -}}
