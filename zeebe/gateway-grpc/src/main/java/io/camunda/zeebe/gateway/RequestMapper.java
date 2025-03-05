/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.gateway.cmd.IllegalTenantRequestException;
import io.camunda.zeebe.gateway.cmd.InvalidTenantRequestException;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerBroadcastSignalRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerEvaluateDecisionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerThrowErrorRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateJobRetriesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateJobTimeoutRequest;
import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessRequestObject;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Resource;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.grpc.Context;
import java.util.List;
import java.util.regex.Pattern;
import org.agrona.DirectBuffer;
import org.apache.commons.lang3.StringUtils;

public final class RequestMapper extends RequestUtil {

  private static final Pattern TENANT_ID_MASK = Pattern.compile("^[\\w\\.-]{1,31}$");
  private static boolean isMultiTenancyEnabled = false;

  /**
   * Sets whether multi-tenancy is enabled or not. This typically does not change at runtime. We
   * need it during mapping, but it's hard to pass along in the mapper functions.
   *
   * <p>Expected to only be called by the constructor of {@link EndpointManager}, and from tests.
   *
   * @param isEnabled true when multi-tenancy is enabled, otherwise false
   */
  public static void setMultiTenancyEnabled(final boolean isEnabled) {
    isMultiTenancyEnabled = isEnabled;
  }

  public static BrokerDeployResourceRequest toDeployProcessRequest(
      final DeployProcessRequest grpcRequest) {
    ensureTenantIdSet("DeployProcess", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    final BrokerDeployResourceRequest brokerRequest = new BrokerDeployResourceRequest();

    for (final ProcessRequestObject process : grpcRequest.getProcessesList()) {
      brokerRequest.addResource(process.getName(), process.getDefinition().toByteArray());
    }
    brokerRequest.setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    return brokerRequest;
  }

  public static BrokerDeployResourceRequest toDeployResourceRequest(
      final DeployResourceRequest grpcRequest) {
    final BrokerDeployResourceRequest brokerRequest = new BrokerDeployResourceRequest();

    final String tenantId = grpcRequest.getTenantId();
    brokerRequest.setTenantId(ensureTenantIdSet("DeployResource", tenantId));

    for (final Resource resource : grpcRequest.getResourcesList()) {
      brokerRequest.addResource(resource.getName(), resource.getContent().toByteArray());
    }

    return brokerRequest;
  }

  public static BrokerPublishMessageRequest toPublishMessageRequest(
      final PublishMessageRequest grpcRequest) {
    final BrokerPublishMessageRequest brokerRequest =
        new BrokerPublishMessageRequest(grpcRequest.getName(), grpcRequest.getCorrelationKey());

    brokerRequest
        .setMessageId(grpcRequest.getMessageId())
        .setTimeToLive(grpcRequest.getTimeToLive())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()))
        .setTenantId(ensureTenantIdSet("PublishMessage", grpcRequest.getTenantId()));

