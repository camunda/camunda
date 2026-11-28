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

import io.camunda.exporter.handlers.ExportHandler.IdAndIndex;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryActorType;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryEntity;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration.ActorAuditLogConfiguration;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableSourceRecord;
import io.camunda.zeebe.protocol.record.ImmutableAgent;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class VariableHistoryHandlerTest {

  private static final String INDEX_NAME = "variable-history";
  private static final int VARIABLE_SIZE_THRESHOLD = 10;
  private static final int PARTITION_ID = 3;
  private static final Map<String, Object> USER = Map.of(Authorization.AUTHORIZED_USERNAME, "demo");
  private static final Map<String, Object> CLIENT =
      Map.of(Authorization.AUTHORIZED_CLIENT_ID, "billing-client");

  private final ProtocolFactory factory = new ProtocolFactory();
  private final VariableHistoryHandler underTest =
      new VariableHistoryHandler(INDEX_NAME, VARIABLE_SIZE_THRESHOLD, new AuditLogConfiguration());

  /** A variable value from an API call, with the random fields of the factory. */
  private ImmutableVariableRecordValue.Builder variable() {
    return ImmutableVariableRecordValue.builder()
        .from(factory.generateObject(VariableRecordValue.class))
        .withSource(new VariableSourceRecord().setType(VariableOperationType.API));
  }

  private Record<VariableRecordValue> record(
      final VariableIntent intent,
      final VariableRecordValue value,
      final Map<String, Object> authorizations,
      final long position) {
    // the factory seeds a random agent, so the record is built here and has no agent unless the
    // test sets one
    return ImmutableRecord.<VariableRecordValue>builder()
        .withValueType(ValueType.VARIABLE)
        .withIntent(intent)
        .withPartitionId(PARTITION_ID)
        .withPosition(position)
        .withKey(value.getScopeKey() + 1)
        .withTimestamp(1_700_000_000_123L)
        .withAuthorizations(authorizations)
        .withValue(value)
        .build();
  }

  private Record<VariableRecordValue> record(
      final VariableIntent intent, final Map<String, Object> authorizations) {
    return record(intent, variable().build(), authorizations, 100L);
  }

  private static AuditLogConfiguration everything() {
    final var all = ActorAuditLogConfiguration.logAll();
    return new AuditLogConfiguration().setUser(all).setClient(all).setUnknown(all).setEngine(all);
  }

  private VariableHistoryEntity entityOf(final Record<VariableRecordValue> record) {
    final var entity = underTest.createNewEntity("id");
    underTest.updateEntity(record, entity);
    return entity;
  }

  @Test
  void shouldHandleVariableRecords() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.VARIABLE);
  }

  @Test
  void shouldCreateVariableHistoryEntities() {
    // when
    final var entity = underTest.createNewEntity("id");

    // then
    assertThat(underTest.getEntityType()).isEqualTo(VariableHistoryEntity.class);
    assertThat(entity.getId()).isEqualTo("id");
  }

  @ParameterizedTest
  @EnumSource(
      value = VariableIntent.class,
      names = {"CREATED", "UPDATED"})
  void shouldHandleACreatedOrUpdatedChangeOfAUser(final VariableIntent intent) {
    // given
    final var record = record(intent, USER);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = VariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleOtherIntents(final VariableIntent intent) {
    // given
    final var record = record(intent, USER);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleAChangeOfAClientWithTheDefaultSections() {
    // given
    final var record = record(VariableIntent.CREATED, CLIENT);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleAChangeOfTheEngineWithTheDefaultSections() {
    // given a change that an output mapping wrote, under the command of a user
    final var value =
        variable()
            .withSource(new VariableSourceRecord().setType(VariableOperationType.OUTPUT_MAPPING))
            .build();
    final var record = record(VariableIntent.CREATED, value, USER, 100L);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldHandleAChangeOfTheEngineWhenItsSectionSelectsIt() {
    // given
    final var handler =
        new VariableHistoryHandler(INDEX_NAME, VARIABLE_SIZE_THRESHOLD, everything());
    final var value =
        variable()
            .withSource(new VariableSourceRecord().setType(VariableOperationType.OUTPUT_MAPPING))
            .build();
    final var record = record(VariableIntent.CREATED, value, USER, 100L);

    // when - then
    assertThat(handler.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldGenerateTheIdFromThePartitionAndThePosition() {
    // given
    final var record = record(VariableIntent.CREATED, variable().build(), USER, 100L);

    // when - then
    assertThat(underTest.generateIds(record)).containsExactly(PARTITION_ID + "-100");
  }

  @Test
  void shouldGiveTwoChangesOfOneVariableDistinctIdsAndOrderedKeys() {
    // given two changes of the same variable, one after the other in the log
    final var value = variable().build();
    final var first = record(VariableIntent.CREATED, value, USER, 100L);
    final var second = record(VariableIntent.UPDATED, value, USER, 101L);

    // when
    final var firstEntity = entityOf(first);
    final var secondEntity = entityOf(second);

    // then
    assertThat(underTest.generateIds(first)).isNotEqualTo(underTest.generateIds(second));
    assertThat(firstEntity.getVariableHistoryItemKey())
        .isLessThan(secondEntity.getVariableHistoryItemKey());
  }

  @Test
  void shouldEncodeThePartitionAndThePositionInTheItemKey() {
    // given
    final var record = record(VariableIntent.CREATED, variable().build(), USER, 100L);

    // when
    final var entity = entityOf(record);

    // then
    assertThat(entity.getVariableHistoryItemKey())
        .isEqualTo(Protocol.encodePartitionId(PARTITION_ID, 100L));
  }

  @Test
  void shouldRouteToTheOrdinalIndexOfTheRecord() {
    // given
    final var value = variable().withStorageOrdinal(5).build();
    final var record = record(VariableIntent.CREATED, value, USER, 100L);

    // when
    final var result = underTest.extractIdAndIndexes(new TargetIndexLocator(), record);

    // then
    assertThat(result)
        .containsExactly(
            new IdAndIndex(PARTITION_ID + "-100", TargetIndex.ordinalIndex(INDEX_NAME, 5)));
  }

  @Test
  void shouldRouteToTheMainIndexWithoutAnOrdinal() {
    // given
    final var value = variable().withStorageOrdinal(0).build();
    final var record = record(VariableIntent.CREATED, value, USER, 100L);

    // when
    final var result = underTest.extractIdAndIndexes(new TargetIndexLocator(), record);

    // then
    assertThat(result)
        .containsExactly(new IdAndIndex(PARTITION_ID + "-100", TargetIndex.mainIndex(INDEX_NAME)));
  }

  @Test
  void shouldMapTheRecordToTheEntity() {
    // given
    final var source =
        new VariableSourceRecord()
            .setType(VariableOperationType.JOB_COMPLETION)
            .setElementInstanceKey(55L)
            .setElementId("review");
    final var value =
        variable()
            .withName("approved")
            .withValue("true")
            .withScopeKey(10L)
            .withProcessInstanceKey(11L)
            .withRootProcessInstanceKey(12L)
            .withProcessDefinitionKey(13L)
            .withBpmnProcessId("order-process")
            .withTenantId("tenant-a")
            .withSource(source)
            .build();
    final var record = record(VariableIntent.UPDATED, value, USER, 100L);

    // when
    final var entity = entityOf(record);

    // then
    assertThat(entity.getVariableKey()).isEqualTo(record.getKey());
    assertThat(entity.getName()).isEqualTo("approved");
    assertThat(entity.getValue()).isEqualTo("true");
    assertThat(entity.getFullValue()).isNull();
    assertThat(entity.getIsPreview()).isFalse();
    assertThat(entity.getScopeKey()).isEqualTo(10L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(11L);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(12L);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(13L);
    assertThat(entity.getProcessDefinitionId()).isEqualTo("order-process");
    assertThat(entity.getTenantId()).isEqualTo("tenant-a");
    assertThat(entity.getTimestamp())
        .isEqualTo(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(1_700_000_000_123L), ZoneOffset.UTC));
    assertThat(entity.getOperationType()).isEqualTo(VariableHistoryOperationType.UPDATE);
    assertThat(entity.getVariableOperationType()).isEqualTo(VariableOperationType.JOB_COMPLETION);
    assertThat(entity.getElementInstanceKey()).isEqualTo(55L);
    assertThat(entity.getElementId()).isEqualTo("review");
    assertThat(entity.getActorType()).isEqualTo(VariableHistoryActorType.USER);
    assertThat(entity.getActorId()).isEqualTo("demo");
    assertThat(entity.getAgentElementId()).isNull();
    assertThat(entity.getItemIndex()).isNull();
  }

  @Test
  void shouldStoreCreatedAsACreateOperation() {
    // given
    final var record = record(VariableIntent.CREATED, USER);

    // when - then
    assertThat(entityOf(record).getOperationType()).isEqualTo(VariableHistoryOperationType.CREATE);
  }

  @Test
  void shouldStoreAValueAboveTheThresholdAsPreviewAndFullValue() {
    // given
    final var longValue = "v".repeat(VARIABLE_SIZE_THRESHOLD + 1);
    final var record =
        record(VariableIntent.CREATED, variable().withValue(longValue).build(), USER, 100L);

    // when
    final var entity = entityOf(record);

    // then
    assertThat(entity.getValue()).isEqualTo("v".repeat(VARIABLE_SIZE_THRESHOLD));
    assertThat(entity.getFullValue()).isEqualTo(longValue);
    assertThat(entity.getIsPreview()).isTrue();
  }

  @Test
  void shouldStoreAValueAtTheThresholdWithoutFullValue() {
    // given
    final var value = "v".repeat(VARIABLE_SIZE_THRESHOLD);
    final var record =
        record(VariableIntent.CREATED, variable().withValue(value).build(), USER, 100L);

    // when
    final var entity = entityOf(record);

    // then
    assertThat(entity.getValue()).isEqualTo(value);
    assertThat(entity.getFullValue()).isNull();
    assertThat(entity.getIsPreview()).isFalse();
  }

  @Test
  void shouldStoreAnEventFromBeforeTheUpgradeWithoutCausingElement() {
    // given an event with a type but without the element, as written by 8.9 and 8.10
    final var value =
        variable()
            .withSource(new VariableSourceRecord().setType(VariableOperationType.API))
            .build();
    final var record = record(VariableIntent.CREATED, value, USER, 100L);

    // when
    final var entity = entityOf(record);

    // then
    assertThat(entity.getVariableOperationType()).isEqualTo(VariableOperationType.API);
    assertThat(entity.getElementInstanceKey()).isNull();
    assertThat(entity.getElementId()).isNull();
  }

  @Test
  void shouldStoreTheDefaultTenantWhenTheRecordHasNone() {
    // given
    final var record =
        record(VariableIntent.CREATED, variable().withTenantId("").build(), USER, 100L);

    // when - then
    assertThat(entityOf(record).getTenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldNotStoreTheRootProcessInstanceWhenTheRecordHasNone() {
    // given
    final var record =
        record(
            VariableIntent.CREATED, variable().withRootProcessInstanceKey(-1L).build(), USER, 100L);

    // when - then
    assertThat(entityOf(record).getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldStoreAClientAsClientActor() {
    // given
    final var handler =
        new VariableHistoryHandler(INDEX_NAME, VARIABLE_SIZE_THRESHOLD, everything());
    final var record = record(VariableIntent.CREATED, CLIENT);

    // when
    final var entity = handler.createNewEntity("id");
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getActorType()).isEqualTo(VariableHistoryActorType.CLIENT);
    assertThat(entity.getActorId()).isEqualTo("billing-client");
  }

  @Test
  void shouldStoreTheEngineAsActorWithoutIdentity() {
    // given an output mapping under the command of a user
    final var handler =
        new VariableHistoryHandler(INDEX_NAME, VARIABLE_SIZE_THRESHOLD, everything());
    final var value =
        variable()
            .withSource(new VariableSourceRecord().setType(VariableOperationType.OUTPUT_MAPPING))
            .build();
    final var record = record(VariableIntent.CREATED, value, USER, 100L);

    // when
    final var entity = handler.createNewEntity("id");
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getActorType()).isEqualTo(VariableHistoryActorType.ENGINE);
    assertThat(entity.getActorId()).isNull();
  }

  @Test
  void shouldStoreAChangeWithoutReliableIdentityAsUnknownActor() {
    // given
    final var handler =
        new VariableHistoryHandler(INDEX_NAME, VARIABLE_SIZE_THRESHOLD, everything());
    final var value =
        variable()
            .withSource(
                new VariableSourceRecord().setType(VariableOperationType.MESSAGE_CORRELATION))
            .build();
    final var record = record(VariableIntent.CREATED, value, USER, 100L);

    // when
    final var entity = handler.createNewEntity("id");
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getActorType()).isEqualTo(VariableHistoryActorType.UNKNOWN);
    assertThat(entity.getActorId()).isNull();
  }

  @Test
  void shouldStoreTheAgentElementOfAUserActingForAnAgent() {
    // given
    final var record =
        ImmutableRecord.<VariableRecordValue>builder()
            .from(record(VariableIntent.CREATED, USER))
            .withAgent(ImmutableAgent.builder().withElementId("support-agent").build())
            .build();

    // when
    final var entity = entityOf(record);

    // then
    assertThat(entity.getActorType()).isEqualTo(VariableHistoryActorType.USER);
    assertThat(entity.getAgentElementId()).isEqualTo("support-agent");
  }

  @Test
  void shouldAddTheEntityOnFlush() {
    // given
    final var entity = new VariableHistoryEntity().setId("id");
    final var index = TargetIndex.mainIndex(INDEX_NAME);
    final var batchRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, batchRequest);

    // then
    verify(batchRequest).add(index, entity);
  }

  @Test
  void shouldReturnTheIndexName() {
    assertThat(underTest.getIndexName()).isEqualTo(INDEX_NAME);
  }
}
