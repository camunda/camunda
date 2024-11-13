/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

import io.camunda.exporter.CamundaExporter;
import io.camunda.exporter.DefaultExporterResourceProvider;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.AuthorizationHandler;
import io.camunda.exporter.handlers.DecisionEvaluationHandler;
import io.camunda.exporter.handlers.DecisionHandler;
import io.camunda.exporter.handlers.DecisionRequirementsHandler;
import io.camunda.exporter.handlers.EventFromIncidentHandler;
import io.camunda.exporter.handlers.EventFromJobHandler;
import io.camunda.exporter.handlers.EventFromProcessInstanceHandler;
import io.camunda.exporter.handlers.EventFromProcessMessageSubscriptionHandler;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.FlowNodeInstanceFromIncidentHandler;
import io.camunda.exporter.handlers.FlowNodeInstanceFromProcessInstanceHandler;
import io.camunda.exporter.handlers.FormHandler;
import io.camunda.exporter.handlers.GroupCreatedUpdatedHandler;
import io.camunda.exporter.handlers.IncidentHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromIncidentHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromJobHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewProcessInstanceFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewVariableFromVariableHandler;
import io.camunda.exporter.handlers.MappingCreatedHandler;
import io.camunda.exporter.handlers.MappingDeletedHandler;
import io.camunda.exporter.handlers.MetricFromProcessInstanceHandler;
import io.camunda.exporter.handlers.PostImporterQueueFromIncidentHandler;
import io.camunda.exporter.handlers.ProcessHandler;
import io.camunda.exporter.handlers.RoleCreateUpdateHandler;
import io.camunda.exporter.handlers.SequenceFlowHandler;
import io.camunda.exporter.handlers.TaskCompletedMetricHandler;
import io.camunda.exporter.handlers.UserTaskCompletionVariableHandler;
import io.camunda.exporter.handlers.UserTaskHandler;
import io.camunda.exporter.handlers.UserTaskProcessInstanceHandler;
import io.camunda.exporter.handlers.UserTaskVariableHandler;
import io.camunda.exporter.handlers.VariableHandler;
import io.camunda.exporter.handlers.operation.OperationFromIncidentHandler;
import io.camunda.exporter.handlers.operation.OperationFromProcessInstanceHandler;
import io.camunda.exporter.handlers.operation.OperationFromVariableDocumentHandler;
import io.camunda.exporter.utils.CamundaExporterITInvocationProvider;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableMappingRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableRoleRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

@ExtendWith(CamundaExporterITInvocationProvider.class)
public class CamundaExporterHandlerIT {
  static MockedStatic<OffsetDateTime> offsetDateTimeMockedStatic;
  private static final OffsetDateTime CURRENT_TIME =
      OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
  private final ProtocolFactory factory = new ProtocolFactory();