    return brokerRequest;
  }

  public static BrokerUpdateJobRetriesRequest toUpdateJobRetriesRequest(
      final UpdateJobRetriesRequest grpcRequest) {
    final var brokerRequest =
        new BrokerUpdateJobRetriesRequest(grpcRequest.getJobKey(), grpcRequest.getRetries());
    if (grpcRequest.hasOperationReference()) {
      brokerRequest.setOperationReference(grpcRequest.getOperationReference());
    }
    return brokerRequest;
  }

  public static BrokerUpdateJobTimeoutRequest toUpdateJobTimeoutRequest(
      final UpdateJobTimeoutRequest grpcRequest) {
    final var brokerRequest =
        new BrokerUpdateJobTimeoutRequest(grpcRequest.getJobKey(), grpcRequest.getTimeout());
    if (grpcRequest.hasOperationReference()) {
      brokerRequest.setOperationReference(grpcRequest.getOperationReference());
    }
    return brokerRequest;
  }

  public static BrokerFailJobRequest toFailJobRequest(final FailJobRequest grpcRequest) {
    return new BrokerFailJobRequest(
            grpcRequest.getJobKey(), grpcRequest.getRetries(), grpcRequest.getRetryBackOff())
        .setErrorMessage(grpcRequest.getErrorMessage())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()));
  }

  public static BrokerThrowErrorRequest toThrowErrorRequest(final ThrowErrorRequest grpcRequest) {
    return new BrokerThrowErrorRequest(grpcRequest.getJobKey(), grpcRequest.getErrorCode())
        .setErrorMessage(grpcRequest.getErrorMessage())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()));
  }

  public static BrokerCompleteJobRequest toCompleteJobRequest(
      final CompleteJobRequest grpcRequest) {
    return new BrokerCompleteJobRequest(
        grpcRequest.getJobKey(), ensureJsonSet(grpcRequest.getVariables()));
  }

  public static BrokerCreateProcessInstanceRequest toCreateProcessInstanceRequest(
      final CreateProcessInstanceRequest grpcRequest) {
    final var brokerRequest = new BrokerCreateProcessInstanceRequest();

    brokerRequest
        .setBpmnProcessId(grpcRequest.getBpmnProcessId())
        .setKey(grpcRequest.getProcessDefinitionKey())
        .setVersion(grpcRequest.getVersion())
        .setTenantId(ensureTenantIdSet("CreateProcessInstance", grpcRequest.getTenantId()))
        .setVariables(ensureJsonSet(grpcRequest.getVariables()))
        .setStartInstructions(grpcRequest.getStartInstructionsList());

    if (grpcRequest.hasOperationReference()) {
      brokerRequest.setOperationReference(grpcRequest.getOperationReference());
    }

    return brokerRequest;
  }

  public static BrokerCreateProcessInstanceWithResultRequest
      toCreateProcessInstanceWithResultRequest(
          final CreateProcessInstanceWithResultRequest grpcRequest) {
    final var brokerRequest = new BrokerCreateProcessInstanceWithResultRequest();

    final var request = grpcRequest.getRequest();
    brokerRequest
        .setBpmnProcessId(request.getBpmnProcessId())
        .setKey(request.getProcessDefinitionKey())
        .setVersion(request.getVersion())
        .setTenantId(ensureTenantIdSet("CreateProcessInstanceWithResult", request.getTenantId()))
        .setVariables(ensureJsonSet(request.getVariables()))
        .setStartInstructions(request.getStartInstructionsList())
        .setFetchVariables(grpcRequest.getFetchVariablesList());

    if (request.hasOperationReference()) {
      brokerRequest.setOperationReference(request.getOperationReference());
    }
    return brokerRequest;
  }

  public static BrokerEvaluateDecisionRequest toEvaluateDecisionRequest(
      final EvaluateDecisionRequest grpcRequest) {
    final BrokerEvaluateDecisionRequest brokerRequest = new BrokerEvaluateDecisionRequest();

    brokerRequest
        .setDecisionId(grpcRequest.getDecisionId())
        .setDecisionKey(grpcRequest.getDecisionKey())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()))
        .setTenantId(ensureTenantIdSet("EvaluateDecision", grpcRequest.getTenantId()));

    return brokerRequest;
  }

  public static BrokerCancelProcessInstanceRequest toCancelProcessInstanceRequest(
      final CancelProcessInstanceRequest grpcRequest) {
    final var brokerRequest = new BrokerCancelProcessInstanceRequest();
    brokerRequest.setProcessInstanceKey(grpcRequest.getProcessInstanceKey());

    if (grpcRequest.hasOperationReference()) {
      brokerRequest.setOperationReference(grpcRequest.getOperationReference());
    }

    return brokerRequest;
  }

  public static BrokerSetVariablesRequest toSetVariablesRequest(
      final SetVariablesRequest grpcRequest) {
    final var brokerRequest = new BrokerSetVariablesRequest();

    brokerRequest.setElementInstanceKey(grpcRequest.getElementInstanceKey());
    brokerRequest.setVariables(ensureJsonSet(grpcRequest.getVariables()));
    brokerRequest.setLocal(grpcRequest.getLocal());

    if (grpcRequest.hasOperationReference()) {
      brokerRequest.setOperationReference(grpcRequest.getOperationReference());
    }

    return brokerRequest;
  }

  public static BrokerActivateJobsRequest toActivateJobsRequest(
      final ActivateJobsRequest grpcRequest) {

    List<String> tenantIds = grpcRequest.getTenantIdsList();
    tenantIds = ensureTenantIdsSet("ActivateJobs", tenantIds);

    return new BrokerActivateJobsRequest(grpcRequest.getType())
        .setTimeout(grpcRequest.getTimeout())
        .setWorker(grpcRequest.getWorker())
        .setMaxJobsToActivate(grpcRequest.getMaxJobsToActivate())
        .setVariables(grpcRequest.getFetchVariableList())
        .setTenantIds(tenantIds);
  }

  public static BrokerResolveIncidentRequest toResolveIncidentRequest(
      final ResolveIncidentRequest grpcRequest) {
    final var brokerRequest = new BrokerResolveIncidentRequest(grpcRequest.getIncidentKey());

    if (grpcRequest.hasOperationReference()) {
      brokerRequest.setOperationReference(grpcRequest.getOperationReference());
    }

    return brokerRequest;
  }

  public static BrokerModifyProcessInstanceRequest toModifyProcessInstanceRequest(
      final ModifyProcessInstanceRequest grpcRequest) {
    final var brokerRequest =
        new BrokerModifyProcessInstanceRequest()
            .setProcessInstanceKey(grpcRequest.getProcessInstanceKey())
            .addActivateInstructions(grpcRequest.getActivateInstructionsList())
            .addTerminateInstructions(grpcRequest.getTerminateInstructionsList());

    if (grpcRequest.hasOperationReference()) {
      brokerRequest.setOperationReference(grpcRequest.getOperationReference());
    }
    return brokerRequest;
  }

  public static BrokerMigrateProcessInstanceRequest toMigrateProcessInstanceRequest(
      final MigrateProcessInstanceRequest grpcRequest) {
    final var brokerRequest =
        new BrokerMigrateProcessInstanceRequest()
            .setProcessInstanceKey(grpcRequest.getProcessInstanceKey())
            .setTargetProcessDefinitionKey(
                grpcRequest.getMigrationPlan().getTargetProcessDefinitionKey())
            .addMappingInstructions(grpcRequest.getMigrationPlan().getMappingInstructionsList());

    if (grpcRequest.hasOperationReference()) {
      brokerRequest.setOperationReference(grpcRequest.getOperationReference());
    }
    return brokerRequest;
  }

  public static BrokerDeleteResourceRequest toDeleteResourceRequest(
      final DeleteResourceRequest grpcRequest) {
    final var brokerRequest =
        new BrokerDeleteResourceRequest().setResourceKey(grpcRequest.getResourceKey());

    if (grpcRequest.hasOperationReference()) {
      brokerRequest.setOperationReference(grpcRequest.getOperationReference());
    }
    return brokerRequest;
  }

  public static BrokerBroadcastSignalRequest toBroadcastSignalRequest(
      final BroadcastSignalRequest grpcRequest) {
    return new BrokerBroadcastSignalRequest(grpcRequest.getSignalName())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()))
        .setTenantId(ensureTenantIdSet("BroadcastSignal", grpcRequest.getTenantId()));
  }

  public static JobActivationProperties toJobActivationProperties(
      final StreamActivatedJobsRequest request) {

    List<String> tenantIds = request.getTenantIdsList();
    tenantIds = ensureTenantIdsSet("StreamActivatedJobs", tenantIds);

    final JobActivationPropertiesImpl jobActivationProperties = new JobActivationPropertiesImpl();
    final DirectBuffer worker = wrapString(request.getWorker());
    jobActivationProperties
        .setWorker(worker, 0, worker.capacity())
        .setTimeout(request.getTimeout())
        .setFetchVariables(request.getFetchVariableList().stream().map(StringValue::new).toList())
        .setTenantIds(tenantIds);

    return jobActivationProperties;
  }

  public static String ensureTenantIdSet(final String commandName, final String tenantId) {

    final boolean hasTenantId = !StringUtils.isBlank(tenantId);
    if (!isMultiTenancyEnabled) {
      if (hasTenantId && !TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)) {
        throw new InvalidTenantRequestException(commandName, tenantId, "multi-tenancy is disabled");
      }

      return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    }

    if (!hasTenantId) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "no tenant identifier was provided.");
    }

    if (tenantId.length() > 31) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant identifier is longer than 31 characters");
    }

    if (!TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)
        && !TENANT_ID_MASK.matcher(tenantId).matches()) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant identifier contains illegal characters");
    }

    final List<String> authorizedTenants;
    try {
      authorizedTenants = Context.current().call(InterceptorUtil.getAuthorizedTenantsKey()::get);
    } catch (final Exception e) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant could not be retrieved from the request context", e);
    }
    if (authorizedTenants == null) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant could not be retrieved from the request context");
    }
    if (!authorizedTenants.contains(tenantId)) {
      throw new IllegalTenantRequestException(
          commandName, tenantId, "tenant is not authorized to perform this request");
    }

    return tenantId;
  }

  public static List<String> ensureTenantIdsSet(
      final String commandName, final List<String> tenantIds) {

    if (tenantIds.isEmpty()) {
      if (!isMultiTenancyEnabled) {
        return List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      }

      throw new InvalidTenantRequestException(
          commandName, tenantIds, "no tenant identifiers were provided.");
    }

    tenantIds.stream().forEach(tenantId -> ensureTenantIdSet(commandName, tenantId));

    return tenantIds;
  }
}
