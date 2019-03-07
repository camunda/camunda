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
package io.zeebe.client.impl.workflow;

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1.CreateWorkflowInstanceCommandStep2;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1.CreateWorkflowInstanceCommandStep3;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.impl.ArgumentUtil;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest.Builder;
import java.io.InputStream;
import java.util.Map;

public class CreateWorkflowInstanceCommandImpl
    implements CreateWorkflowInstanceCommandStep1,
        CreateWorkflowInstanceCommandStep2,
        CreateWorkflowInstanceCommandStep3 {

  private final ZeebeObjectMapper objectMapper;
  private final GatewayStub asyncStub;
  private final Builder builder;

  public CreateWorkflowInstanceCommandImpl(GatewayStub asyncStub, ZeebeObjectMapper objectMapper) {
    this.asyncStub = asyncStub;
    this.objectMapper = objectMapper;

    this.builder = CreateWorkflowInstanceRequest.newBuilder();
  }

  @Override
  public CreateWorkflowInstanceCommandStep3 variables(InputStream variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(objectMapper.validateJson("variables", variables));
  }

  @Override
  public CreateWorkflowInstanceCommandStep3 variables(String variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(objectMapper.validateJson("variables", variables));
  }

  @Override
  public CreateWorkflowInstanceCommandStep3 variables(Map<String, Object> variables) {
    return variables((Object) variables);
  }

  @Override
  public CreateWorkflowInstanceCommandStep3 variables(Object variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(objectMapper.toJson(variables));
  }

  @Override
  public CreateWorkflowInstanceCommandStep2 bpmnProcessId(final String id) {
    builder.setBpmnProcessId(id);
    return this;
  }

  @Override
  public CreateWorkflowInstanceCommandStep3 version(final int version) {
    builder.setVersion(version);
    return this;
  }

  @Override
  public CreateWorkflowInstanceCommandStep3 latestVersion() {
    return version(LATEST_VERSION);
  }

  @Override
  public CreateWorkflowInstanceCommandStep3 workflowKey(final long workflowKey) {
    builder.setWorkflowKey(workflowKey);
    return this;
  }

  @Override
  public ZeebeFuture<WorkflowInstanceEvent> send() {
    final CreateWorkflowInstanceRequest request = builder.build();

    final ZeebeClientFutureImpl<
            WorkflowInstanceEvent, GatewayOuterClass.CreateWorkflowInstanceResponse>
        future = new ZeebeClientFutureImpl<>(CreateWorkflowInstanceResponseImpl::new);

    asyncStub.createWorkflowInstance(request, future);
    return future;
  }

  private CreateWorkflowInstanceCommandStep3 setVariables(String jsonDocument) {
    builder.setVariables(jsonDocument);
    return this;
  }
}
