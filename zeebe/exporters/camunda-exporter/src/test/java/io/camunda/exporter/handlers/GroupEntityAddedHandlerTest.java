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
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.entities.usermanagement.GroupEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class GroupEntityAddedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-group";
  private final GroupEntityAddedHandler underTest = new GroupEntityAddedHandler(indexName);

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
    final io.camunda.zeebe.protocol.record.Record<GroupRecordValue> groupDeletedRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.ENTITY_ADDED);

    // when - then
    assertThat(underTest.handlesRecord(groupDeletedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<GroupRecordValue> groupRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.ENTITY_ADDED);

    // when
    final var idList = underTest.generateIds(groupRecord);

    // then
    final var value = groupRecord.getValue();
    // TODO: revisit with https://github.com/camunda/camunda/pull/30697
    assertThat(idList)
        .containsExactly(GroupEntity.getChildKey(value.getGroupKey(), Long.parseLong(value.getEntityId())));
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
  void shouldUpdateGroupEntityOnFlush() throws PersistenceException {
    // given
    final var joinRelation = GroupIndex.JOIN_RELATION_FACTORY.createChild(111L);
    final GroupEntity inputEntity =
        new GroupEntity().setId("111").setMemberKey(222L).setJoin(joinRelation);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .addWithRouting(indexName, inputEntity, String.valueOf(joinRelation.parent()));
  }
}
