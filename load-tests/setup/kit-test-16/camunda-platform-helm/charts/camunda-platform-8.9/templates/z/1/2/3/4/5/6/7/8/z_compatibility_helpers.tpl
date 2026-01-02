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
* Camunda 8.7 => 8.8

Overview:
    Backward compatibility to make the values of Camunda Helm chart v12.0.0 (Camunda 8.7)
    works with Camunda Helm chart v13.0.0 (Camunda 8.8).

Approach:
    Using deep copy and deep merge functions to override new keys using the old key.
    https://helm.sh/docs/chart_template_guide/function_list/#mergeoverwrite-mustmergeoverwrite

Note:
    Some values are not compatible with the new values syntax. Thus, the user needs to manually adjust their configurations.
********************************************************************************
*/}}

{{/*
Orchestration compatibility.
*/}}
{{- if .Values.global.compatibility.orchestration.enabled -}}
    {{/*
    Zeebe => Orchestration.
    Deep copy "zeebe" key as "orchestration" key, then set/override the new changes.
    */}}
    {{- if and .Values.zeebe .Values.zeebe.enabled -}}
        {{/* Deep copy "orchestration" as tmp var to set some keys later after the merge with "zeebe" key. */}}
        {{- $orchestrationOrig := deepCopy .Values.orchestration -}}
        {{/* Deep copy and merge "zeebe" with "orchestration" */}}
        {{- $_ := set .Values "orchestration" (deepCopy .Values.zeebe | mergeOverwrite .Values.orchestration) -}}

        {{/*
            Override keys with different values.
        */}}

        {{/*
            zeebe.resources => orchestration.resources
            Set the default values for the orchestration resources if they use the Zeebe default values,
            as the defaults have been changed in Camunda Helm chart v13.0.0 (Camunda 8.8).
            If the user has set custom values for the orchestration resources, they will not be overridden.
        */}}
        {{- if eq (((.Values.zeebe).resources).requests).cpu "800m" -}}
            {{- $_ := set .Values.orchestration.resources.requests "cpu" $orchestrationOrig.resources.requests.cpu -}}
        {{- end -}}
        {{- if eq (((.Values.zeebe).resources).requests).memory "1200Mi" -}}
            {{- $_ := set .Values.orchestration.resources.requests "memory" $orchestrationOrig.resources.requests.memory -}}
        {{- end -}}
        {{- if eq (((.Values.zeebe).resources).limits).cpu "960m" -}}
            {{- $_ := set .Values.orchestration.resources.limits "cpu" $orchestrationOrig.resources.limits.cpu -}}
        {{- end -}}
        {{- if eq (((.Values.zeebe).resources).limits).memory "1920Mi" -}}
            {{- $_ := set .Values.orchestration.resources.limits "memory" $orchestrationOrig.resources.limits.memory -}}
        {{- end -}}

        {{/*
            zeebe.javaOpts => orchestration.javaOpts
            NOTE: The key is already overwritten by Zeebe values, so we need to replace the value.
        */}}
        {{- $_ := set .Values.orchestration "javaOpts" (.Values.orchestration.javaOpts | replace "zeebe" "camunda") -}}
    {{- end -}}

    {{- if and .Values.zeebe -}}
        {{/*
            zeebe.enabled => orchestration.profiles.broker
        */}}
        {{- $_ := set .Values.orchestration.profiles "broker" .Values.zeebe.enabled -}}
    {{- end -}}

    {{/*
    Zeebe Gateway => Orchestration.
    */}}
    {{- if and .Values.zeebeGateway .Values.zeebeGateway.enabled -}}
        {{- if ((.Values.zeebeGateway).ingress) -}}
            {{- $_ := set .Values.orchestration "ingress" .Values.zeebeGateway.ingress -}}
        {{- end -}}
        {{- if ((.Values.zeebeGateway).contextPath) -}}
            {{- $_ := set .Values.orchestration "contextPath" .Values.zeebeGateway.contextPath -}}
        {{- end -}}
        {{- if ((.Values.zeebeGateway).service) -}}
            {{- if ((.Values.zeebeGateway.service).loadBalancerIP) -}}
                {{- $_ := set .Values.orchestration.service "loadBalancerIP" .Values.zeebeGateway.service.loadBalancerIP -}}
            {{- end -}}
            {{- if ((.Values.zeebeGateway.service).loadBalancerSourceRanges) -}}
                {{- $_ := set .Values.orchestration.service "loadBalancerSourceRanges" .Values.zeebeGateway.service.loadBalancerSourceRanges -}}
            {{- end -}}
            {{/* The key has been renamed in 8.8 for consistency, so now it's "httpPort" */}}
            {{- if ((.Values.zeebeGateway.service).restPort) -}}
                {{- $_ := set .Values.orchestration.service "httpPort" .Values.zeebeGateway.service.restPort -}}
            {{- end -}}
            {{- if ((.Values.zeebeGateway.service).grpcPort) -}}
                {{- $_ := set .Values.orchestration.service "grpcPort" .Values.zeebeGateway.service.grpcPort -}}
            {{- end -}}
            {{- if ((.Values.zeebeGateway.service).commandPort) -}}
                {{- $_ := set .Values.orchestration.service "commandPort" .Values.zeebeGateway.service.commandPort -}}
            {{- end -}}
            {{- if ((.Values.zeebeGateway.service).internalPort) -}}
                {{- $_ := set .Values.orchestration.service "internalPort" .Values.zeebeGateway.service.internalPort -}}
            {{- end -}}
        {{- end -}}
    {{- end -}}

    {{/*
    Operate => Orchestration.
    */}}
    {{- if and .Values.operate .Values.operate.enabled -}}
        {{- $_ := set .Values.orchestration.profiles "operate" .Values.operate.enabled -}}
    {{- end -}}

    {{/*
    Tasklist => Orchestration.
    */}}
    {{- if and .Values.tasklist .Values.tasklist.enabled -}}
        {{- $_ := set .Values.orchestration.profiles "tasklist" .Values.tasklist.enabled -}}
    {{- end -}}

    {{/*
    Global Orchestration Auth => Orchestration Auth.
    */}}
    {{- if and ((.Values.global.identity.auth).orchestration) .Values.orchestration.enabled -}}
        {{- $_ := set .Values.orchestration.security.authentication "oidc" (
          deepCopy .Values.global.identity.auth.orchestration |
            mergeOverwrite .Values.orchestration.security.authentication.oidc
        ) -}}
    {{- end -}}

    {{/*
    Global Connectors Auth => Connectors Auth.
    */}}
    {{- if and ((.Values.global.identity.auth).connectors) .Values.connectors.enabled -}}
        {{- $_ := set .Values.connectors.security.authentication "oidc" (
          deepCopy .Values.global.identity.auth.connectors |
            mergeOverwrite .Values.connectors.security.authentication.oidc
        ) -}}
    {{- end -}}
{{- end -}}

