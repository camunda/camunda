/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.zeebe.util.collection.Tuple;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.support.ReflectionSupport;

public class SearchColumnTest {
  private static final Set<Class<?>> SEARCH_COLUMNS =
      new HashSet<>(
          ReflectionSupport.findAllClassesInPackage(
              "io.camunda.db.rdbms.sql.columns",
              c -> {
                final var interfaces = c.getInterfaces();
                return Arrays.asList(interfaces).contains(SearchColumn.class);
              },
              ignored -> true));

  private static List<Object[]> provideSearchColumns() {
    return SEARCH_COLUMNS.stream()
        .filter(Class::isEnum) // search columns are defined as enums
        .flatMap(
            clazz -> {
              try {
                // Use reflection to call the `values()` method on the enum class
                final Object[] enumConstants = clazz.getEnumConstants();
                return Arrays.stream(enumConstants)
                    .map(e -> new Object[] {clazz.getSimpleName(), (SearchColumn<?>) e});
              } catch (final Exception e) {
                throw new RuntimeException("Failed to retrieve enum values for class: " + clazz, e);
              }
            })
        .toList();
  }

  final static OffsetDateTime expectedOffsetDateTime = OffsetDateTime.parse("2025-06-04T16:30Z");
  final static Map<Class, List<Tuple>> probeValues = Map.ofEntries(
      Map.entry(String.class, List.of(Tuple.of("My String", "My String"))),
      Map.entry(Integer.class, List.of(Tuple.of(10, 10), Tuple.of(10, "10"))),
      Map.entry(Long.class, List.of(Tuple.of(10L, 10L), Tuple.of(10L, "10"))),
      Map.entry(Boolean.class, List.of(Tuple.of(false, "false"), Tuple.of(false, 0))),
      Map.entry(OffsetDateTime.class, List.of(
          Tuple.of(expectedOffsetDateTime, expectedOffsetDateTime),
          Tuple.of(expectedOffsetDateTime, "2025-06-04T16:30:00.000Z"),
          Tuple.of(expectedOffsetDateTime, "2025-06-04T18:30+02:00"),
          Tuple.of(expectedOffsetDateTime, expectedOffsetDateTime.toInstant().toEpochMilli()))),
      Map.entry(BatchOperationItemState.class, List.of(
          Tuple.of(BatchOperationItemState.ACTIVE, BatchOperationItemState.ACTIVE),
          Tuple.of(BatchOperationItemState.ACTIVE, "ACTIVE"))),
      Map.entry(ProcessInstanceState.class, List.of(
          Tuple.of(ProcessInstanceState.ACTIVE, ProcessInstanceState.ACTIVE),
          Tuple.of(ProcessInstanceState.ACTIVE, "ACTIVE"))),
      Map.entry(DecisionDefinitionType.class, List.of(
          Tuple.of(DecisionDefinitionType.DECISION_TABLE, DecisionDefinitionType.DECISION_TABLE),
          Tuple.of(DecisionDefinitionType.DECISION_TABLE, "DECISION_TABLE"))),
      Map.entry(DecisionInstanceState.class, List.of(
          Tuple.of(DecisionInstanceState.EVALUATED, DecisionInstanceState.EVALUATED),
          Tuple.of(DecisionInstanceState.EVALUATED, "EVALUATED"))),
      Map.entry(IncidentEntity.ErrorType.class, List.of(
          Tuple.of(IncidentEntity.ErrorType.CALLED_ELEMENT_ERROR,  IncidentEntity.ErrorType.CALLED_ELEMENT_ERROR),
          Tuple.of(IncidentEntity.ErrorType.CALLED_ELEMENT_ERROR, "CALLED_ELEMENT_ERROR"))),
      Map.entry(IncidentState.class, List.of(
          Tuple.of(IncidentState.MIGRATED, IncidentState.MIGRATED),
          Tuple.of(IncidentState.MIGRATED, "MIGRATED"))),
      Map.entry(FlowNodeState.class, List.of(
          Tuple.of(FlowNodeState.ACTIVE, FlowNodeState.ACTIVE),
          Tuple.of(FlowNodeState.ACTIVE, "ACTIVE"))),
      Map.entry(FlowNodeType.class, List.of(
          Tuple.of(FlowNodeType.CALL_ACTIVITY, FlowNodeType.CALL_ACTIVITY),
          Tuple.of(FlowNodeType.CALL_ACTIVITY, "CALL_ACTIVITY")))
  );

  @ParameterizedTest(name = "{0}#{1}")
  @MethodSource("provideSearchColumns")
  void testAllGetPropertyValue(
      final String className, final SearchColumn<?> column) {
    assertDoesNotThrow(() -> column.getPropertyType());
  }

  @ParameterizedTest(name = "{0}#{1}")
  @MethodSource("provideSearchColumns")
  void testAllConvertToPropertyValue(
      final String className, final SearchColumn<?> column) {

    for(var value : this.getProbesForColumn(column)) {
      assertEquals(value.getLeft(), column.convertToPropertyValue(value.getRight()));
    }
  }

  private List<Tuple> getProbesForColumn(final SearchColumn<?> column) {
    var probes = probeValues.get(column.getPropertyType());
    assertThat(probes).describedAs("No probes found for column type %s", column.getPropertyType()).isNotNull();
    assertThat(probes).allSatisfy(p -> p.getLeft().equals(column.getPropertyType()));
    return probes;
  }
}
