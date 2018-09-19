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

import io.zeebe.gateway.api.commands.DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2;
import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.MessageEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ClusterClient {

  private static final String EMPTY_OBJECT = "{}";

  private final ZeebeClient client;

  public ClusterClient(final ZeebeClient client) {
    this.client = client;
  }

  public ActorFuture<Topology> sendHealthRequest() {
    return client.newTopologyRequest().send();
  }

  public ActorFuture<DeploymentEvent> sendDeployWorkflowRequest(
      final DeployWorkflowRequest deployRequest) {

    if (deployRequest.getWorkflowsList().size() == 0) {
      throw new RuntimeException("no workflow to deploy");
    }

    final List<WorkflowRequestObject> workflowsList = deployRequest.getWorkflowsList();
    WorkflowRequestObject cursor = workflowsList.get(0);
    DeployWorkflowCommandBuilderStep2 clusterRequestStep2 =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceBytes(cursor.getDefinition().toByteArray(), cursor.getName());

    for (int i = 1; i < workflowsList.size(); i++) {
      cursor = workflowsList.get(i);
      clusterRequestStep2 =
          clusterRequestStep2.addResourceBytes(
              cursor.getDefinition().toByteArray(), cursor.getName());
    }

    return clusterRequestStep2.send();
  }

  public ActorFuture<MessageEvent> sendPublishMessage(final PublishMessageRequest messageRequest) {
    final String payload = ensureJsonSet(messageRequest.getPayload());

    return client
        .workflowClient()
        .newPublishMessageCommand()
        .messageName(messageRequest.getName())
        .correlationKey(messageRequest.getCorrelationKey())
        .messageId(messageRequest.getMessageId())
        .payload(payload)
        .timeToLive(Duration.ofMillis(messageRequest.getTimeToLive()))
        .send();
  }

  public ActorFuture<JobEvent> sendCreateJob(CreateJobRequest request) {
    final String payload = ensureJsonSet(request.getPayload());
    final String customHeaders = ensureJsonSet(request.getCustomHeaders());

    return client
        .jobClient()
        .newCreateCommand()
        .jobType(request.getJobType())
        .retries(request.getRetries())
        .payload(payload)
        .addCustomHeaders(jsonToMap(customHeaders))
        .send();
  }

  public ActorFuture<WorkflowInstanceEvent> sendCreateWorkflowInstance(
      CreateWorkflowInstanceRequest request) {
    final String payload = ensureJsonSet(request.getPayload());

    if (request.getWorkflowKey() > 0) {
      return client
          .workflowClient()
          .newCreateInstanceCommand()
          .workflowKey(request.getWorkflowKey())
          .payload(payload)
          .send();
    } else {
      return client
          .workflowClient()
          .newCreateInstanceCommand()
          .bpmnProcessId(request.getBpmnProcessId())
          .version(request.getVersion())
          .payload(payload)
          .send();
    }
  }

  private String ensureJsonSet(final String value) {
    if (value == null || value.trim().isEmpty()) {
      return EMPTY_OBJECT;
    } else {
      return value;
    }
  }

  private Map<String, Object> jsonToMap(final String json) {
    return ((ZeebeObjectMapperImpl) client.objectMapper()).fromJsonAsMap(json);
  }
}
