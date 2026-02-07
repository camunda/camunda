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

import static io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider.provideSearchRequestProperty;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.CreateBatchOperationCommandStep2;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.CreateBatchOperationCommandStep3;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.ProcessInstanceMigrationStep;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.ProcessInstanceModificationStep;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.request.SearchRequestBuilders;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateBatchOperationResponseImpl;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;
import io.camunda.client.protocol.rest.BatchOperationTypeEnum;
import io.camunda.client.protocol.rest.DecisionInstanceDeletionBatchOperationRequest;
import io.camunda.client.protocol.rest.MigrateProcessInstanceMappingInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceCancellationBatchOperationRequest;
import io.camunda.client.protocol.rest.ProcessInstanceDeletionBatchOperationRequest;
import io.camunda.client.protocol.rest.ProcessInstanceIncidentResolutionBatchOperationRequest;
import io.camunda.client.protocol.rest.ProcessInstanceMigrationBatchOperationPlan;
import io.camunda.client.protocol.rest.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.client.protocol.rest.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.client.protocol.rest.ProcessInstanceModificationMoveBatchOperationInstruction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateBatchOperationCommandImpl<E extends SearchRequestFilter>
    implements ProcessInstanceMigrationStep<E>,
        CreateBatchOperationCommandStep2<E>,
        ProcessInstanceModificationStep<E>,
        CreateBatchOperationCommandStep3<E> {
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  private final BatchOperationTypeEnum type;
  private final Function<Consumer<E>, E> filterFactory;
  private E filter;
  private final ProcessInstanceMigrationBatchOperationPlan migrationPlan =
      new ProcessInstanceMigrationBatchOperationPlan();
  private final List<ProcessInstanceModificationMoveBatchOperationInstruction> moveInstructions =
      new ArrayList<>();

  public CreateBatchOperationCommandImpl(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final BatchOperationTypeEnum type,
      final Function<Consumer<E>, E> filterFactory) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();

    this.type = type;
    this.filterFactory = filterFactory;
  }

  @Override
  public CreateBatchOperationCommandStep3<E> filter(final E filter) {
    this.filter = Objects.requireNonNull(filter, "must specify a filter");
    return this;
  }

  @Override
  public CreateBatchOperationCommandStep3<E> filter(final Consumer<E> fn) {
    Objects.requireNonNull(fn, "must specify a filter consumer");
    filter = filterFactory.apply(fn);
    return this;
  }

  @Override
  public ProcessInstanceModificationStep<E> addMoveInstruction(
      final String sourceElementId, final String targetElementId) {
    Objects.requireNonNull(sourceElementId, "must specify a source element id");
    Objects.requireNonNull(targetElementId, "must specify a target element id");
    moveInstructions.add(
        new ProcessInstanceModificationMoveBatchOperationInstruction()
            .sourceElementId(sourceElementId)
            .targetElementId(targetElementId));
    return this;
  }

  @Override
  public FinalCommandStep<CreateBatchOperationResponse> requestTimeout(
      final Duration requestTimeout) {
    ArgumentUtil.ensurePositive("requestTimeout", requestTimeout);
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateBatchOperationResponse> send() {
    final HttpCamundaFuture<CreateBatchOperationResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        getUrl(),
        jsonMapper.toJson(getBody()),
        httpRequestConfig.build(),
        BatchOperationCreatedResult.class,
        CreateBatchOperationResponseImpl::new,
        result);
    return result;
  }

  private String getUrl() {
    switch (type) {
      case CANCEL_PROCESS_INSTANCE:
        return "/process-instances/cancellation";
      case DELETE_PROCESS_INSTANCE:
        return "/process-instances/deletion";
      case DELETE_DECISION_INSTANCE:
        return "/decision-instances/deletion";
      case RESOLVE_INCIDENT:
        return "/process-instances/incident-resolution";
      case MIGRATE_PROCESS_INSTANCE:
        return "/process-instances/migration";
      case MODIFY_PROCESS_INSTANCE:
        return "/process-instances/modification";
      default:
        throw new IllegalArgumentException("Unsupported batch operation type: " + type);
    }
  }

  private Object getBody() {
    switch (type) {
      case MIGRATE_PROCESS_INSTANCE:
        return new ProcessInstanceMigrationBatchOperationRequest()
            .filter(provideSearchRequestProperty(filter))
            .migrationPlan(migrationPlan);
      case MODIFY_PROCESS_INSTANCE:
        return new ProcessInstanceModificationBatchOperationRequest()
            .filter(provideSearchRequestProperty(filter))
            .moveInstructions(moveInstructions);
      case CANCEL_PROCESS_INSTANCE:
        return new ProcessInstanceCancellationBatchOperationRequest()
            .filter(provideSearchRequestProperty(filter));
      case DELETE_PROCESS_INSTANCE:
        return new ProcessInstanceDeletionBatchOperationRequest()
            .filter(provideSearchRequestProperty(filter));
      case DELETE_DECISION_INSTANCE:
        return new DecisionInstanceDeletionBatchOperationRequest()
            .filter(provideSearchRequestProperty(filter));
      case RESOLVE_INCIDENT:
        return new ProcessInstanceIncidentResolutionBatchOperationRequest()
            .filter(provideSearchRequestProperty(filter));
      default:
        throw new IllegalArgumentException("Unsupported batch operation type: " + type);
    }
  }

  @Override
  public ProcessInstanceMigrationStep<E> migrationPlan(final MigrationPlan migrationPlan) {
    targetProcessDefinitionKey(migrationPlan.getTargetProcessDefinitionKey());
    migrationPlan
        .getMappingInstructions()
        .forEach(
            instruction ->
                addMappingInstruction(
                    instruction.getSourceElementId(), instruction.getTargetElementId()));
    return this;
  }

  @Override
  public ProcessInstanceMigrationStep<E> addMappingInstruction(
      final String sourceElementId, final String targetElementId) {
    Objects.requireNonNull(sourceElementId, "must specify a source element id");
    Objects.requireNonNull(targetElementId, "must specify a target element id");
    migrationPlan.addMappingInstructionsItem(
        new MigrateProcessInstanceMappingInstruction()
            .sourceElementId(sourceElementId)
            .targetElementId(targetElementId));
    return this;
  }

  @Override
  public ProcessInstanceMigrationStep<E> targetProcessDefinitionKey(
      final long targetProcessDefinitionKey) {
    migrationPlan.targetProcessDefinitionKey(String.valueOf(targetProcessDefinitionKey));
    return this;
  }

  public static class CreateBatchOperationCommandStep1Impl
      implements CreateBatchOperationCommandStep1 {

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;

    public CreateBatchOperationCommandStep1Impl(
        final HttpClient httpClient, final JsonMapper jsonMapper) {
      this.httpClient = httpClient;
      this.jsonMapper = jsonMapper;
    }

    @Override
    public CreateBatchOperationCommandStep2<ProcessInstanceFilter> processInstanceCancel() {
      return new CreateBatchOperationCommandImpl<>(
          httpClient,
          jsonMapper,
          BatchOperationTypeEnum.CANCEL_PROCESS_INSTANCE,
          SearchRequestBuilders::processInstanceFilter);
    }

    @Override
    public CreateBatchOperationCommandStep2<ProcessInstanceFilter> processInstanceDelete() {
      return new CreateBatchOperationCommandImpl<>(
          httpClient,
          jsonMapper,
          BatchOperationTypeEnum.DELETE_PROCESS_INSTANCE,
          SearchRequestBuilders::processInstanceFilter);
    }

    @Override
    public CreateBatchOperationCommandStep2<ProcessInstanceFilter> resolveIncident() {
      return new CreateBatchOperationCommandImpl<>(
          httpClient,
          jsonMapper,
          BatchOperationTypeEnum.RESOLVE_INCIDENT,
          SearchRequestBuilders::processInstanceFilter);
    }

    @Override
    public ProcessInstanceMigrationStep<ProcessInstanceFilter> migrateProcessInstance() {
      return new CreateBatchOperationCommandImpl<>(
          httpClient,
          jsonMapper,
          BatchOperationTypeEnum.MIGRATE_PROCESS_INSTANCE,
          SearchRequestBuilders::processInstanceFilter);
    }

    @Override
    public ProcessInstanceModificationStep<ProcessInstanceFilter> modifyProcessInstance() {
      return new CreateBatchOperationCommandImpl<>(
          httpClient,
          jsonMapper,
          BatchOperationTypeEnum.MODIFY_PROCESS_INSTANCE,
          SearchRequestBuilders::processInstanceFilter);
    }

    @Override
    public CreateBatchOperationCommandStep2<DecisionInstanceFilter> decisionInstanceDelete() {
      return new CreateBatchOperationCommandImpl<>(
          httpClient,
          jsonMapper,
          BatchOperationTypeEnum.DELETE_DECISION_INSTANCE,
          SearchRequestBuilders::decisionInstanceFilter);
    }
  }
}
