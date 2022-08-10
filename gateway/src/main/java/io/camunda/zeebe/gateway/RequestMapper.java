/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import static org.agrona.LangUtil.rethrowUnchecked;

import com.fasterxml.jackson.core.JsonParseException;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerThrowErrorRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateJobRetriesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessRequestObject;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Resource;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class RequestMapper {

  public static BrokerDeployResourceRequest toDeployProcessRequest(
      final DeployProcessRequest grpcRequest) {
    final BrokerDeployResourceRequest brokerRequest = new BrokerDeployResourceRequest();

    for (final ProcessRequestObject process : grpcRequest.getProcessesList()) {
      brokerRequest.addResource(process.getDefinition().toByteArray(), process.getName());
    }

    return brokerRequest;
  }

  public static BrokerDeployResourceRequest toDeployResourceRequest(
      final DeployResourceRequest grpcRequest) {
    final BrokerDeployResourceRequest brokerRequest = new BrokerDeployResourceRequest();

    for (final Resource resource : grpcRequest.getResourcesList()) {
      brokerRequest.addResource(resource.getContent().toByteArray(), resource.getName());
    }

    return brokerRequest;
  }

  public static BrokerPublishMessageRequest toPublishMessageRequest(
      final PublishMessageRequest grpcRequest) {
    final BrokerPublishMessageRequest brokerRequest =
        new BrokerPublishMessageRequest(grpcRequest.getName(), grpcRequest.getCorrelationKey(), grpcRequest.getTenantId());

    brokerRequest
        .setMessageId(grpcRequest.getMessageId())
        .setTimeToLive(grpcRequest.getTimeToLive())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()));

    return brokerRequest;
  }

  public static BrokerUpdateJobRetriesRequest toUpdateJobRetriesRequest(
      final UpdateJobRetriesRequest grpcRequest) {
    return new BrokerUpdateJobRetriesRequest(grpcRequest.getJobKey(),
        grpcRequest.getRetries(),
        grpcRequest.getTenantId());
  }

  public static BrokerFailJobRequest toFailJobRequest(final FailJobRequest grpcRequest) {
    return new BrokerFailJobRequest(
            grpcRequest.getJobKey(), grpcRequest.getRetries(), grpcRequest.getRetryBackOff(), grpcRequest.getTenantId())
        .setErrorMessage(grpcRequest.getErrorMessage());
  }

  public static BrokerThrowErrorRequest toThrowErrorRequest(final ThrowErrorRequest grpcRequest) {
    return new BrokerThrowErrorRequest(grpcRequest.getJobKey(), grpcRequest.getErrorCode(), grpcRequest.getTenantId())
        .setErrorMessage(grpcRequest.getErrorMessage());
  }

  public static BrokerCompleteJobRequest toCompleteJobRequest(
      final CompleteJobRequest grpcRequest) {
    return new BrokerCompleteJobRequest(
        grpcRequest.getJobKey(),
        ensureJsonSet(grpcRequest.getVariables()),
        grpcRequest.getTenantId());
  }

  public static BrokerCreateProcessInstanceRequest toCreateProcessInstanceRequest(
      final CreateProcessInstanceRequest grpcRequest) {
    final BrokerCreateProcessInstanceRequest brokerRequest =
        new BrokerCreateProcessInstanceRequest();

    brokerRequest
        .setBpmnProcessId(grpcRequest.getBpmnProcessId())
        .setKey(grpcRequest.getProcessDefinitionKey())
        .setVersion(grpcRequest.getVersion())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()))
        .setStartInstructions(grpcRequest.getStartInstructionsList());

    return brokerRequest;
  }

  public static BrokerCreateProcessInstanceWithResultRequest
      toCreateProcessInstanceWithResultRequest(
          final CreateProcessInstanceWithResultRequest grpcRequest) {
    final BrokerCreateProcessInstanceWithResultRequest brokerRequest =
        new BrokerCreateProcessInstanceWithResultRequest();

    final CreateProcessInstanceRequest request = grpcRequest.getRequest();
    brokerRequest
        .setBpmnProcessId(request.getBpmnProcessId())
        .setKey(request.getProcessDefinitionKey())
        .setVersion(request.getVersion())
        .setVariables(ensureJsonSet(request.getVariables()))
        .setStartInstructions(request.getStartInstructionsList())
        .setFetchVariables(grpcRequest.getFetchVariablesList());

    return brokerRequest;
  }

  public static BrokerCancelProcessInstanceRequest toCancelProcessInstanceRequest(
      final CancelProcessInstanceRequest grpcRequest) {
    final BrokerCancelProcessInstanceRequest brokerRequest =
        new BrokerCancelProcessInstanceRequest();

    brokerRequest.setProcessInstanceKey(grpcRequest.getProcessInstanceKey());

    return brokerRequest;
  }

  public static BrokerSetVariablesRequest toSetVariablesRequest(
      final SetVariablesRequest grpcRequest) {
    final BrokerSetVariablesRequest brokerRequest = new BrokerSetVariablesRequest();

    brokerRequest.setElementInstanceKey(grpcRequest.getElementInstanceKey());
    brokerRequest.setVariables(ensureJsonSet(grpcRequest.getVariables()));
    brokerRequest.setLocal(grpcRequest.getLocal());

    return brokerRequest;
  }

  public static BrokerActivateJobsRequest toActivateJobsRequest(
      final ActivateJobsRequest grpcRequest) {
    return new BrokerActivateJobsRequest(grpcRequest.getType(), grpcRequest.getTenantId())
        .setTimeout(grpcRequest.getTimeout())
        .setWorker(grpcRequest.getWorker())
        .setMaxJobsToActivate(grpcRequest.getMaxJobsToActivate())
        .setVariables(grpcRequest.getFetchVariableList());
  }

  public static BrokerResolveIncidentRequest toResolveIncidentRequest(
      final ResolveIncidentRequest grpcRequest) {
    return new BrokerResolveIncidentRequest(grpcRequest.getIncidentKey(), grpcRequest.getTenantId());
  }

  public static BrokerModifyProcessInstanceRequest toModifyProcessInstanceRequest(
      final ModifyProcessInstanceRequest grpcRequest) {
    return new BrokerModifyProcessInstanceRequest()
        .setProcessInstanceKey(grpcRequest.getProcessInstanceKey())
        .addActivateInstructions(grpcRequest.getActivateInstructionsList())
        .addTerminateInstructions(grpcRequest.getTerminateInstructionsList());
  }

  public static DirectBuffer ensureJsonSet(final String value) {
    if (value == null || value.trim().isEmpty()) {
      return DocumentValue.EMPTY_DOCUMENT;
    } else {
      try {
        return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
      } catch (final RuntimeException e) {
        final var cause = e.getCause();
        if (cause instanceof JsonParseException) {
          final var parseException = (JsonParseException) cause;

          final var descriptiveException =
              new JsonParseException(
                  parseException.getProcessor(),
                  "Invalid JSON value: " + value,
                  parseException.getLocation(),
                  cause);

          rethrowUnchecked(descriptiveException);
          return DocumentValue.EMPTY_DOCUMENT; // bogus return statement
        } else {
          throw e;
        }
      }
    }
  }
}