  @BeforeAll
  static void init() {
    offsetDateTimeMockedStatic = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS);
    offsetDateTimeMockedStatic.when(OffsetDateTime::now).thenReturn(CURRENT_TIME);
  }

  @AfterAll
  static void tearDown() {
    offsetDateTimeMockedStatic.close();
  }

  @TestTemplate
  void shouldExportUsingAuthorizationHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, AuthorizationHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingDecisionEvaluationHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, DecisionEvaluationHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, decisionEvalRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingDecisionHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, DecisionHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingDecisionRequirementsHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, DecisionRequirementsHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingEventFromIncidentHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, EventFromIncidentHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingEventFromJobHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, EventFromJobHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, jobRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingEventFromProcessInstanceHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, EventFromProcessInstanceHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingEventFromProcessMessageSubscriptionHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, EventFromProcessMessageSubscriptionHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingFlowNodeInstanceIncidentHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, FlowNodeInstanceFromIncidentHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingFlowNodeInstanceProcessInstanceHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, FlowNodeInstanceFromProcessInstanceHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingFormHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, FormHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingIncidentHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, IncidentHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingListViewFlowNodeFromIncidentHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, ListViewFlowNodeFromIncidentHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingListViewFlowNodeFromJobHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, ListViewFlowNodeFromJobHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingListViewFlowNodeFromProcessInstanceHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, ListViewFlowNodeFromProcessInstanceHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingListViewProcessInstanceFromProcessInstanceHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, ListViewProcessInstanceFromProcessInstanceHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler,
        config,
        clientAdapter,
        processInstanceRecordGenerator(handler, BpmnElementType.PROCESS));
  }

  @TestTemplate
  void shouldExportUsingListViewVariableFromVariableHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, ListViewVariableFromVariableHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingMetricFromProcessInstanceHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, MetricFromProcessInstanceHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler,
        config,
        clientAdapter,
        processInstanceRecordGenerator(handler, BpmnElementType.PROCESS));
  }

  @TestTemplate
  void shouldExportUsingPostImporterQueueFromIncidentHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, PostImporterQueueFromIncidentHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingProcessHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, ProcessHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingSequenceFlowHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, SequenceFlowHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingTaskCompletedMetricHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, TaskCompletedMetricHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingRoleCreateUpdateHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, RoleCreateUpdateHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, roleRecordGenerator(handler, RoleIntent.CREATED));
  }

  @TestTemplate
  void shouldExportUsingUserTaskCompletionVariableHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, UserTaskCompletionVariableHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingUserTaskHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, UserTaskHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingUserTaskProcessInstanceHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, UserTaskProcessInstanceHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingUserTaskVariableHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, UserTaskVariableHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingVariableHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, VariableHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, defaultRecordGenerator(handler));
  }

  @TestTemplate
  void shouldExportUsingOperationFromIncidentHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, OperationFromIncidentHandler.class);
    testForOperationHandlers(handler, config, clientAdapter);
  }

  @TestTemplate
  void shouldExportUsingOperationFromProcessInstanceHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, OperationFromProcessInstanceHandler.class);
    testForOperationHandlers(handler, config, clientAdapter);
  }

  @TestTemplate
  void shouldExportUsingOperationFromVariableDocumentHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, OperationFromVariableDocumentHandler.class);
    testForOperationHandlers(handler, config, clientAdapter);
  }

  @TestTemplate
  void shouldExportUsingMappingCreatedHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, MappingCreatedHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, mappingRecordGenerator(handler, MappingIntent.CREATED));
  }

  @TestTemplate
  void shouldExportUsingMappingDeletedHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, MappingDeletedHandler.class);
    basicAssertWhereHandlerDeletesDefaultEntity(
        handler, config, clientAdapter, mappingRecordGenerator(handler, MappingIntent.DELETED));
  }

  @TestTemplate
  void shouldExportGroupCreatedUsingGroupCreateUpdateHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, GroupCreatedUpdatedHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, groupRecordGenerator(handler, GroupIntent.CREATED));
  }

  @TestTemplate
  void shouldExportGroupUpdatedUsingGroupCreateUpdateHandler(
      final ExporterConfiguration config, final SearchClientAdapter clientAdapter)
      throws IOException {
    final var handler = getHandler(config, GroupCreatedUpdatedHandler.class);
    basicAssertWhereHandlerCreatesDefaultEntity(
        handler, config, clientAdapter, groupRecordGenerator(handler, GroupIntent.UPDATED));
  }

  @SuppressWarnings("unchecked")
  private <S extends ExporterEntity<S>, T extends RecordValue> ExportHandler<S, T> getHandler(
      final ExporterConfiguration config, final Class<?> handlerClass) {
    final var provider = new DefaultExporterResourceProvider();
    provider.init(config, ClientAdapter.of(config).getExporterEntityCacheProvider());

    return provider.getExportHandlers().stream()
        .filter(handler -> handler.getClass().equals(handlerClass))
        .findFirst()
        .map(handler -> (ExportHandler<S, T>) handler)
        .orElseThrow();
  }

  private Context getContextFromConfig(final ExporterConfiguration config) {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>(config.getConnect().getType(), config));
  }

  private CamundaExporter getExporter(
      final ExporterConfiguration config, final ExportHandler<?, ?> handler) {
    final var provider = spy(new DefaultExporterResourceProvider());
    provider.init(config, ClientAdapter.of(config).getExporterEntityCacheProvider());

    doReturn(Set.of(handler)).when(provider).getExportHandlers();

    final var exporter = new CamundaExporter(provider);
    exporter.configure(getContextFromConfig(config));
    exporter.open(new ExporterTestController());

    return exporter;
  }

  private <S extends ExporterEntity<S>, T extends RecordValue> S getExpectedEntity(
      final io.camunda.zeebe.protocol.record.Record<T> record, final ExportHandler<S, T> handler) {
    final var entityId = handler.generateIds(record).getFirst();
    final var expectedEntity = handler.createNewEntity(entityId);
    handler.updateEntity(record, expectedEntity);

    return expectedEntity;
  }

  private <S extends ExporterEntity<S>, T extends RecordValue>
      void basicAssertWhereHandlerCreatesDefaultEntity(
          final ExportHandler<S, T> handler,
          final ExporterConfiguration config,
          final SearchClientAdapter clientAdapter,
          final Record<T> record)
          throws IOException {

    // given
    final var exporter = getExporter(config, handler);

    final var expectedEntity = getExpectedEntity(record, handler);

    // when
    exporter.export(record);

    // then
    final var responseEntity =
        clientAdapter.get(expectedEntity.getId(), handler.getIndexName(), handler.getEntityType());

    assertThat(responseEntity)
        .describedAs(
            "Handler [%s] correctly handles a [%s] record",
            handler.getClass().getSimpleName(), handler.getHandledValueType())
        .isEqualTo(expectedEntity);
  }

  private <S extends ExporterEntity<S>, T extends RecordValue>
      void basicAssertWhereHandlerDeletesDefaultEntity(
          final ExportHandler<S, T> handler,
          final ExporterConfiguration config,
          final SearchClientAdapter clientAdapter,
          final Record<T> record)
          throws IOException {

    // given
    final var exporter = getExporter(config, handler);

    final var expectedEntity = getExpectedEntity(record, handler);

    // when
    exporter.export(record);

    // then
    final var responseEntity =
        clientAdapter.get(expectedEntity.getId(), handler.getIndexName(), handler.getEntityType());

    assertThat(responseEntity)
        .describedAs(
            "Handler [%s] correctly deletes a [%s] record",
            handler.getClass().getSimpleName(), handler.getHandledValueType())
        .isNull();
  }

  private <S extends ExporterEntity<S>, T extends RecordValue> void testForOperationHandlers(
      final ExportHandler<S, T> handler,
      final ExporterConfiguration config,
      final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    final var exporter = getExporter(config, handler);

    final var initialDocumentId = String.valueOf(new Random().nextLong(Long.MAX_VALUE));
    clientAdapter.index(initialDocumentId, handler.getIndexName(), new HashMap<>());

    final Record<T> operationRecord =
        recordGenerator(
            handler,
            () ->
                factory.generateRecord(
                    handler.getHandledValueType(),
                    r -> r.withOperationReference(Long.parseLong(initialDocumentId))));

    // when
    exporter.export(operationRecord);

    // then
    final var updatedEntity =
        clientAdapter.get(initialDocumentId, handler.getIndexName(), OperationEntity.class);

    assertThat(updatedEntity.getLockExpirationTime()).isNull();
    assertThat(updatedEntity.getLockOwner()).isNull();
    assertThat(updatedEntity.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(updatedEntity.getCompletedDate()).isEqualTo(CURRENT_TIME);
  }

  private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> recordGenerator(
      final ExportHandler<S, T> handler, final Supplier<Record<T>> createRecord) {
    // Sometimes the factory generates record with intents that are not supported by the handler.
    var record = createRecord.get();
    do {
      record = createRecord.get();
    } while (!handler.handlesRecord(record));
    return record;
  }

  private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> jobRecordGenerator(
      final ExportHandler<S, T> handler) {
    return recordGenerator(
        handler,
        () -> {
          final var jobRecordValue =
              ImmutableJobRecordValue.builder()
                  .from(factory.generateObject(JobRecordValue.class))
                  .withDeadline(System.currentTimeMillis() + 10000)
                  .build();
          return factory.generateRecord(
              ValueType.JOB,
              r ->
                  r.withValue((T) jobRecordValue)
                      .withTimestamp(System.currentTimeMillis())
                      .withIntent(JobIntent.CREATED));
        });
  }

  private <S extends ExporterEntity<S>, T extends RecordValue>
      Record<T> decisionEvalRecordGenerator(final ExportHandler<S, T> handler) {
    return recordGenerator(
        handler,
        () -> {
          final ImmutableEvaluatedDecisionValue decisionValue =
              ImmutableEvaluatedDecisionValue.builder()
                  .from(factory.generateObject(EvaluatedDecisionValue.class))
                  .build();
          final DecisionEvaluationRecordValue decisionRecordValue =
              ImmutableDecisionEvaluationRecordValue.builder()
                  .from(factory.generateObject(DecisionEvaluationRecordValue.class))
                  .withEvaluatedDecisions(List.of(decisionValue))
                  .build();
          return factory.generateRecord(
              ValueType.DECISION_EVALUATION,
              r -> r.withValue((T) decisionRecordValue).withTimestamp(System.currentTimeMillis()));
        });
  }

  private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> mappingRecordGenerator(
      final ExportHandler<S, T> handler, final MappingIntent intent) {
    return recordGenerator(
        handler,
        () -> {
          final var mappingRecordValue =
              ImmutableMappingRecordValue.builder()
                  .from(factory.generateObject(MappingRecordValue.class))
                  .build();
          return factory.generateRecord(
              ValueType.MAPPING,
              r ->
                  r.withValue((T) mappingRecordValue)
                      .withIntent(intent)
                      .withTimestamp(System.currentTimeMillis()));
        });
  }

  private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> groupRecordGenerator(
      final ExportHandler<S, T> handler, final GroupIntent intent) {
    return recordGenerator(
        handler,
        () -> {
          final var groupRecordValue =
              ImmutableGroupRecordValue.builder()
                  .from(factory.generateObject(GroupRecordValue.class))
                  .build();
          return factory.generateRecord(
              ValueType.GROUP,
              r ->
                  r.withValue((T) groupRecordValue)
                      .withIntent(intent)
                      .withTimestamp(System.currentTimeMillis()));
        });
  }

  private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> roleRecordGenerator(
      final ExportHandler<S, T> handler, final RoleIntent intent) {
    return recordGenerator(
        handler,
        () -> {
          final var roleRecordValue =
              ImmutableRoleRecordValue.builder()
                  .from(factory.generateObject(RoleRecordValue.class))
                  .build();
          return factory.generateRecord(
              ValueType.ROLE,
              r ->
                  r.withValue((T) roleRecordValue)
                      .withIntent(intent)
                      .withTimestamp(System.currentTimeMillis()));
        });
  }

  private <S extends ExporterEntity<S>, T extends RecordValue> Record<T> defaultRecordGenerator(
      final ExportHandler<S, T> handler) {
    return recordGenerator(
        handler,
        () ->
            factory.generateRecord(
                handler.getHandledValueType(), r -> r.withTimestamp(System.currentTimeMillis())));
  }

  private <S extends ExporterEntity<S>, T extends RecordValue>
      Record<T> processInstanceRecordGenerator(
          final ExportHandler<S, T> handler, final BpmnElementType elementType) {
    return recordGenerator(
        handler,
        () -> {
          final ProcessInstanceRecordValue processInstanceRecordValue =
              ImmutableProcessInstanceRecordValue.builder()
                  .from(factory.generateObject(ProcessInstanceRecordValue.class))
                  .withParentProcessInstanceKey(-1L)
                  .withBpmnElementType(elementType)
                  .build();
          return factory.generateRecord(
              ValueType.PROCESS_INSTANCE,
              r ->
                  r.withIntent(ELEMENT_ACTIVATING)
                      .withValue((T) processInstanceRecordValue)
                      .withTimestamp(System.currentTimeMillis()));
        });
  }
}
