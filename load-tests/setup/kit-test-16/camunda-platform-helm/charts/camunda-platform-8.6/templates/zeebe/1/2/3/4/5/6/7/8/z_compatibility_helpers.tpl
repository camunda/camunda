{{/*
NOTE: We need to load this file first thing before all other resources to support backward compatibility.

      Helm prioritizes files that are deeply nested in subdirectories when it is determining the render order.
      see the sort function in Helm:
      https://github.com/helm/helm/blob/d58d7b376265338e059ff11c71267b5a6cf504c3/pkg/engine/engine.go#L347-L356
      
      Because of this sort order, and that we have nested subcharts such that
      one of the rendered templates is:
      charts/keycloak/charts/postgresql/charts/common/templates/validations/_validations.tpl,
      we need this z_compatibility_helpers.tpl to be nested in at least 8 folders.

      In addition to the subdirectory ordering, Helm also orders the templates
      alphabetically descending within the same folder level, which is why it
      is named with a "z_" inside the zeebe directory. so Helm will process
      this file first, and all migration steps will be applied to all templates
      in the chart implicitly:
      https://github.com/helm/helm/blob/d58d7b376265338e059ff11c71267b5a6cf504c3/pkg/engine/engine.go#L362-L369
*/}}

{{/*
********************************************************************************
* Camunda 8.5 backward compatibility.

Overview:
    Backward compatibility with values syntax before Camunda Helm chart v10.0.0 (Camunda 8.5 cycle).

Approach:
    Using deep copy and deep merge functions to override new keys using the old key.
    https://helm.sh/docs/chart_template_guide/function_list/#mergeoverwrite-mustmergeoverwrite
********************************************************************************
*/}}


{{/*
Identity.
*/}}
{{- if .Values.identity.keycloak -}}
    {{- $_ := set .Values "identityKeycloak" (deepCopy .Values.identity.keycloak | mergeOverwrite .Values.identityKeycloak) -}}
{{- end -}}

{{- if .Values.identity.postgresql -}}
    {{- $_ := set .Values "identityPostgresql" (deepCopy .Values.identity.postgresql | mergeOverwrite .Values.identityPostgresql) -}}
{{- end -}}


{{/*
Zeebe Gateway.
*/}}

{{- if (index .Values "zeebe-gateway") -}}
    {{- $_ := set .Values "zeebeGateway" (deepCopy (index .Values "zeebe-gateway") | mergeOverwrite .Values.zeebeGateway) -}}
{{- end -}}

{{- if .Values.zeebeGateway.service.gatewayName -}}
    {{- $_ := set .Values.zeebeGateway.service "grpcName" .Values.zeebeGateway.service.gatewayName -}}
{{- end -}}

{{- if .Values.zeebeGateway.service.gatewayPort -}}
    {{- $_ := set .Values.zeebeGateway.service "grpcPort" .Values.zeebeGateway.service.gatewayPort -}}
{{- end -}}

{{- if .Values.zeebeGateway.ingress.enabled -}}
    {{- $zgIngress := omit .Values.zeebeGateway.ingress "rest" -}}
    {{- $_ := set .Values.zeebeGateway.ingress "grpc" (deepCopy $zgIngress | mergeOverwrite .Values.zeebeGateway.ingress.grpc) -}}
{{- end -}}

{{/*
OpenShift.
The `elasticsearch.sysctlImage` container adjusts the virtual memory and file descriptors of the machine needed for Elasticsearch.
By default, the `sysctlImage` container will fail on OpenShift because it requires privileged mode.
Also, recent OpenShift versions (> 4.10) have adjusted the virtual memory of the machine by default.
*/}}
{{- if eq .Values.global.compatibility.openshift.adaptSecurityContext "force" -}}
    {{- $_ := set .Values.elasticsearch.sysctlImage "enabled" false -}}
{{- end -}}


{{/*
OpenShift.
The label `tuned.openshift.io/elasticsearch` is added to ensure compatibility with the previous Camunda Helm charts.
Without this label, the Helm upgrade will fail for OpenShift because it is already set for the volumeClaimTemplate.
*/}}

{{- if .Values.elasticsearch.enabled -}}
  {{- if eq .Values.global.compatibility.openshift.adaptSecurityContext "force" -}}
      {{- if not (hasKey .Values.elasticsearch.commonLabels "tuned.openshift.io/elasticsearch") -}}
          {{- $_ := set .Values.elasticsearch.commonLabels "tuned.openshift.io/elasticsearch" "" -}}
      {{- end -}}
  {{- end -}}
{{- end -}}
{{/*
Elasticsearch.

Old:
- "global.elasticsearch.url" is a string (had priority over global.elasticsearch.{protocol, host, port})
- "global.elasticsearch.protocol", "global.elasticsearch.host, "global.elasticsearch.port".

New:
- "global.elasticsearch.url.protocol", "global.elasticsearch.url.host, "global.url.elasticsearch.port".

Notes:
- Helm CLI will show a warning like "cannot overwrite table with non table for", but the old syntax will still work.
*/}}
{{- if or (not .Values.global.elasticsearch.url) (not ((.Values.global.elasticsearch).url | default "")) -}}
    {{- $esProtocol := .Values.global.elasticsearch.protocol | default "http" -}}
    {{- $esHost := .Values.global.elasticsearch.host | default (print .Release.Name "-elasticsearch") -}}
    {{- $esPort := .Values.global.elasticsearch.port | default "9200" -}}
    {{- $_ := set .Values.global.elasticsearch "url" (dict "protocol" $esProtocol "host" $esHost "port" $esPort) -}}
{{- else if eq (kindOf .Values.global.elasticsearch.url) "string" -}}
    {{- $esURL := urlParse .Values.global.elasticsearch.url -}}
    {{- $esProtocol := $esURL.scheme | default .Values.global.elasticsearch.protocol | default "http" -}}
    {{- $esHost := ($esURL.host | splitList ":" | first) | default .Values.global.elasticsearch.host | default (print .Release.Name "-elasticsearch") -}}
    {{- $esPort := ($esURL.host | splitList ":" | last) | default .Values.global.elasticsearch.port | default "9200" -}}
    {{- $_ := set .Values.global.elasticsearch "url" (dict "protocol" $esProtocol "host" $esHost "port" $esPort) -}}
{{- end -}}
