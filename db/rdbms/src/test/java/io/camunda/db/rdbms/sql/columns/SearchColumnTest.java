/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.entities.ClusterVariableScope;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.collection.Tuple;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class SearchColumnTest {
  static final OffsetDateTime EXPECTED_DATE_TIME = OffsetDateTime.parse("2025-06-04T16:30Z");
  static final Map<Class, List<Tuple>> PROBE_VALUES =
      Map.ofEntries(
          Map.entry(String.class, List.of(Tuple.of("My String", "My String"))),
          Map.entry(Integer.class, List.of(Tuple.of(10, 10), Tuple.of(10, "10"))),
          Map.entry(Long.class, List.of(Tuple.of(10L, 10L), Tuple.of(10L, "10"))),
          Map.entry(Boolean.class, List.of(Tuple.of(false, "false"), Tuple.of(false, 0))),
          Map.entry(
              OffsetDateTime.class,
              List.of(
                  Tuple.of(EXPECTED_DATE_TIME, EXPECTED_DATE_TIME),
                  Tuple.of(EXPECTED_DATE_TIME, "2025-06-04T16:30:00.000Z"),
                  Tuple.of(EXPECTED_DATE_TIME, "2025-06-04T18:30+02:00"),
                  Tuple.of(EXPECTED_DATE_TIME, EXPECTED_DATE_TIME.toInstant().toEpochMilli()))),
          Map.entry(
              BatchOperationItemState.class,
              List.of(
                  Tuple.of(BatchOperationItemState.ACTIVE, BatchOperationItemState.ACTIVE),
                  Tuple.of(BatchOperationItemState.ACTIVE, "ACTIVE"))),
          Map.entry(
              BatchOperationState.class,
              List.of(
                  Tuple.of(BatchOperationState.ACTIVE, BatchOperationState.ACTIVE),
                  Tuple.of(BatchOperationState.ACTIVE, "ACTIVE"))),
          Map.entry(
              BatchOperationType.class,
              List.of(
                  Tuple.of(
                      BatchOperationType.MIGRATE_PROCESS_INSTANCE,
                      BatchOperationType.MIGRATE_PROCESS_INSTANCE),
                  Tuple.of(
                      BatchOperationType.MIGRATE_PROCESS_INSTANCE, "MIGRATE_PROCESS_INSTANCE"))),
          Map.entry(
              ProcessInstanceState.class,
              List.of(
                  Tuple.of(ProcessInstanceState.ACTIVE, ProcessInstanceState.ACTIVE),
                  Tuple.of(ProcessInstanceState.ACTIVE, "ACTIVE"))),
          Map.entry(
              DecisionDefinitionType.class,
              List.of(
                  Tuple.of(
                      DecisionDefinitionType.DECISION_TABLE, DecisionDefinitionType.DECISION_TABLE),
                  Tuple.of(DecisionDefinitionType.DECISION_TABLE, "DECISION_TABLE"))),
          Map.entry(
              DecisionInstanceState.class,
              List.of(
                  Tuple.of(DecisionInstanceState.EVALUATED, DecisionInstanceState.EVALUATED),
                  Tuple.of(DecisionInstanceState.EVALUATED, "EVALUATED"))),
          Map.entry(
              IncidentEntity.ErrorType.class,
              List.of(
                  Tuple.of(
                      IncidentEntity.ErrorType.CALLED_ELEMENT_ERROR,
                      IncidentEntity.ErrorType.CALLED_ELEMENT_ERROR),
                  Tuple.of(IncidentEntity.ErrorType.CALLED_ELEMENT_ERROR, "CALLED_ELEMENT_ERROR"))),
          Map.entry(
              IncidentState.class,
              List.of(
                  Tuple.of(IncidentState.MIGRATED, IncidentState.MIGRATED),
                  Tuple.of(IncidentState.MIGRATED, "MIGRATED"))),
          Map.entry(
              FlowNodeState.class,
              List.of(
                  Tuple.of(FlowNodeState.ACTIVE, FlowNodeState.ACTIVE),
                  Tuple.of(FlowNodeState.ACTIVE, "ACTIVE"))),
          Map.entry(
              FlowNodeType.class,
              List.of(
                  Tuple.of(FlowNodeType.CALL_ACTIVITY, FlowNodeType.CALL_ACTIVITY),
                  Tuple.of(FlowNodeType.CALL_ACTIVITY, "CALL_ACTIVITY"))),
          Map.entry(
              JobState.class,
              List.of(
                  Tuple.of(JobState.CREATED, JobState.CREATED),
                  Tuple.of(JobState.CREATED, "CREATED"))),
          Map.entry(
              JobKind.class,
              List.of(
                  Tuple.of(JobKind.BPMN_ELEMENT, JobKind.BPMN_ELEMENT),
                  Tuple.of(JobKind.BPMN_ELEMENT, "BPMN_ELEMENT"))),
          Map.entry(
              ListenerEventType.class,
              List.of(
                  Tuple.of(ListenerEventType.START, ListenerEventType.START),
                  Tuple.of(ListenerEventType.START, "START"))),
          Map.entry(
              MessageSubscriptionState.class,
              List.of(
                  Tuple.of(MessageSubscriptionState.CREATED, MessageSubscriptionState.CREATED),
                  Tuple.of(MessageSubscriptionState.CREATED, "CREATED"))),
          Map.entry(
              EntityType.class,
              List.of(
                  Tuple.of(EntityType.USER, EntityType.USER), Tuple.of(EntityType.USER, "USER"))),
          Map.entry(
              ClusterVariableScope.class,
              List.of(
                  Tuple.of(ClusterVariableScope.GLOBAL, ClusterVariableScope.GLOBAL),
                  Tuple.of(ClusterVariableScope.GLOBAL, "GLOBAL"))),
          Map.entry(
              AuditLogActorType.class,
              List.of(
                  Tuple.of(AuditLogActorType.USER, AuditLogActorType.USER),
                  Tuple.of(AuditLogActorType.USER, "USER"))),
          Map.entry(
              AuditLogEntityType.class,
              List.of(
                  Tuple.of(
                      AuditLogEntityType.PROCESS_INSTANCE, AuditLogEntityType.PROCESS_INSTANCE),
                  Tuple.of(AuditLogEntityType.PROCESS_INSTANCE, "PROCESS_INSTANCE"))),
          Map.entry(
              AuditLogOperationCategory.class,
              List.of(
                  Tuple.of(AuditLogOperationCategory.ADMIN, AuditLogOperationCategory.ADMIN),
                  Tuple.of(AuditLogOperationCategory.ADMIN, "ADMIN"))),
          Map.entry(
              AuditLogOperationResult.class,
              List.of(
                  Tuple.of(AuditLogOperationResult.SUCCESS, AuditLogOperationResult.SUCCESS),
                  Tuple.of(AuditLogOperationResult.SUCCESS, "SUCCESS"))),
          Map.entry(
              AuditLogOperationType.class,
              List.of(
                  Tuple.of(AuditLogOperationType.CREATE, AuditLogOperationType.CREATE),
                  Tuple.of(AuditLogOperationType.CREATE, "CREATE"))),
          Map.entry(
              AuditLogTenantScope.class,
              List.of(
                  Tuple.of(AuditLogTenantScope.TENANT, AuditLogTenantScope.TENANT),
                  Tuple.of(AuditLogTenantScope.TENANT, "TENANT"))),
          Map.entry(
              GlobalListenerSource.class,
              List.of(
                  Tuple.of(GlobalListenerSource.API, GlobalListenerSource.API),
                  Tuple.of(GlobalListenerSource.API, "API"))),
          Map.entry(
              GlobalListenerType.class,
              List.of(
                  Tuple.of(GlobalListenerType.USER_TASK, GlobalListenerType.USER_TASK),
                  Tuple.of(GlobalListenerType.USER_TASK, "USER_TASK"))));

  private static List<Object[]> provideSearchColumns() {
    return SearchColumnUtils.findAll().stream()
        .map(column -> new Object[] {column.getEntityClass().getSimpleName(), column})
        .toList();
  }

  @ParameterizedTest(name = "{0}#{1}")
  @MethodSource("provideSearchColumns")
  void testAllGetPropertyValue(final String className, final SearchColumn<?> column) {
    assertThatCode(() -> column.getPropertyType()).doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "{0}#{1}")
  @MethodSource("provideSearchColumns")
  void testAllConvertToPropertyValue(final String className, final SearchColumn<?> column) {

    for (final var value : getProbesForColumn(column)) {
      final var expectedValue = value.getLeft();
      final var encodedValue = value.getRight();
      final var convertedValue = column.convertToPropertyValue(value.getRight());

      assertThat(convertedValue)
          .describedAs(
              "Conversion mismatch for input value %s (type: %s)",
              encodedValue, encodedValue.getClass().getSimpleName())
          .isEqualTo(expectedValue);
    }
  }

  private List<Tuple> getProbesForColumn(final SearchColumn<?> column) {
    final var probes = PROBE_VALUES.get(column.getPropertyType());
    assertThat(probes)
        .describedAs("No probes found for column type %s", column.getPropertyType())
        .isNotNull();
    assertThat(probes).allSatisfy(p -> p.getLeft().equals(column.getPropertyType()));
    return probes;
  }
}
