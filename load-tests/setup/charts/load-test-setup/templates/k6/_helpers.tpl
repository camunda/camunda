{{/*
The run mode of a k6 test: "statefulset" when its `runner` has a `statefulset` key, "testrun" otherwise.

The input is an entry of `k6.tests`.
*/}}
{{- define "load-test-setup.k6.mode" -}}
{{- if hasKey (. | dig "runner" dict) "statefulset" }}statefulset{{ else }}testrun{{ end -}}
{{- end }}

{{/*
The environment variables of the k6 runners, shared by the TestRun and the StatefulSet modes.

The input is the root context.
*/}}
{{- define "load-test-setup.k6.env" -}}
- name: K6_LOG_FORMAT
  value: json
# Export to Prometheus
- name: K6_OUT
  value: experimental-prometheus-rw
# The Prometheus endpoint to report metrics to
- name: K6_PROMETHEUS_RW_SERVER_URL
  value: "{{ .Values.k6.prometheus.endpoint }}"
# Mark Prometheus as "stale" when they are not updated anymore.
- name: K6_PROMETHEUS_RW_STALE_MARKERS
  value: "true"
# System tags set automatically on every metric (see `k6.tags.system` in values.yaml).
- name: K6_SYSTEM_TAGS
  value: "{{ .Values.k6.tags.system | join "," }}"
# Found empirically: without these three settings, k6 memory grows without bound
# on long runs and the pod is OOM-killed.
- name: K6_NO_THRESHOLDS
  value: "true"
- name: K6_SUMMARY_MODE
  value: disabled
- name: K6_FEATURES
  value: native-histograms
- name: DATA_DIR
  value: /data
- name: K8S_NAMESPACE
  valueFrom:
    fieldRef:
      fieldPath: metadata.namespace
# Credentials
- name: CAMUNDA_BASE_URL
  valueFrom:
    secretKeyRef:
      key: zeebeRestAddress
      name: load-test-credentials
- name: CAMUNDA_CLIENT_ID
  valueFrom:
    secretKeyRef:
      key: clientId
      name: load-test-credentials
- name: CAMUNDA_CLIENT_SECRET
  valueFrom:
    secretKeyRef:
      key: clientSecret
      name: load-test-credentials
- name: CAMUNDA_OAUTH_URL
  valueFrom:
    secretKeyRef:
      key: authServer
      name: load-test-credentials
- name: CAMUNDA_TOKEN_AUDIENCE
  valueFrom:
    secretKeyRef:
      key: authorizationAudience
      name: load-test-credentials
- name: ZEEBE_GRPC_ADDRESS
  valueFrom:
    secretKeyRef:
      key: zeebeGrpcAddress
      name: load-test-credentials
{{- end }}

{{/* vim: set filetype=gotmpl: */}}
