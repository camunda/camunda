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
package io.zeebe.client.impl.command;

import io.grpc.stub.StreamObserver;
import io.zeebe.client.api.JsonMapper;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2;
import io.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.response.ProcessInstanceEvent;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.client.impl.response.CreateProcessInstanceResponseImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest.Builder;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class CreateProcessInstanceCommandImpl
    implements CreateProcessInstanceCommandStep1,
        CreateProcessInstanceCommandStep2,
        CreateProcessInstanceCommandStep3 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;
  private final JsonMapper jsonMapper;
  private Duration requestTimeout;

  public CreateProcessInstanceCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final Duration requestTimeout,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    this.jsonMapper = jsonMapper;
    builder = CreateProcessInstanceRequest.newBuilder();
  }

  @Override
  public CreateProcessInstanceCommandStep3 variables(final InputStream variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.validateJson("variables", variables));
  }

  @Override
  public CreateProcessInstanceCommandStep3 variables(final String variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.validateJson("variables", variables));
  }

  @Override
  public CreateProcessInstanceCommandStep3 variables(final Map<String, Object> variables) {
    return variables((Object) variables);
  }

  @Override
  public CreateProcessInstanceCommandStep3 variables(final Object variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.toJson(variables));
  }

  @Override
  public CreateProcessInstanceWithResultCommandStep1 withResult() {
    return new CreateProcessInstanceWithResultCommandImpl(
        jsonMapper, asyncStub, builder, retryPredicate, requestTimeout);
  }

  @Override
  public CreateProcessInstanceCommandStep2 bpmnProcessId(final String id) {
    builder.setBpmnProcessId(id);
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 processDefinitionKey(final long processDefinitionKey) {
    builder.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 version(final int version) {
    builder.setVersion(version);
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 latestVersion() {
    return version(LATEST_VERSION);
  }

  @Override
  public FinalCommandStep<ProcessInstanceEvent> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<ProcessInstanceEvent> send() {
    final CreateProcessInstanceRequest request = builder.build();

    final RetriableClientFutureImpl<
            ProcessInstanceEvent, GatewayOuterClass.CreateProcessInstanceResponse>
        future =
            new RetriableClientFutureImpl<>(
                CreateProcessInstanceResponseImpl::new,
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final CreateProcessInstanceRequest request,
      final StreamObserver<GatewayOuterClass.CreateProcessInstanceResponse> future) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .createProcessInstance(request, future);
  }

  private CreateProcessInstanceCommandStep3 setVariables(final String jsonDocument) {
    builder.setVariables(jsonDocument);
    return this;
  }
}
