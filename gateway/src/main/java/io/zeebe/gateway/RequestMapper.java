/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway;

import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCancelWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerDeployWorkflowRequest;
import io.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.zeebe.gateway.impl.broker.request.BrokerUpdateJobRetriesRequest;
import io.zeebe.gateway.impl.data.MsgPackConverter;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject;
import io.zeebe.msgpack.value.DocumentValue;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RequestMapper {

  private static final MsgPackConverter MSG_PACK_CONVERTER = new MsgPackConverter();

  public static BrokerDeployWorkflowRequest toDeployWorkflowRequest(
      DeployWorkflowRequest grpcRequest) {
    final BrokerDeployWorkflowRequest brokerRequest = new BrokerDeployWorkflowRequest();

    for (WorkflowRequestObject workflow : grpcRequest.getWorkflowsList()) {
      brokerRequest.addResource(
          workflow.getDefinition().toByteArray(), workflow.getName(), workflow.getType());
    }

    return brokerRequest;
  }

  public static BrokerPublishMessageRequest toPublishMessageRequest(
      PublishMessageRequest grpcRequest) {
    final BrokerPublishMessageRequest brokerRequest =
        new BrokerPublishMessageRequest(grpcRequest.getName(), grpcRequest.getCorrelationKey());

    brokerRequest
        .setMessageId(grpcRequest.getMessageId())
        .setTimeToLive(grpcRequest.getTimeToLive())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()));

    return brokerRequest;
  }

  public static BrokerUpdateJobRetriesRequest toUpdateJobRetriesRequest(
      UpdateJobRetriesRequest grpcRequest) {
    return new BrokerUpdateJobRetriesRequest(grpcRequest.getJobKey(), grpcRequest.getRetries());
  }

  public static BrokerFailJobRequest toFailJobRequest(FailJobRequest grpcRequest) {
    return new BrokerFailJobRequest(grpcRequest.getJobKey(), grpcRequest.getRetries())
        .setErrorMessage(grpcRequest.getErrorMessage());
  }

  public static BrokerCompleteJobRequest toCompleteJobRequest(CompleteJobRequest grpcRequest) {
    return new BrokerCompleteJobRequest(
        grpcRequest.getJobKey(), ensureJsonSet(grpcRequest.getVariables()));
  }

  public static BrokerCreateWorkflowInstanceRequest toCreateWorkflowInstanceRequest(
      CreateWorkflowInstanceRequest grpcRequest) {
    final BrokerCreateWorkflowInstanceRequest brokerRequest =
        new BrokerCreateWorkflowInstanceRequest();

    brokerRequest
        .setBpmnProcessId(grpcRequest.getBpmnProcessId())
        .setKey(grpcRequest.getWorkflowKey())
        .setVersion(grpcRequest.getVersion())
        .setVariables(ensureJsonSet(grpcRequest.getVariables()));

    return brokerRequest;
  }

  public static BrokerCancelWorkflowInstanceRequest toCancelWorkflowInstanceRequest(
      CancelWorkflowInstanceRequest grpcRequest) {
    final BrokerCancelWorkflowInstanceRequest brokerRequest =
        new BrokerCancelWorkflowInstanceRequest();

    brokerRequest.setWorkflowInstanceKey(grpcRequest.getWorkflowInstanceKey());

    return brokerRequest;
  }

  public static BrokerSetVariablesRequest toSetVariablesRequest(SetVariablesRequest grpcRequest) {
    final BrokerSetVariablesRequest brokerRequest = new BrokerSetVariablesRequest();

    brokerRequest.setElementInstanceKey(grpcRequest.getElementInstanceKey());
    brokerRequest.setDocument(ensureJsonSet(grpcRequest.getVariables()));
    brokerRequest.setLocal(grpcRequest.getLocal());

    return brokerRequest;
  }

  public static BrokerActivateJobsRequest toActivateJobsRequest(ActivateJobsRequest grpcRequest) {
    return new BrokerActivateJobsRequest(grpcRequest.getType())
        .setTimeout(grpcRequest.getTimeout())
        .setWorker(grpcRequest.getWorker())
        .setMaxJobsToActivate(grpcRequest.getMaxJobsToActivate())
        .setVariables(grpcRequest.getFetchVariableList());
  }

  public static BrokerResolveIncidentRequest toResolveIncidentRequest(
      ResolveIncidentRequest grpcRequest) {
    return new BrokerResolveIncidentRequest(grpcRequest.getIncidentKey());
  }

  private static DirectBuffer ensureJsonSet(final String value) {
    if (value == null || value.trim().isEmpty()) {
      return DocumentValue.EMPTY_DOCUMENT;
    } else {
      return new UnsafeBuffer(MSG_PACK_CONVERTER.convertToMsgPack(value));
    }
  }
}
