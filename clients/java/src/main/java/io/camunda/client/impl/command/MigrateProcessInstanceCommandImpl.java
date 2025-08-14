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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.MigrateProcessInstanceCommandStep1;
import io.camunda.client.api.command.MigrateProcessInstanceCommandStep1.MigrateProcessInstanceCommandFinalStep;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.response.MigrateProcessInstanceResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.EmptyApiResponse;
import io.camunda.client.impl.response.MigrateProcessInstanceResponseImpl;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.MigrateProcessInstanceMappingInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceMigrationInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest.MappingInstruction;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.config.RequestConfig;

public final class MigrateProcessInstanceCommandImpl
    implements MigrateProcessInstanceCommandStep1, MigrateProcessInstanceCommandFinalStep {

  private final MigrateProcessInstanceRequest.Builder requestBuilder =
      MigrateProcessInstanceRequest.newBuilder();
  private final GatewayStub asyncStub;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final ProcessInstanceMigrationInstruction httpRequestObject;
  private boolean useRest;
  private final long processInstanceKey;
  private final JsonMapper jsonMapper;

  public MigrateProcessInstanceCommandImpl(
      final long processInstanceKey,
      final GatewayStub asyncStub,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final CamundaClientConfiguration config,
      final JsonMapper jsonMapper) {
    requestBuilder.setProcessInstanceKey(processInstanceKey);
    this.asyncStub = asyncStub;
    requestTimeout = config.getDefaultRequestTimeout();
    this.retryPredicate = retryPredicate;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new ProcessInstanceMigrationInstruction();
    useRest = config.preferRestOverGrpc();
    this.processInstanceKey = processInstanceKey;
    this.jsonMapper = jsonMapper;
    requestTimeout(requestTimeout);
  }

  @Override
  public MigrateProcessInstanceCommandFinalStep migrationPlan(
      final long targetProcessDefinitionKey) {
    requestBuilder.setMigrationPlan(
        MigrateProcessInstanceRequest.MigrationPlan.newBuilder()
            .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .build());
    httpRequestObject.setTargetProcessDefinitionKey(
        ParseUtil.keyToString(targetProcessDefinitionKey));
    return this;
  }

  @Override
  public MigrateProcessInstanceCommandFinalStep migrationPlan(final MigrationPlan migrationPlan) {
    final List<MappingInstruction> mappingInstructions =
        migrationPlan.getMappingInstructions().stream()
            .map(
                mappingInstruction ->
                    buildMappingInstruction(
                        mappingInstruction.getSourceElementId(),
                        mappingInstruction.getTargetElementId()))
            .collect(Collectors.toList());
    requestBuilder.setMigrationPlan(
        MigrateProcessInstanceRequest.MigrationPlan.newBuilder()
            .setTargetProcessDefinitionKey(migrationPlan.getTargetProcessDefinitionKey())
            .addAllMappingInstructions(mappingInstructions));
    buildRequestObject(migrationPlan);
    return this;
  }

  private void buildRequestObject(final MigrationPlan migrationPlan) {
    httpRequestObject.setTargetProcessDefinitionKey(
        ParseUtil.keyToString(migrationPlan.getTargetProcessDefinitionKey()));
    httpRequestObject.setMappingInstructions(
        migrationPlan.getMappingInstructions().stream()
            .map(
                mappingInstruction ->
                    new MigrateProcessInstanceMappingInstruction()
                        .sourceElementId(mappingInstruction.getSourceElementId())
                        .targetElementId(mappingInstruction.getTargetElementId()))
            .collect(Collectors.toList()));
  }

  @Override
  public MigrateProcessInstanceCommandFinalStep addMappingInstruction(
      final String sourceElementId, final String targetElementId) {
    requestBuilder
        .getMigrationPlanBuilder()
        .addMappingInstructions(buildMappingInstruction(sourceElementId, targetElementId));
    httpRequestObject.addMappingInstructionsItem(
        new MigrateProcessInstanceMappingInstruction()
            .sourceElementId(sourceElementId)
            .targetElementId(targetElementId));
    return this;
  }

  @Override
  public FinalCommandStep<MigrateProcessInstanceResponse> requestTimeout(
      final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<MigrateProcessInstanceResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<MigrateProcessInstanceResponse> sendRestRequest() {
    final HttpCamundaFuture<MigrateProcessInstanceResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-instances/" + processInstanceKey + "/migration",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        r -> new EmptyApiResponse(),
        result);
    return result;
  }

  private CamundaFuture<MigrateProcessInstanceResponse> sendGrpcRequest() {
    final MigrateProcessInstanceRequest request = requestBuilder.build();

    final RetriableClientFutureImpl<
            MigrateProcessInstanceResponse, GatewayOuterClass.MigrateProcessInstanceResponse>
        future =
            new RetriableClientFutureImpl<>(
                MigrateProcessInstanceResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);

    return future;
  }

  private void sendGrpcRequest(
      final MigrateProcessInstanceRequest request,
      final StreamObserver<GatewayOuterClass.MigrateProcessInstanceResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .migrateProcessInstance(request, streamObserver);
  }

  private MappingInstruction buildMappingInstruction(
      final String sourceElementId, final String targetElementId) {
    ArgumentUtil.ensureNotNull("sourceElementId", sourceElementId);
    ArgumentUtil.ensureNotNull("targetElementId", targetElementId);

    return MappingInstruction.newBuilder()
        .setSourceElementId(sourceElementId)
        .setTargetElementId(targetElementId)
        .build();
  }

  @Override
  public MigrateProcessInstanceCommandFinalStep operationReference(final long operationReference) {
    requestBuilder.setOperationReference(operationReference);
    httpRequestObject.setOperationReference(operationReference);
    return this;
  }

  @Override
  public MigrateProcessInstanceCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public MigrateProcessInstanceCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
