#!/usr/bin/env python3
"""
Eliminate ProtocolBridge from all Default*ServiceAdapter files.

This script:
1. Generates strict-contract overloads for mapper methods
2. Transforms adapter files to use strict overloads or record accessors
3. Cleans up imports (removes ProtocolBridge + unused protocol model imports)

Run from the monorepo root:
  python3 gateways/gateway-mapping-http/tools/eliminate_protocol_bridge.py
"""
import os
import re
import sys
from pathlib import Path

# ==== CONFIGURATION ====

REPO_ROOT = Path(__file__).resolve().parents[3]  # gateways/../../../

ADAPTER_DIR = REPO_ROOT / "zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/generated"
MAPPER_BASE = REPO_ROOT / "gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http"

STRICT_PKG = "io.camunda.gateway.mapping.http.search.contract.generated"
PROTO_PKG = "io.camunda.gateway.protocol.model"

# Mapping from protocol model class to strict contract class 
# Convention: Generated{ProtoClassName}StrictContract
def strict_class(proto_class: str) -> str:
    return f"Generated{proto_class}StrictContract"


# ==== PHASE 1: Generate mapper overloads ====

# Each entry: (mapper_file_relative, method_name, proto_param_type, extra_params_before, extra_params_after)
# extra_params_before/after are the other parameters (not the protocol model) with types

