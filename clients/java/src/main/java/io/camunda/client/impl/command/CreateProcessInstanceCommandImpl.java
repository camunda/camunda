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
package io.camunda.client.impl.command;

import static io.camunda.client.api.command.enums.ProcessInstanceCreationInstruction.TERMINATE_PROCESS_INSTANCE;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateProcessInstanceResponseImpl;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.impl.util.TagUtil;
import io.camunda.client.protocol.rest.CreateProcessInstanceResult;
import io.camunda.client.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceCreationTerminateInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest.Builder;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TerminateProcessInstanceInstruction;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CreateProcessInstanceCommandImpl
    extends CommandWithVariables<CreateProcessInstanceCommandImpl>
    implements CreateProcessInstanceCommandStep1,
        CreateProcessInstanceCommandStep2,
        CreateProcessInstanceCommandStep3 {

  private final GatewayStub asyncStub;
  private final Builder grpcRequestObjectBuilder;
  private final Predicate<StatusCode> retryPredicate;
  private final JsonMapper jsonMapper;
  private Duration requestTimeout;
  private boolean useRest;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final ProcessInstanceCreationInstruction httpRequestObject =
      new ProcessInstanceCreationInstruction();
  private final CamundaClientConfiguration config;

  public CreateProcessInstanceCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final CamundaClientConfiguration config,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final boolean preferRestOverGrpc) {
    super(jsonMapper);
    this.config = config;
    this.asyncStub = asyncStub;
    requestTimeout = config.getDefaultRequestTimeout();
    this.retryPredicate = retryPredicate;
    this.jsonMapper = jsonMapper;
    grpcRequestObjectBuilder = CreateProcessInstanceRequest.newBuilder();
    tenantId(config.getDefaultTenantId());
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    requestTimeout(requestTimeout);
    useRest = preferRestOverGrpc;
  }

  @Override
  protected CreateProcessInstanceCommandImpl setVariablesInternal(final String variables) {
    grpcRequestObjectBuilder.setVariables(variables);
    // This check is mandatory. Without it, gRPC requests can fail unnecessarily.
    // gRPC and REST handle setting variables differently:
    // - For gRPC commands, we only check if the JSON is valid and forward it to the engine.
    //    The engine checks if the provided String can be transformed into a Map, if not it
    //    throws an error.
    // - For REST commands, users have to provide a valid JSON Object String.
    //    Otherwise, the client throws an exception already.
    if (useRest) {
      httpRequestObject.setVariables(jsonMapper.fromJsonAsMap(variables));
    }
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 startBeforeElement(final String elementId) {
    grpcRequestObjectBuilder.addStartInstructions(
        ProcessInstanceCreationStartInstruction.newBuilder().setElementId(elementId).build());
    httpRequestObject.addStartInstructionsItem(
        new io.camunda.client.protocol.rest.ProcessInstanceCreationStartInstruction()
            .elementId(elementId));
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 terminateAfterElement(final String elementId) {
    grpcRequestObjectBuilder.addRuntimeInstructions(
        GatewayOuterClass.ProcessInstanceCreationRuntimeInstruction.newBuilder()
            .setTerminate(
                TerminateProcessInstanceInstruction.newBuilder().setAfterElementId(elementId)));
    httpRequestObject.addRuntimeInstructionsItem(
        new ProcessInstanceCreationTerminateInstruction()
            .afterElementId(elementId)
            .type(TERMINATE_PROCESS_INSTANCE.name()));
    return this;
  }

  @Override
  public CreateProcessInstanceWithResultCommandStep1 withResult() {
    return new CreateProcessInstanceWithResultCommandImpl(
        jsonMapper,
        asyncStub,
        grpcRequestObjectBuilder,
        retryPredicate,
        requestTimeout,
        httpClient,
        useRest,
        httpRequestObject,
        config);
  }

  @Override
  public CreateProcessInstanceCommandStep3 tags(final String... tags) {
    final Set<String> uniqueTags = new HashSet<>(Arrays.asList(tags)); // ensure no duplicates

    return tags(uniqueTags);
  }

  @Override
  public CreateProcessInstanceCommandStep3 tags(final Iterable<String> tags) {

    final Set<String> uniqueTags = new HashSet<>();
    for (final String item : tags) {
      uniqueTags.add(item);
    }
    return tags(uniqueTags);
  }

  @Override
  public CreateProcessInstanceCommandStep3 tags(final Set<String> tags) {
    TagUtil.ensureValidTags("tags", tags);
    grpcRequestObjectBuilder.addAllTags(tags);
    httpRequestObject.setTags(tags);
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 businessId(final String businessId) {
    grpcRequestObjectBuilder.setBusinessId(businessId);
    httpRequestObject.setBusinessId(businessId);
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep2 bpmnProcessId(final String id) {
    grpcRequestObjectBuilder.setBpmnProcessId(id);
    httpRequestObject.setProcessDefinitionId(id);
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 processDefinitionKey(final long processDefinitionKey) {
    grpcRequestObjectBuilder.setProcessDefinitionKey(processDefinitionKey);
    httpRequestObject.setProcessDefinitionVersion(null); // reset version when setting key2
    httpRequestObject.setProcessDefinitionKey(ParseUtil.keyToString(processDefinitionKey));
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 version(final int version) {
    grpcRequestObjectBuilder.setVersion(version);
    httpRequestObject.setProcessDefinitionVersion(version);
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 latestVersion() {
    return version(LATEST_VERSION);
  }

  @Override
  public FinalCommandStep<ProcessInstanceEvent> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ProcessInstanceEvent> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<ProcessInstanceEvent> sendRestRequest() {
    final HttpCamundaFuture<ProcessInstanceEvent> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-instances",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        CreateProcessInstanceResult.class,
        CreateProcessInstanceResponseImpl::new,
        result);
    return result;
  }

  private CamundaFuture<ProcessInstanceEvent> sendGrpcRequest() {
    final CreateProcessInstanceRequest request = grpcRequestObjectBuilder.build();

    final RetriableClientFutureImpl<ProcessInstanceEvent, CreateProcessInstanceResponse> future =
        new RetriableClientFutureImpl<>(
            CreateProcessInstanceResponseImpl::new,
            retryPredicate,
            streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  @Override
  public CreateProcessInstanceCommandStep3 tenantId(final String tenantId) {
    grpcRequestObjectBuilder.setTenantId(tenantId);
    httpRequestObject.setTenantId(tenantId);
    return this;
  }

  private void sendGrpcRequest(
      final CreateProcessInstanceRequest request,
      final StreamObserver<GatewayOuterClass.CreateProcessInstanceResponse> future) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .createProcessInstance(request, future);
  }

  @Override
  public CreateProcessInstanceCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