{{/*
Orchestration constraints.
Free-style inputs should be migrated manually by the user.
*/}}

{{- if .Values.global.compatibility.orchestration.enabled -}}
    {{/*
    Zeebe => Orchestration.
    */}}
    {{- if and .Values.zeebe .Values.zeebe.enabled -}}
        {{/*
        zeebe.configuration => orchestration.configuration
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebe.configuration)
            "oldName" "zeebe.configuration"
            "newName" "orchestration.configuration"
        ) }}
        {{/*
        zeebe.extraConfiguration => orchestration.extraConfiguration
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebe.extraConfiguration)
            "oldName" "zeebe.extraConfiguration"
            "newName" "orchestration.extraConfiguration"
        ) }}
        {{/*
        zeebe.env => orchestration.env
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebe.env)
            "oldName" "zeebe.env"
            "newName" "orchestration.env"
        ) }}
        {{/*
        zeebe.envFrom => orchestration.envFrom
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebe.envFrom)
            "oldName" "zeebe.envFrom"
            "newName" "orchestration.envFrom"
        ) }}
        {{/*
        zeebe.initContainers => orchestration.initContainers
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebe.initContainers)
            "oldName" "zeebe.initContainers"
            "newName" "orchestration.initContainers"
        ) }}
        {{/*
        zeebe.sidecars => orchestration.sidecars
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebe.sidecars)
            "oldName" "zeebe.sidecars"
            "newName" "orchestration.sidecars"
        ) }}
        {{/*
        zeebe.extraVolumes => orchestration.extraVolumes
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebe.extraVolumes)
            "oldName" "zeebe.extraVolumes"
            "newName" "orchestration.extraVolumes"
        ) }}
        {{/*
        zeebe.extraVolumeMounts => orchestration.extraVolumeMounts
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebe.extraVolumeMounts)
            "oldName" "zeebe.extraVolumeMounts"
            "newName" "orchestration.extraVolumeMounts"
        ) }}
    {{- end -}}

    {{/*
    Zeebe Gateway => Orchestration.
    */}}
    {{- if and .Values.zeebeGateway .Values.zeebeGateway.enabled -}}
        {{/*
        zeebeGateway.configuration => orchestration.configuration
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebeGateway.configuration)
            "oldName" "zeebeGateway.configuration"
            "newName" "orchestration.configuration"
        ) }}
        {{/*
        zeebeGateway.extraConfiguration => orchestration.extraConfiguration
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebeGateway.extraConfiguration)
            "oldName" "zeebeGateway.extraConfiguration"
            "newName" "orchestration.extraConfiguration"
        ) }}
        {{/*
        zeebeGateway.env => orchestration.env
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebeGateway.env)
            "oldName" "zeebeGateway.env"
            "newName" "orchestration.env"
        ) }}
        {{/*
        zeebeGateway.envFrom => orchestration.envFrom
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebeGateway.envFrom)
            "oldName" "zeebeGateway.envFrom"
            "newName" "orchestration.envFrom"
        ) }}
        {{/*
        zeebeGateway.initContainers => orchestration.initContainers
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebeGateway.initContainers)
            "oldName" "zeebeGateway.initContainers"
            "newName" "orchestration.initContainers"
        ) }}
        {{/*
        zeebeGateway.sidecars => orchestration.sidecars
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebeGateway.sidecars)
            "oldName" "zeebeGateway.sidecars"
            "newName" "orchestration.sidecars"
        ) }}
        {{/*
        zeebeGateway.extraVolumes => orchestration.extraVolumes
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebeGateway.extraVolumes)
            "oldName" "zeebeGateway.extraVolumes"
            "newName" "orchestration.extraVolumes"
        ) }}
        {{/*
        zeebeGateway.extraVolumeMounts => orchestration.extraVolumeMounts
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.zeebeGateway.extraVolumeMounts)
            "oldName" "zeebeGateway.extraVolumeMounts"
            "newName" "orchestration.extraVolumeMounts"
        ) }}
    {{- end -}}

    {{/*
    Operate => Orchestration.
    */}}
    {{- if and .Values.operate .Values.operate.enabled -}}
        {{/*
        operate.configuration => orchestration.configuration
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.operate.configuration)
            "oldName" "operate.configuration"
            "newName" "orchestration.configuration"
        ) }}
        {{/*
        operate.extraConfiguration => orchestration.extraConfiguration
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.operate.extraConfiguration)
            "oldName" "operate.extraConfiguration"
            "newName" "orchestration.extraConfiguration"
        ) }}
        {{/*
        operate.env => orchestration.env
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.operate.env)
            "oldName" "operate.env"
            "newName" "orchestration.env"
        ) }}
        {{/*
        operate.envFrom => orchestration.envFrom
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.operate.envFrom)
            "oldName" "operate.envFrom"
            "newName" "orchestration.envFrom"
        ) }}
        {{/*
        operate.initContainers => orchestration.initContainers
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.operate.initContainers)
            "oldName" "operate.initContainers"
            "newName" "orchestration.initContainers"
        ) }}
        {{/*
        operate.sidecars => orchestration.sidecars
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.operate.sidecars)
            "oldName" "operate.sidecars"
            "newName" "orchestration.sidecars"
        ) }}
        {{/*
        operate.extraVolumes => orchestration.extraVolumes
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.operate.extraVolumes)
            "oldName" "operate.extraVolumes"
            "newName" "orchestration.extraVolumes"
        ) }}
        {{/*
        operate.extraVolumeMounts => orchestration.extraVolumeMounts
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.operate.extraVolumeMounts)
            "oldName" "operate.extraVolumeMounts"
            "newName" "orchestration.extraVolumeMounts"
        ) }}
    {{- end -}}

    {{/*
    Tasklist => Orchestration.
    */}}
    {{- if and .Values.tasklist .Values.tasklist.enabled -}}
        {{/*
        tasklist.configuration => orchestration.configuration
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.tasklist.configuration)
            "oldName" "tasklist.configuration"
            "newName" "orchestration.configuration"
        ) }}
        {{/*
        tasklist.extraConfiguration => orchestration.extraConfiguration
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.tasklist.extraConfiguration)
            "oldName" "tasklist.extraConfiguration"
            "newName" "orchestration.extraConfiguration"
        ) }}
        {{/*
        tasklist.env => orchestration.env
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.tasklist.env)
            "oldName" "tasklist.env"
            "newName" "orchestration.env"
        ) }}
        {{/*
        tasklist.envFrom => orchestration.envFrom
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.tasklist.envFrom)
            "oldName" "tasklist.envFrom"
            "newName" "orchestration.envFrom"
        ) }}
        {{/*
        tasklist.initContainers => orchestration.initContainers
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.tasklist.initContainers)
            "oldName" "tasklist.initContainers"
            "newName" "orchestration.initContainers"
        ) }}
        {{/*
        tasklist.sidecars => orchestration.sidecars
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.tasklist.sidecars)
            "oldName" "tasklist.sidecars"
            "newName" "orchestration.sidecars"
        ) }}
        {{/*
        tasklist.extraVolumes => orchestration.extraVolumes
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.tasklist.extraVolumes)
            "oldName" "tasklist.extraVolumes"
            "newName" "orchestration.extraVolumes"
        ) }}
        {{/*
        tasklist.extraVolumeMounts => orchestration.extraVolumeMounts
        */}}
        {{ include "camundaPlatform.manualMigrationRequired" (dict
            "condition" (.Values.tasklist.extraVolumeMounts)
            "oldName" "tasklist.extraVolumeMounts"
            "newName" "orchestration.extraVolumeMounts"
        ) }}
    {{- end -}}
{{- end -}}

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
This is only needed when elasticsearch is actually enabled and deployed.
*/}}

{{- if and (eq .Values.global.compatibility.openshift.adaptSecurityContext "force") .Values.elasticsearch.enabled -}}
    {{- if not (hasKey .Values.elasticsearch "commonLabels") -}}
        {{- $_ := set .Values.elasticsearch "commonLabels" (dict) -}}
    {{- end -}}
    {{- if not (hasKey .Values.elasticsearch.commonLabels "tuned.openshift.io/elasticsearch") -}}
        {{- $_ := set .Values.elasticsearch.commonLabels "tuned.openshift.io/elasticsearch" "" -}}
    {{- end -}}
{{- end -}}
