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
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.utils.CamundaExporterITInvocationProvider;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

@ExtendWith(CamundaExporterITInvocationProvider.class)
public class CamundaExporterHandlerIT {
  static MockedStatic<OffsetDateTime> offsetDateTimeMockedStatic;
  private final ProtocolFactory factory = new ProtocolFactory();

  @BeforeAll
  static void init() {
    final var currentTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    offsetDateTimeMockedStatic = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS);
    offsetDateTimeMockedStatic.when(OffsetDateTime::now).thenReturn(currentTime);
  }

  @AfterAll
  static void tearDown() {
    offsetDateTimeMockedStatic.close();
  }

  @SuppressWarnings("unchecked")
  private <S extends ExporterEntity<S>, T extends RecordValue> ExportHandler<S, T> getHandler(
      final ExporterConfiguration config, final Class<?> handlerClass) {
    final var provider = new DefaultExporterResourceProvider();
    provider.init(config, ClientAdapter.of(config)::getProcessCacheLoader);

    return provider.getExportHandlers().stream()
        .filter(handler -> handler.getClass().equals(handlerClass))
        .findFirst()
        .orElseThrow();
  }

  private Context getContextFromConfig(final ExporterConfiguration config) {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>(config.getConnect().getType(), config));
  }

  private CamundaExporter getExporter(
      final ExporterConfiguration config, final ExportHandler<?, ?> handler) {
    final var provider = spy(new DefaultExporterResourceProvider());
    provider.init(config, ClientAdapter.of(config)::getProcessCacheLoader);

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
          final Class<?> handlerClass,
          final ExporterConfiguration config,
          final SearchClientAdapter clientAdapter)
          throws IOException {

    final ExportHandler<S, T> handler = getHandler(config, handlerClass);
    final Record<T> record = defaultRecordGenerator(handler);

    final var expectedEntity = getExpectedEntity(record, handler);

    getExporter(config, handler).export(record);

    final var responseEntity =
        clientAdapter.get(expectedEntity.getId(), handler.getIndexName(), handler.getEntityType());

    assertThat(responseEntity)
        .describedAs(
            "Handler [%s] correctly handles a [%s] record",
            handler.getClass().getSimpleName(), handler.getHandledValueType())
        .isEqualTo(expectedEntity);
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
