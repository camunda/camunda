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
import static org.mockito.Mockito.mockStatic;

import io.camunda.exporter.CamundaExporter;
import io.camunda.exporter.handlers.DecisionEvaluationHandler;
import io.camunda.exporter.handlers.EventFromJobHandler;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromJobHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewProcessInstanceFromProcessInstanceHandler;
import io.camunda.exporter.handlers.MetricFromProcessInstanceHandler;
import io.camunda.exporter.handlers.UserTaskProcessInstanceHandler;
import io.camunda.exporter.utils.CamundaExporterITInvocationProvider;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.webapps.schema.entities.ExporterEntity;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CamundaExporterITInvocationProvider.class)
public class CamundaExporterHandlerIT {
  final Map<Class<?>, Function<ExportHandler<?, ?>, Record<?>>> customRecordGenerators;
  private final ProtocolFactory factory = new ProtocolFactory();

  public CamundaExporterHandlerIT() {
    customRecordGenerators =
        new HashMap<>(
            Map.of(
                EventFromJobHandler.class,
                this::jobRecordGenerator,
                ListViewFlowNodeFromJobHandler.class,
                this::jobRecordGenerator,
                ListViewProcessInstanceFromProcessInstanceHandler.class,
                (handler) -> processInstanceRecordGenerator(handler, BpmnElementType.PROCESS),
                ListViewFlowNodeFromProcessInstanceHandler.class,
                (handler) ->
                    processInstanceRecordGenerator(handler, BpmnElementType.BOUNDARY_EVENT),
                DecisionEvaluationHandler.class,
                this::decisionEvalRecordGenerator,
                MetricFromProcessInstanceHandler.class,
                (handler) -> processInstanceRecordGenerator(handler, BpmnElementType.PROCESS),
                UserTaskProcessInstanceHandler.class,
                (handler) -> processInstanceRecordGenerator(handler, BpmnElementType.PROCESS)));
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  <S extends ExporterEntity<S>, T extends RecordValue> void allHandlerTestsWithInvocationProvider(
      final CamundaExporter exporter,
      final SearchClientAdapter clientAdapter,
      final ExportHandler<S, T> handler)
      throws IOException {

    final var currentTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    try (final var mocked = mockStatic(OffsetDateTime.class)) {
      mocked.when(OffsetDateTime::now).thenReturn(currentTime);

      final var record =
          (Record<T>)
              customRecordGenerators
                  .getOrDefault(handler.getClass(), this::defaultRecordGenerator)
                  .apply(handler);
      customRecordGenerators.put(handler.getClass(), (ignored) -> record);

      final var entityId = handler.generateIds(record).getFirst();
      final var expectedEntity = handler.createNewEntity(entityId);
      handler.updateEntity(record, expectedEntity);
      exporter.export(record);

      final var responseEntity =
          clientAdapter.get(
              expectedEntity.getId(), handler.getIndexName(), handler.getEntityType());

      assertThat(responseEntity)
          .describedAs(
              "Handler [%s] correctly handles a [%s] record",
              handler.getClass().getSimpleName(), handler.getHandledValueType())
          .isEqualTo(expectedEntity);
    }
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