REQUESTMAPPER_OVERLOADS = [
    # (method_name, proto_type, return_type_is_either, params_signature, delegation_call)
    # Params use {strict} as placeholder for the bridged param in delegation
    ("toUserTaskCompletionRequest", "UserTaskCompletionRequest",
     "public static CompleteUserTaskRequest toUserTaskCompletionRequest(\n"
     "      final {strict_type} completionRequest, final long userTaskKey) {{\n"
     "    return toUserTaskCompletionRequest(\n"
     "        ProtocolBridge.toProtocol(completionRequest, {proto_type}.class), userTaskKey);\n"
     "  }}"),
    
    ("toUserTaskAssignmentRequest", "UserTaskAssignmentRequest",
     "public static Either<ProblemDetail, AssignUserTaskRequest> toUserTaskAssignmentRequest(\n"
     "      final {strict_type} assignmentRequest, final long userTaskKey) {{\n"
     "    return toUserTaskAssignmentRequest(\n"
     "        ProtocolBridge.toProtocol(assignmentRequest, {proto_type}.class), userTaskKey);\n"
     "  }}"),

    ("toUserTaskUpdateRequest", "UserTaskUpdateRequest",
     "public static Either<ProblemDetail, UpdateUserTaskRequest> toUserTaskUpdateRequest(\n"
     "      final {strict_type} updateRequest, final long userTaskKey) {{\n"
     "    return toUserTaskUpdateRequest(\n"
     "        ProtocolBridge.toProtocol(updateRequest, {proto_type}.class), userTaskKey);\n"
     "  }}"),

    ("toJobsActivationRequest", "JobActivationRequest",
     "public static Either<ProblemDetail, ActivateJobsRequest> toJobsActivationRequest(\n"
     "      final {strict_type} activationRequest, final boolean multiTenancyEnabled) {{\n"
     "    return toJobsActivationRequest(\n"
     "        ProtocolBridge.toProtocol(activationRequest, {proto_type}.class), multiTenancyEnabled);\n"
     "  }}"),

    ("toJobFailRequest", "JobFailRequest",
     "public static FailJobRequest toJobFailRequest(\n"
     "      final {strict_type} failRequest, final long jobKey) {{\n"
     "    return toJobFailRequest(\n"
     "        ProtocolBridge.toProtocol(failRequest, {proto_type}.class), jobKey);\n"
     "  }}"),

    ("toJobErrorRequest", "JobErrorRequest",
     "public static Either<ProblemDetail, ErrorJobRequest> toJobErrorRequest(\n"
     "      final {strict_type} errorRequest, final long jobKey) {{\n"
     "    return toJobErrorRequest(\n"
     "        ProtocolBridge.toProtocol(errorRequest, {proto_type}.class), jobKey);\n"
     "  }}"),

    ("toJobCompletionRequest", "JobCompletionRequest",
     "public static CompleteJobRequest toJobCompletionRequest(\n"
     "      final {strict_type} completionRequest, final long jobKey) {{\n"
     "    return toJobCompletionRequest(\n"
     "        ProtocolBridge.toProtocol(completionRequest, {proto_type}.class), jobKey);\n"
     "  }}"),

    ("toJobUpdateRequest", "JobUpdateRequest",
     "public static Either<ProblemDetail, UpdateJobRequest> toJobUpdateRequest(\n"
     "      final {strict_type} updateRequest, final long jobKey) {{\n"
     "    return toJobUpdateRequest(\n"
     "        ProtocolBridge.toProtocol(updateRequest, {proto_type}.class), jobKey);\n"
     "  }}"),

    ("toBroadcastSignalRequest", "SignalBroadcastRequest",
     "public static Either<ProblemDetail, BroadcastSignalRequest> toBroadcastSignalRequest(\n"
     "      final {strict_type} request, final boolean multiTenancyEnabled) {{\n"
     "    return toBroadcastSignalRequest(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class), multiTenancyEnabled);\n"
     "  }}"),

    ("toResourceDeletion", "DeleteResourceRequest",
     "public static Either<ProblemDetail, ResourceDeletionRequest> toResourceDeletion(\n"
     "      final long resourceKey, final {strict_type} deleteRequest) {{\n"
     "    return toResourceDeletion(\n"
     "        resourceKey, ProtocolBridge.toProtocol(deleteRequest, {proto_type}.class));\n"
     "  }}"),

    ("toDocumentCreateRequest", "DocumentMetadata",
     "public static Either<ProblemDetail, DocumentCreateRequest> toDocumentCreateRequest(\n"
     "      final String documentId, final String storeId, final Part file,\n"
     "      final {strict_type} metadata) {{\n"
     "    return toDocumentCreateRequest(\n"
     "        documentId, storeId, file, ProtocolBridge.toProtocol(metadata, {proto_type}.class));\n"
     "  }}"),

    ("toDocumentCreateRequestBatch_strict", "DocumentMetadata",
     "public static Either<ProblemDetail, List<DocumentCreateRequest>> toDocumentCreateRequestBatch(\n"
     "      final List<Part> parts, final String storeId, final ObjectMapper objectMapper,\n"
     "      final List<{strict_type}> metadataList) {{\n"
     "    return toDocumentCreateRequestBatch(\n"
     "        parts, storeId, objectMapper,\n"
     "        metadataList.stream().map(m -> ProtocolBridge.toProtocol(m, {proto_type}.class)).toList());\n"
     "  }}"),

    ("toDocumentLinkParams", "DocumentLinkRequest",
     "public static Either<ProblemDetail, DocumentLinkParams> toDocumentLinkParams(\n"
     "      final {strict_type} documentLinkRequest) {{\n"
     "    return toDocumentLinkParams(\n"
     "        ProtocolBridge.toProtocol(documentLinkRequest, {proto_type}.class));\n"
     "  }}"),

    ("toCancelProcessInstance", "CancelProcessInstanceRequest",
     "public static Either<ProblemDetail, ProcessInstanceCancelRequest> toCancelProcessInstance(\n"
     "      final long processInstanceKey, final {strict_type} request) {{\n"
     "    return toCancelProcessInstance(\n"
     "        processInstanceKey, ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toRequiredProcessInstanceFilter_strict", "ProcessInstanceFilter",
     "public static Either<ProblemDetail, io.camunda.search.filter.ProcessInstanceFilter> toRequiredProcessInstanceFilter(\n"
     "      final {strict_type} request) {{\n"
     "    return toRequiredProcessInstanceFilter(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toProcessInstanceMigrationBatchOperationRequest", "ProcessInstanceMigrationBatchOperationRequest",
     "public static Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest>\n"
     "      toProcessInstanceMigrationBatchOperationRequest(\n"
     "          final {strict_type} request) {{\n"
     "    return toProcessInstanceMigrationBatchOperationRequest(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toProcessInstanceModifyBatchOperationRequest", "ProcessInstanceModificationBatchOperationRequest",
     "public static Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest>\n"
     "      toProcessInstanceModifyBatchOperationRequest(\n"
     "          final {strict_type} request) {{\n"
     "    return toProcessInstanceModifyBatchOperationRequest(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toMigrateProcessInstance", "ProcessInstanceMigrationInstruction",
     "public static Either<ProblemDetail, ProcessInstanceMigrateRequest> toMigrateProcessInstance(\n"
     "      final long processInstanceKey, final {strict_type} request) {{\n"
     "    return toMigrateProcessInstance(\n"
     "        processInstanceKey, ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toModifyProcessInstance", "ProcessInstanceModificationInstruction",
     "public static Either<ProblemDetail, ProcessInstanceModifyRequest> toModifyProcessInstance(\n"
     "      final long processInstanceKey, final {strict_type} request) {{\n"
     "    return toModifyProcessInstance(\n"
     "        processInstanceKey, ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toMessagePublicationRequest", "MessagePublicationRequest",
     "public static Either<ProblemDetail, PublicationMessageRequest> toMessagePublicationRequest(\n"
     "      final {strict_type} messagePublicationRequest,\n"
     "      final boolean multiTenancyEnabled, final int maxNameFieldLength) {{\n"
     "    return toMessagePublicationRequest(\n"
     "        ProtocolBridge.toProtocol(messagePublicationRequest, {proto_type}.class),\n"
     "        multiTenancyEnabled, maxNameFieldLength);\n"
     "  }}"),

    ("toMessageCorrelationRequest", "MessageCorrelationRequest",
     "public static Either<ProblemDetail, CorrelateMessageRequest> toMessageCorrelationRequest(\n"
     "      final {strict_type} correlationRequest,\n"
     "      final boolean multiTenancyEnabled, final int maxNameFieldLength) {{\n"
     "    return toMessageCorrelationRequest(\n"
     "        ProtocolBridge.toProtocol(correlationRequest, {proto_type}.class),\n"
     "        multiTenancyEnabled, maxNameFieldLength);\n"
     "  }}"),

    ("toEvaluateConditionalRequest", "ConditionalEvaluationInstruction",
     "public static Either<ProblemDetail, EvaluateConditionalRequest> toEvaluateConditionalRequest(\n"
     "      final {strict_type} request, final boolean multiTenancyEnabled) {{\n"
     "    return toEvaluateConditionalRequest(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class), multiTenancyEnabled);\n"
     "  }}"),

    ("toAdHocSubProcessActivateActivitiesRequest", "AdHocSubProcessActivateActivitiesInstruction",
     "public static Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest>\n"
     "      toAdHocSubProcessActivateActivitiesRequest(\n"
     "          final String adHocSubProcessInstanceKey, final {strict_type} request) {{\n"
     "    return toAdHocSubProcessActivateActivitiesRequest(\n"
     "        adHocSubProcessInstanceKey, ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),
]

SEARCHQUERY_OVERLOADS = [
    ("toJobTypeStatisticsQuery", "JobTypeStatisticsQuery",
     "public static Either<ProblemDetail, io.camunda.search.query.JobTypeStatisticsQuery>\n"
     "      toJobTypeStatisticsQuery(final {strict_type} request) {{\n"
     "    return toJobTypeStatisticsQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toJobWorkerStatisticsQuery", "JobWorkerStatisticsQuery",
     "public static Either<ProblemDetail, io.camunda.search.query.JobWorkerStatisticsQuery>\n"
     "      toJobWorkerStatisticsQuery(final {strict_type} request) {{\n"
     "    return toJobWorkerStatisticsQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toJobTimeSeriesStatisticsQuery", "JobTimeSeriesStatisticsQuery",
     "public static Either<ProblemDetail, io.camunda.search.query.JobTimeSeriesStatisticsQuery>\n"
     "      toJobTimeSeriesStatisticsQuery(final {strict_type} request) {{\n"
     "    return toJobTimeSeriesStatisticsQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toJobErrorStatisticsQuery", "JobErrorStatisticsQuery",
     "public static Either<ProblemDetail, io.camunda.search.query.JobErrorStatisticsQuery>\n"
     "      toJobErrorStatisticsQuery(final {strict_type} request) {{\n"
     "    return toJobErrorStatisticsQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toGlobalTaskListenerQuery", "GlobalTaskListenerSearchQueryRequest",
     "public static Either<ProblemDetail, GlobalListenerQuery> toGlobalTaskListenerQuery(\n"
     "      final {strict_type} request) {{\n"
     "    return toGlobalTaskListenerQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toIncidentProcessInstanceStatisticsByErrorQuery", "IncidentProcessInstanceStatisticsByErrorQuery",
     "public static Either<ProblemDetail, io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery>\n"
     "      toIncidentProcessInstanceStatisticsByErrorQuery(final {strict_type} request) {{\n"
     "    return toIncidentProcessInstanceStatisticsByErrorQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toIncidentProcessInstanceStatisticsByDefinitionQuery", "IncidentProcessInstanceStatisticsByDefinitionQuery",
     "public static Either<ProblemDetail, io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery>\n"
     "      toIncidentProcessInstanceStatisticsByDefinitionQuery(final {strict_type} request) {{\n"
     "    return toIncidentProcessInstanceStatisticsByDefinitionQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toProcessDefinitionQuery", "ProcessDefinitionSearchQuery",
     "public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(\n"
     "      final {strict_type} request) {{\n"
     "    return toProcessDefinitionQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toProcessDefinitionStatisticsQuery", "ProcessDefinitionElementStatisticsQuery",
     "public static Either<ProblemDetail, ProcessDefinitionStatisticsFilter>\n"
     "      toProcessDefinitionStatisticsQuery(\n"
     "          final long processDefinitionKey, final {strict_type} request) {{\n"
     "    return toProcessDefinitionStatisticsQuery(\n"
     "        processDefinitionKey, ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toProcessDefinitionMessageSubscriptionStatisticsQuery", "ProcessDefinitionMessageSubscriptionStatisticsQuery",
     "public static Either<ProblemDetail, io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery>\n"
     "      toProcessDefinitionMessageSubscriptionStatisticsQuery(final {strict_type} request) {{\n"
     "    return toProcessDefinitionMessageSubscriptionStatisticsQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toProcessDefinitionInstanceStatisticsQuery", "ProcessDefinitionInstanceStatisticsQuery",
     "public static Either<ProblemDetail, io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery>\n"
     "      toProcessDefinitionInstanceStatisticsQuery(final {strict_type} request) {{\n"
     "    return toProcessDefinitionInstanceStatisticsQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toProcessDefinitionInstanceVersionStatisticsQuery", "ProcessDefinitionInstanceVersionStatisticsQuery",
     "public static Either<ProblemDetail, io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery>\n"
     "      toProcessDefinitionInstanceVersionStatisticsQuery(final {strict_type} request) {{\n"
     "    return toProcessDefinitionInstanceVersionStatisticsQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("toCorrelatedMessageSubscriptionQuery", "CorrelatedMessageSubscriptionSearchQuery",
     "public static Either<ProblemDetail, CorrelatedMessageSubscriptionQuery>\n"
     "      toCorrelatedMessageSubscriptionQuery(\n"
     "          final {strict_type} request) {{\n"
     "    return toCorrelatedMessageSubscriptionQuery(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),
]

# Dedicated mapper overloads: (mapper_file, method_name, proto_type, template)
DEDICATED_OVERLOADS = [
    ("mapper/GroupMapper.java", "toGroupCreateRequest", "GroupCreateRequest",
     "public Either<ProblemDetail, GroupDTO> toGroupCreateRequest(\n"
     "      final {strict_type} groupCreateRequest) {{\n"
     "    return toGroupCreateRequest(\n"
     "        ProtocolBridge.toProtocol(groupCreateRequest, {proto_type}.class));\n"
     "  }}"),

    ("mapper/GroupMapper.java", "toGroupUpdateRequest", "GroupUpdateRequest",
     "public Either<ProblemDetail, GroupDTO> toGroupUpdateRequest(\n"
     "      final {strict_type} groupUpdateRequest, final String groupId) {{\n"
     "    return toGroupUpdateRequest(\n"
     "        ProtocolBridge.toProtocol(groupUpdateRequest, {proto_type}.class), groupId);\n"
     "  }}"),

    ("mapper/TenantMapper.java", "toTenantCreateDto", "TenantCreateRequest",
     "public Either<ProblemDetail, TenantRequest> toTenantCreateDto(\n"
     "      final {strict_type} tenantCreateRequest) {{\n"
     "    return toTenantCreateDto(\n"
     "        ProtocolBridge.toProtocol(tenantCreateRequest, {proto_type}.class));\n"
     "  }}"),

    ("mapper/TenantMapper.java", "toTenantUpdateDto", "TenantUpdateRequest",
     "public Either<ProblemDetail, TenantRequest> toTenantUpdateDto(\n"
     "      final String tenantId, final {strict_type} tenantUpdateRequest) {{\n"
     "    return toTenantUpdateDto(\n"
     "        tenantId, ProtocolBridge.toProtocol(tenantUpdateRequest, {proto_type}.class));\n"
     "  }}"),

    ("mapper/RoleMapper.java", "toRoleCreateRequest", "RoleCreateRequest",
     "public Either<ProblemDetail, CreateRoleRequest> toRoleCreateRequest(\n"
     "      final {strict_type} roleCreateRequest) {{\n"
     "    return toRoleCreateRequest(\n"
     "        ProtocolBridge.toProtocol(roleCreateRequest, {proto_type}.class));\n"
     "  }}"),

    ("mapper/RoleMapper.java", "toRoleUpdateRequest", "RoleUpdateRequest",
     "public Either<ProblemDetail, UpdateRoleRequest> toRoleUpdateRequest(\n"
     "      final {strict_type} roleUpdateRequest, final String roleId) {{\n"
     "    return toRoleUpdateRequest(\n"
     "        ProtocolBridge.toProtocol(roleUpdateRequest, {proto_type}.class), roleId);\n"
     "  }}"),

    ("mapper/ClusterVariableMapper.java", "toGlobalClusterVariableCreateRequest", "CreateClusterVariableRequest",
     "public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableCreateRequest(\n"
     "      final {strict_type} request) {{\n"
     "    return toGlobalClusterVariableCreateRequest(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("mapper/ClusterVariableMapper.java", "toTenantClusterVariableCreateRequest", "CreateClusterVariableRequest",
     "public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableCreateRequest(\n"
     "      final {strict_type} request, final String tenantId) {{\n"
     "    return toTenantClusterVariableCreateRequest(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class), tenantId);\n"
     "  }}"),

    ("mapper/ClusterVariableMapper.java", "toGlobalClusterVariableUpdateRequest", "UpdateClusterVariableRequest",
     "public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableUpdateRequest(\n"
     "      final String name, final {strict_type} request) {{\n"
     "    return toGlobalClusterVariableUpdateRequest(\n"
     "        name, ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("mapper/ClusterVariableMapper.java", "toTenantClusterVariableUpdateRequest", "UpdateClusterVariableRequest",
     "public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableUpdateRequest(\n"
     "      final String name, final {strict_type} request, final String tenantId) {{\n"
     "    return toTenantClusterVariableUpdateRequest(\n"
     "        name, ProtocolBridge.toProtocol(request, {proto_type}.class), tenantId);\n"
     "  }}"),

    ("mapper/UserMapper.java", "toUserRequest", "UserRequest",
     "public Either<ProblemDetail, UserDTO> toUserRequest(\n"
     "      final {strict_type} request) {{\n"
     "    return toUserRequest(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("mapper/UserMapper.java", "toUserUpdateRequest", "UserUpdateRequest",
     "public Either<ProblemDetail, UserDTO> toUserUpdateRequest(\n"
     "      final {strict_type} updateRequest, final String username) {{\n"
     "    return toUserUpdateRequest(\n"
     "        ProtocolBridge.toProtocol(updateRequest, {proto_type}.class), username);\n"
     "  }}"),

    ("mapper/GlobalListenerMapper.java", "toGlobalTaskListenerCreateRequest", "CreateGlobalTaskListenerRequest",
     "public Either<ProblemDetail, GlobalListenerRecord> toGlobalTaskListenerCreateRequest(\n"
     "      final {strict_type} request) {{\n"
     "    return toGlobalTaskListenerCreateRequest(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("mapper/GlobalListenerMapper.java", "toGlobalTaskListenerUpdateRequest", "UpdateGlobalTaskListenerRequest",
     "public Either<ProblemDetail, GlobalListenerRecord> toGlobalTaskListenerUpdateRequest(\n"
     "      final String id, final {strict_type} request) {{\n"
     "    return toGlobalTaskListenerUpdateRequest(\n"
     "        id, ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("mapper/MappingRuleMapper.java", "toMappingRuleCreateRequest", "MappingRuleCreateRequest",
     "public Either<ProblemDetail, MappingRuleDTO> toMappingRuleCreateRequest(\n"
     "      final {strict_type} request) {{\n"
     "    return toMappingRuleCreateRequest(\n"
     "        ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),

    ("mapper/MappingRuleMapper.java", "toMappingRuleUpdateRequest", "MappingRuleUpdateRequest",
     "public Either<ProblemDetail, MappingRuleDTO> toMappingRuleUpdateRequest(\n"
     "      final String mappingRuleId, final {strict_type} request) {{\n"
     "    return toMappingRuleUpdateRequest(\n"
     "        mappingRuleId, ProtocolBridge.toProtocol(request, {proto_type}.class));\n"
     "  }}"),
]


def generate_overload(template: str, proto_type: str) -> str:
    """Generate an overload method from template."""
    st = strict_class(proto_type)
    return template.format(strict_type=st, proto_type=proto_type)


def add_overloads_to_file(filepath: Path, overloads: list, static_methods: bool = True):
    """Add overload methods to a mapper file, just before the closing brace."""
    content = filepath.read_text()
    
    # Find the last closing brace (end of class)
    last_brace = content.rfind("}")
    if last_brace < 0:
        print(f"  WARN: No closing brace found in {filepath}")
        return False
    
    # Collect imports needed
    strict_types_needed = set()
    for _, proto_type, template in overloads:
        strict_types_needed.add(strict_class(proto_type))
    
    # Generate all overload methods
    methods = []
    for _, proto_type, template in overloads:
        methods.append("  " + generate_overload(template, proto_type))
    
    overload_block = "\n\n  // ---- Strict contract overloads (transitional) ----\n\n" + "\n\n".join(methods) + "\n"
    
    # Insert before last closing brace
    new_content = content[:last_brace] + overload_block + content[last_brace:]
    
    # Add imports for strict contract types
    import_block = ""
    for st in sorted(strict_types_needed):
        import_line = f"import {STRICT_PKG}.{st};"
        if import_line not in new_content:
            import_block += import_line + "\n"
    
    # Add ProtocolBridge import if not present
    bridge_import = "import io.camunda.gateway.mapping.http.ProtocolBridge;"
    if bridge_import not in new_content:
        import_block += bridge_import + "\n"
    
    if import_block:
        # Insert after the last existing import
        last_import = max(new_content.rfind("\nimport "), 0)
        end_of_import_line = new_content.index("\n", last_import + 1) + 1
        new_content = new_content[:end_of_import_line] + import_block + new_content[end_of_import_line:]
    
    filepath.write_text(new_content)
    return True


def phase1_generate_overloads():
    """Generate all mapper overloads."""
    print("=== Phase 1: Generating mapper overloads ===")
    
    # RequestMapper overloads
    rm_path = MAPPER_BASE / "RequestMapper.java"
    rm_overloads = [(name, proto, tmpl) for name, proto, tmpl in REQUESTMAPPER_OVERLOADS]
    print(f"  Adding {len(rm_overloads)} overloads to RequestMapper.java")
    add_overloads_to_file(rm_path, rm_overloads)
    
    # SearchQueryRequestMapper overloads
    sqrm_path = MAPPER_BASE / "search" / "SearchQueryRequestMapper.java"
    sqrm_overloads = [(name, proto, tmpl) for name, proto, tmpl in SEARCHQUERY_OVERLOADS]
    print(f"  Adding {len(sqrm_overloads)} overloads to SearchQueryRequestMapper.java")
    add_overloads_to_file(sqrm_path, sqrm_overloads)
    
    # Dedicated mapper overloads (grouped by file)
    by_file = {}
    for mapper_file, name, proto, tmpl in DEDICATED_OVERLOADS:
        by_file.setdefault(mapper_file, []).append((name, proto, tmpl))
    
    for mapper_file, overloads in by_file.items():
        path = MAPPER_BASE / mapper_file
        print(f"  Adding {len(overloads)} overloads to {mapper_file}")
        add_overloads_to_file(path, overloads)
    
    print(f"  Total: {len(rm_overloads) + len(sqrm_overloads) + len(DEDICATED_OVERLOADS)} overloads added")


# ==== PHASE 2: Transform adapter files ====

def transform_adapter(filepath: Path) -> int:
    """Transform a single adapter file to eliminate ProtocolBridge usage.
    Returns the number of ProtocolBridge calls eliminated."""
    content = filepath.read_text()
    original = content
    changes = 0
    
    # Pattern A: Single-line bridge assignment followed by usage
    # final ProtoType var = ProtocolBridge.toProtocol(strictVar, ProtoType.class);
    bridge_pattern = re.compile(
        r'(\s+)final\s+(\w+(?:\.\w+)*)\s+(\w+)\s*=\s*ProtocolBridge\.toProtocol\((\w+),\s*(\w+(?:\.\w+)*)\.class\);'
    )
    
    # Find all bridge assignments
    matches = list(bridge_pattern.finditer(content))
    
    # Process in reverse order to maintain correct positions
    for match in reversed(matches):
        indent = match.group(1)
        proto_type = match.group(2)
        var_name = match.group(3)
        strict_var = match.group(4)
        class_name = match.group(5)
        
        full_line = match.group(0)
        start = match.start()
        end = match.end()
        
        # Check if this is a direct field extraction case
        # Look at subsequent code to see if we can just replace var with strict accessor
        remaining = content[end:]
        
        # Check for direct field access patterns (e.g., var.getTimestamp())
        # For records, getTimestamp() -> strict.timestamp()
        is_direct_extraction = is_direct_field_extraction(content, var_name, end)
        
        if is_direct_extraction:
            # Replace getter calls with record accessors
            content = replace_getters_with_accessors(content, var_name, strict_var, end)
            # Remove the bridge line
            content = content[:start] + content[end:]
            changes += 1
        else:
            # Replace the var with the strict var in subsequent mapper calls
            # Remove the bridge line and replace var references with strict var
            content = content[:start] + content[end:]
            content = replace_var_in_scope(content, var_name, strict_var, start)
            changes += 1
    
    # Handle stream-based bridge calls:
    # .stream().map(m -> ProtocolBridge.toProtocol(m, Type.class)).toList()
    stream_pattern = re.compile(
        r'\.stream\(\)\.map\((\w+)\s*->\s*ProtocolBridge\.toProtocol\(\1,\s*(\w+(?:\.\w+)*)\.class\)\)\.toList\(\)'
    )
    # For stream calls, we keep them but they should go through the mapper overload
    # Actually, the batch overload handles this internally now
    
    if changes > 0:
        # Clean up imports
        content = clean_imports(content, filepath.name)
        filepath.write_text(content)
        
    return changes


def is_direct_field_extraction(content: str, var_name: str, pos: int) -> bool:
    """Check if the variable is only used for direct getter calls (not passed to a mapper)."""
    # Look at the next ~500 chars for usage patterns
    scope = content[pos:pos+1000]
    
    # Find all usages of the variable
    uses = list(re.finditer(rf'\b{re.escape(var_name)}\b', scope))
    
    if not uses:
        return False
    
    # Check if ALL uses are getter calls (var.getSomething()) 
    # versus being passed as an argument to a method
    for use in uses:
        # Check what follows the variable name
        after = scope[use.end():use.end()+50].lstrip()
        if after.startswith('.get') or after.startswith('.is'):
            continue  # Getter call - OK
        else:
            return False  # Passed as argument - not direct extraction
    
    return True


def replace_getters_with_accessors(content: str, var_name: str, strict_var: str, start_pos: int) -> str:
    """Replace protocol model getter calls with record accessor calls.
    e.g., var.getTimestamp() -> strict.timestamp()
         var.getOperationReference() -> strict.operationReference()
         var.getVariables() -> strict.variables()
         var.getLocal() -> strict.local()
         var.isLocal() -> strict.local()
    """
    # Replace var.getXxx() with strict.xxx()
    def getter_to_accessor(m):
        prefix = m.group(1)  # get or is
        field = m.group(2)
        # Convert field name: first char lowercase
        accessor = field[0].lower() + field[1:]
        return f'{strict_var}.{accessor}()'
    
    # Only replace after start_pos
    before = content[:start_pos]
    after = content[start_pos:]
    
    after = re.sub(
        rf'\b{re.escape(var_name)}\.(get|is)([A-Z]\w*)\(\)',
        getter_to_accessor,
        after
    )
    
    return before + after


def replace_var_in_scope(content: str, old_var: str, new_var: str, pos: int) -> str:
    """Replace all occurrences of old_var with new_var after pos, within the method scope."""
    before = content[:pos]
    after = content[pos:]
    
    # Replace the variable name in the rest of the method
    # Be careful to only replace whole words
    after = re.sub(rf'\b{re.escape(old_var)}\b', new_var, after, count=20)
    
    return before + after


def clean_imports(content: str, filename: str) -> str:
    """Remove ProtocolBridge import and unused protocol model imports."""
    lines = content.split('\n')
    new_lines = []
    removed = []
    
    for line in lines:
        # Remove ProtocolBridge import
        if 'import io.camunda.gateway.mapping.http.ProtocolBridge;' in line:
            removed.append(line.strip())
            continue
        
        # Remove protocol model imports that are no longer used
        proto_import = re.match(r'^import\s+(io\.camunda\.gateway\.protocol\.model\.(\w+));', line)
        if proto_import:
            full_import = proto_import.group(1)
            class_name = proto_import.group(2)
            # Check if this class is still referenced anywhere in the file (excluding imports)
            non_import_content = '\n'.join(l for l in lines if not l.strip().startswith('import '))
            if class_name not in non_import_content:
                removed.append(line.strip())
                continue
        
        new_lines.append(line)
    
    if removed:
        print(f"    Removed {len(removed)} imports from {filename}")
    
    # Remove double blank lines that may result from import removal
    result = '\n'.join(new_lines)
    result = re.sub(r'\n{3,}', '\n\n', result)
    
    return result


def phase2_transform_adapters():
    """Transform all adapter files."""
    print("\n=== Phase 2: Transforming adapter files ===")
    
    total_changes = 0
    adapter_files = sorted(ADAPTER_DIR.glob("Default*ServiceAdapter.java"))
    
    for filepath in adapter_files:
        content = filepath.read_text()
        if 'ProtocolBridge' not in content:
            continue
        
        changes = transform_adapter(filepath)
        if changes > 0:
            print(f"  {filepath.name}: {changes} bridge calls eliminated")
            total_changes += changes
    
    print(f"\n  Total: {total_changes} ProtocolBridge calls eliminated")


def verify():
    """Verify no ProtocolBridge references remain in adapter files."""
    print("\n=== Verification ===")
    remaining = 0
    adapter_files = sorted(ADAPTER_DIR.glob("Default*ServiceAdapter.java"))
    
    for filepath in adapter_files:
        content = filepath.read_text()
        count = content.count('ProtocolBridge')
        if count > 0:
            print(f"  WARN: {filepath.name} still has {count} ProtocolBridge references")
            remaining += count
    
    if remaining == 0:
        print("  OK: No ProtocolBridge references remain in adapter files")
    else:
        print(f"  {remaining} references still remain")
    
    return remaining


if __name__ == "__main__":
    print(f"Repo root: {REPO_ROOT}")
    print(f"Adapter dir: {ADAPTER_DIR}")
    print(f"Mapper base: {MAPPER_BASE}")
    
    if not ADAPTER_DIR.exists():
        print(f"ERROR: Adapter directory not found: {ADAPTER_DIR}")
        sys.exit(1)
    
    phase1_generate_overloads()
    phase2_transform_adapters()
    remaining = verify()
    
    if remaining > 0:
        print(f"\n⚠ {remaining} ProtocolBridge references need manual attention")
        sys.exit(1)
    else:
        print("\n✓ All ProtocolBridge references eliminated from adapter files")
