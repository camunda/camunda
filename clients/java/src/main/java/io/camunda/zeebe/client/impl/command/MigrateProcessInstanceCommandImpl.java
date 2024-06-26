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
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.CredentialsProvider.StatusCode;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.MigrateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.MigrateProcessInstanceCommandStep1.MigrateProcessInstanceCommandFinalStep;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import io.camunda.zeebe.client.api.response.MigrateProcessInstanceResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.MigrateProcessInstanceResponseImpl;
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

public final class MigrateProcessInstanceCommandImpl
    implements MigrateProcessInstanceCommandStep1, MigrateProcessInstanceCommandFinalStep {

  private final MigrateProcessInstanceRequest.Builder requestBuilder =
      MigrateProcessInstanceRequest.newBuilder();
  private final GatewayStub asyncStub;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;

  public MigrateProcessInstanceCommandImpl(
      final long processInstanceKey,
      final GatewayStub asyncStub,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate) {
    requestBuilder.setProcessInstanceKey(processInstanceKey);
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
  }

  @Override
  public MigrateProcessInstanceCommandFinalStep migrationPlan(
      final long targetProcessDefinitionKey) {
    requestBuilder.setMigrationPlan(
        MigrateProcessInstanceRequest.MigrationPlan.newBuilder()
            .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .build());
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
    return this;
  }

  @Override
  public MigrateProcessInstanceCommandFinalStep addMappingInstruction(
      final String sourceElementId, final String targetElementId) {
    requestBuilder
        .getMigrationPlanBuilder()
        .addMappingInstructions(buildMappingInstruction(sourceElementId, targetElementId));
    return this;
  }

  @Override
  public FinalCommandStep<MigrateProcessInstanceResponse> requestTimeout(
      final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<MigrateProcessInstanceResponse> send() {
    final MigrateProcessInstanceRequest request = requestBuilder.build();

    final RetriableClientFutureImpl<
            MigrateProcessInstanceResponse, GatewayOuterClass.MigrateProcessInstanceResponse>
        future =
            new RetriableClientFutureImpl<>(
                MigrateProcessInstanceResponseImpl::new,
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);

    return future;
  }

  private void send(
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
    return this;
  }
}
