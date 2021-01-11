/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway;

import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCancelWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceWithResultRequest;
import io.zeebe.gateway.impl.broker.request.BrokerDeployWorkflowRequest;
import io.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.zeebe.gateway.impl.broker.request.BrokerThrowErrorRequest;
import io.zeebe.gateway.impl.broker.request.BrokerUpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class RequestMapper {

  public static BrokerDeployWorkflowRequest toDeployWorkflowRequest(
      final DeployWorkflowRequest grpcRequest) {
    final BrokerDeployWorkflowRequest brokerRequest = new BrokerDeployWorkflowRequest();

    for (final WorkflowRequestObject workflow : grpcRequest.getWorkflowsList()) {
      brokerRequest.addResource(workflow.getDefinition().toByteArray(), workflow.getName());
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

  public static BrokerCreateWorkflowInstanceRequest toCreateWorkflowInstanceRequest(
      final CreateWorkflowInstanceRequest grpcRequest) {
    final BrokerCreateWorkflowInstanceRequest brokerRequest =
        new BrokerCreateWorkflowInstanceRequest();

    brokerRequest
        .setBpmnProcessId(grpcRequest.getBpmnProcessId())
        .setKey(grpcRequest.getWorkflowKey())
        .setVersion(grpcRequest.getVersion())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()));

    return brokerRequest;
  }

  public static BrokerCreateWorkflowInstanceWithResultRequest
      toCreateWorkflowInstanceWithResultRequest(
          final CreateWorkflowInstanceWithResultRequest grpcRequest) {
    final BrokerCreateWorkflowInstanceWithResultRequest brokerRequest =
        new BrokerCreateWorkflowInstanceWithResultRequest();

    final CreateWorkflowInstanceRequest request = grpcRequest.getRequest();
    brokerRequest
        .setBpmnProcessId(request.getBpmnProcessId())
        .setKey(request.getWorkflowKey())
        .setVersion(request.getVersion())
        .setVariables(ensureJsonSet(request.getVariables()))
        .setFetchVariables(grpcRequest.getFetchVariablesList());

    return brokerRequest;
  }

  public static BrokerCancelWorkflowInstanceRequest toCancelWorkflowInstanceRequest(
      final CancelWorkflowInstanceRequest grpcRequest) {
    final BrokerCancelWorkflowInstanceRequest brokerRequest =
        new BrokerCancelWorkflowInstanceRequest();

    brokerRequest.setWorkflowInstanceKey(grpcRequest.getWorkflowInstanceKey());

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
