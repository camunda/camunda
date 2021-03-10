/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway;

import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.zeebe.gateway.impl.broker.request.BrokerDeployProcessRequest;
import io.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.zeebe.gateway.impl.broker.request.BrokerThrowErrorRequest;
import io.zeebe.gateway.impl.broker.request.BrokerUpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ProcessRequestObject;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class RequestMapper {

  public static BrokerDeployProcessRequest toDeployProcessRequest(
      final DeployProcessRequest grpcRequest) {
    final BrokerDeployProcessRequest brokerRequest = new BrokerDeployProcessRequest();

    for (final ProcessRequestObject process : grpcRequest.getProcessesList()) {
      brokerRequest.addResource(process.getDefinition().toByteArray(), process.getName());
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
        .setVariables(ensureJsonSet(grpcRequest.getVariables()));

    return brokerRequest;
  }

  public static BrokerUpdateJobRetriesRequest toUpdateJobRetriesRequest(
      final UpdateJobRetriesRequest grpcRequest) {
    return new BrokerUpdateJobRetriesRequest(grpcRequest.getJobKey(), grpcRequest.getRetries());
  }

  public static BrokerFailJobRequest toFailJobRequest(final FailJobRequest grpcRequest) {
    return new BrokerFailJobRequest(grpcRequest.getJobKey(), grpcRequest.getRetries())
        .setErrorMessage(grpcRequest.getErrorMessage());
  }

  public static BrokerThrowErrorRequest toThrowErrorRequest(final ThrowErrorRequest grpcRequest) {
    return new BrokerThrowErrorRequest(grpcRequest.getJobKey(), grpcRequest.getErrorCode())
        .setErrorMessage(grpcRequest.getErrorMessage());
  }

  public static BrokerCompleteJobRequest toCompleteJobRequest(
      final CompleteJobRequest grpcRequest) {
    return new BrokerCompleteJobRequest(
        grpcRequest.getJobKey(), ensureJsonSet(grpcRequest.getVariables()));
  }

  public static BrokerCreateProcessInstanceRequest toCreateProcessInstanceRequest(
      final CreateProcessInstanceRequest grpcRequest) {
    final BrokerCreateProcessInstanceRequest brokerRequest =
        new BrokerCreateProcessInstanceRequest();

    brokerRequest
        .setBpmnProcessId(grpcRequest.getBpmnProcessId())
        .setKey(grpcRequest.getProcessDefinitionKey())
        .setVersion(grpcRequest.getVersion())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()));

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
    return new BrokerActivateJobsRequest(grpcRequest.getType())
        .setTimeout(grpcRequest.getTimeout())
        .setWorker(grpcRequest.getWorker())
        .setMaxJobsToActivate(grpcRequest.getMaxJobsToActivate())
        .setVariables(grpcRequest.getFetchVariableList());
  }

  public static BrokerResolveIncidentRequest toResolveIncidentRequest(
      final ResolveIncidentRequest grpcRequest) {
    return new BrokerResolveIncidentRequest(grpcRequest.getIncidentKey());
  }

  private static DirectBuffer ensureJsonSet(final String value) {
    if (value == null || value.trim().isEmpty()) {
      return DocumentValue.EMPTY_DOCUMENT;
    } else {
      return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
    }
  }
}
