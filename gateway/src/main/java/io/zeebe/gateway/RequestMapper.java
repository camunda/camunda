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

import io.zeebe.gateway.impl.broker.request.BrokerCancelWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerDeployWorkflowRequest;
import io.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.zeebe.gateway.impl.broker.request.BrokerTopologyRequest;
import io.zeebe.gateway.impl.data.MsgPackConverter;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject;
import io.zeebe.msgpack.value.DocumentValue;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RequestMapper {

  public static BrokerTopologyRequest toTopologyRequest(HealthRequest grpcRequest) {
    return new BrokerTopologyRequest();
  }

  public static BrokerDeployWorkflowRequest toDeployWorkflowRequest(
      DeployWorkflowRequest grpcRequest) {
    final BrokerDeployWorkflowRequest brokerRequest = new BrokerDeployWorkflowRequest();

    for (WorkflowRequestObject workflow : grpcRequest.getWorkflowsList()) {
      brokerRequest.addResource(workflow.getDefinition().toByteArray(), workflow.getName());
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
        .setPayload(ensureJsonSet(grpcRequest.getPayload()));

    return brokerRequest;
  }

  public static BrokerCreateJobRequest toCreateJobRequest(CreateJobRequest grpcRequest) {
    final BrokerCreateJobRequest brokerRequest =
        new BrokerCreateJobRequest(grpcRequest.getJobType());

    brokerRequest
        .setRetries(grpcRequest.getRetries())
        .setCustomHeaders(ensureJsonSet(grpcRequest.getCustomHeaders()))
        .setPayload(ensureJsonSet(grpcRequest.getPayload()));

    return brokerRequest;
  }

  public static BrokerCreateWorkflowInstanceRequest toCreateWorkflowInstanceRequest(
      CreateWorkflowInstanceRequest grpcRequest) {
    final BrokerCreateWorkflowInstanceRequest brokerRequest =
        new BrokerCreateWorkflowInstanceRequest();

    brokerRequest
        .setBpmnProcessId(grpcRequest.getBpmnProcessId())
        .setWorkflowKey(grpcRequest.getWorkflowKey())
        .setVersion(grpcRequest.getVersion())
        .setPayload(ensureJsonSet(grpcRequest.getPayload()));

    return brokerRequest;
  }

  public static BrokerCancelWorkflowInstanceRequest toCancelWorkflowInstanceRequest(
      CancelWorkflowInstanceRequest grpcRequest) {
    final BrokerCancelWorkflowInstanceRequest brokerRequest =
        new BrokerCancelWorkflowInstanceRequest();

    brokerRequest.setWorkflowInstanceKey(grpcRequest.getWorkflowInstanceKey());

    return brokerRequest;
  }

  private static DirectBuffer ensureJsonSet(final String value) {
    if (value == null || value.trim().isEmpty()) {
      return DocumentValue.EMPTY_DOCUMENT;
    } else {
      // TODO(menski): is msgpack convert thread safe? maybe one instance per thread local?
      final MsgPackConverter msgPackConverter = new MsgPackConverter();
      return new UnsafeBuffer(msgPackConverter.convertToMsgPack(value));
    }
  }
}
