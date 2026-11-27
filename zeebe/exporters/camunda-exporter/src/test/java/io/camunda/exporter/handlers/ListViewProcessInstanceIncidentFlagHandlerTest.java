/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ListViewProcessInstanceIncidentFlagHandlerTest {

  private static final long P1 = 1L;
  private static final long P1E1 = 2L;
  private static final long P2 = 3L;
  private static final long P2E1 = 4L;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final ListViewProcessInstanceIncidentFlagHandler underTest =
      new ListViewProcessInstanceIncidentFlagHandler(indexName);

  @Test
  void shouldHandleValueTypeAndEntityType() {
    // when - then
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
    assertThat(underTest.getEntityType()).isEqualTo(ProcessInstanceForListViewEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentIntent.class,
      names = {"CREATED", "RESOLVED"})
  void shouldHandleFlagIntentWithProcessInstanceKeys(final IncidentIntent intent) {
    // given
    final var incidentRecord = recordWith(intent, List.of(P1, P1E1, P2, P2E1));

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isTrue();
  }

  @Test
  void shouldNotHandleMigratedIntent() {
    // given
    final var incidentRecord = recordWith(IncidentIntent.MIGRATED, List.of(P1, P1E1, P2, P2E1));

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWithoutFlagKeys() {
    // given
    final var incidentRecord = recordWith(IncidentIntent.CREATED, List.of());

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWithFlowNodeKeysOnly() {
    // given
    final var incidentRecord = recordWith(IncidentIntent.CREATED, List.of(P1E1, P2E1));

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isFalse();
  }

  @Test
  void shouldGenerateIdsFromProcessInstanceKeys() {
    // given
    final var incidentRecord = recordWith(IncidentIntent.CREATED, List.of(P1, P1E1, P2, P2E1));

    // when
    final var ids = underTest.generateIds(incidentRecord);

    // then
    assertThat(ids).containsExactly(String.valueOf(P1), String.valueOf(P2));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var entity = underTest.createNewEntity("id");

    // then
    assertThat(entity.getId()).isEqualTo("id");
  }

  @Test
  void shouldSetIncidentFlagAndKeysOnCreated() {
    // given
    final var incidentRecord = recordWith(IncidentIntent.CREATED, List.of(P1, P1E1, P2, P2E1));
    final var entity = underTest.createNewEntity(String.valueOf(P2));

    // when
    underTest.updateEntity(incidentRecord, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(P2));
    assertThat(entity.getKey()).isEqualTo(P2);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(P2);
    assertThat(entity.getPartitionId()).isEqualTo(incidentRecord.getPartitionId());
    assertThat(entity.getTenantId()).isEqualTo(incidentRecord.getValue().getTenantId());
    assertThat(entity.isIncident()).isTrue();
  }

  @Test
  void shouldClearIncidentFlagOnResolved() {
    // given
    final var incidentRecord = recordWith(IncidentIntent.RESOLVED, List.of(P1, P1E1, P2, P2E1));
    final var entity = underTest.createNewEntity(String.valueOf(P1));

    // when
    underTest.updateEntity(incidentRecord, entity);

    // then
    assertThat(entity.getKey()).isEqualTo(P1);
    assertThat(entity.isIncident()).isFalse();
  }

  @Test
  void shouldUpsertOnlyIncidentFieldOnFlush() {
    // given
    final var incidentRecord = recordWith(IncidentIntent.CREATED, List.of(P1, P1E1, P2, P2E1));
    final var entity = underTest.createNewEntity(String.valueOf(P1));
    underTest.updateEntity(incidentRecord, entity);
    final TargetIndex index = TargetIndex.mainIndex(indexName);
    final BatchRequest batchRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, batchRequest);

    // then
    verify(batchRequest).upsert(index, String.valueOf(P1), entity, Map.of("incident", true));
  }

  private Record<IncidentRecordValue> recordWith(
      final IncidentIntent intent, final List<Long> flagKeys) {
    final IncidentRecordValue value =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withElementInstancePath(List.of(List.of(P1, P1E1), List.of(P2, P2E1)))
            .withIncidentFlagKeys(flagKeys)
            .build();
    return factory.generateRecord(
        ValueType.INCIDENT, r -> r.withIntent(intent).withValue(value).withPartitionId(2));
  }
}
