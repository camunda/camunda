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

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.command.SetVariablesCommandStep1;
import io.zeebe.client.api.command.SetVariablesCommandStep1.SetVariablesCommandStep2;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SetVariablesCommandImpl implements SetVariablesCommandStep1, SetVariablesCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final ZeebeObjectMapper objectMapper;
  private final long elementInstanceKey;
  private Duration requestTimeout;

  public SetVariablesCommandImpl(
      GatewayStub asyncStub,
      ZeebeObjectMapper objectMapper,
      long elementInstanceKey,
      Duration requestTimeout) {
    this.asyncStub = asyncStub;
    this.objectMapper = objectMapper;
    this.elementInstanceKey = elementInstanceKey;
    this.requestTimeout = requestTimeout;
    this.builder = SetVariablesRequest.newBuilder();
    builder.setElementInstanceKey(elementInstanceKey);
  }

  @Override
  public FinalCommandStep<Void> requestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<Void> send() {
    final SetVariablesRequest request = builder.build();

    final ZeebeClientFutureImpl<Void, SetVariablesResponse> future = new ZeebeClientFutureImpl<>();

    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .setVariables(request, future);
    return future;
  }

  @Override
  public SetVariablesCommandStep2 local(boolean local) {
    builder.setLocal(local);
    return this;
  }

  @Override
  public SetVariablesCommandStep2 variables(InputStream variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(objectMapper.validateJson("variables", variables));
  }

  @Override
  public SetVariablesCommandStep2 variables(String variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(objectMapper.validateJson("variables", variables));
  }

  @Override
  public SetVariablesCommandStep2 variables(Map<String, Object> variables) {
    return variables((Object) variables);
  }

  @Override
  public SetVariablesCommandStep2 variables(Object variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(objectMapper.toJson(variables));
  }

  private SetVariablesCommandStep2 setVariables(String jsonDocument) {
    builder.setVariables(jsonDocument);
    return this;
  }
}
