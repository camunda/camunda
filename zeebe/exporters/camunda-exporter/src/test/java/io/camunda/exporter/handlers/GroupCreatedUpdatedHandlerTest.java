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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.usermanagement.GroupEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGroupRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class GroupCreatedUpdatedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-group";
  private final GroupCreatedUpdatedHandler underTest = new GroupCreatedUpdatedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.GROUP);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(GroupEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<GroupRecordValue> groupCreatedRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.CREATED);
    final Record<GroupRecordValue> groupUpdatedRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.UPDATED);

    // when - then
    assertThat(underTest.handlesRecord(groupCreatedRecord)).isTrue();
    assertThat(underTest.handlesRecord(groupUpdatedRecord)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = GroupIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldGenerateIds(final GroupIntent intent) {
    // given
    final Record<GroupRecordValue> groupRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, intent);

    // when
    final var idList = underTest.generateIds(groupRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(groupRecord.getValue().getGroupId()));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final var recordKey = 123L;
    final var groupId = Strings.newRandomValidIdentityId();

    final GroupRecordValue groupRecordValue =
        ImmutableGroupRecordValue.builder()
            .from(factory.generateObject(GroupRecordValue.class))
            .withName("updated-foo")
            .withDescription("updated-bar")
            .withGroupId(groupId)
            .withGroupKey(recordKey)
            .build();

    final Record<GroupRecordValue> groupRecord =
        factory.generateRecord(
            ValueType.GROUP,
            r -> r.withIntent(GroupIntent.CREATED).withValue(groupRecordValue).withKey(recordKey));

    // when
    final GroupEntity groupEntity = new GroupEntity().setName("foo").setGroupId(groupId).setDescription("bar");
    underTest.updateEntity(groupRecord, groupEntity);

    // then
    assertThat(groupEntity.getName()).isEqualTo("updated-foo");
    assertThat(groupEntity.getDescription()).isEqualTo("updated-bar");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final GroupEntity inputEntity = new GroupEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }
}
