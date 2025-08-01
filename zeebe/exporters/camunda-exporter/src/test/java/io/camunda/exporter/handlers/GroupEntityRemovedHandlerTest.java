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
import io.camunda.webapps.schema.entities.usermanagement.GroupMemberEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.Test;

public class GroupEntityRemovedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-group";
  private final GroupEntityRemovedHandler underTest = new GroupEntityRemovedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.GROUP);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(GroupMemberEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<GroupRecordValue> groupDeletedRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.ENTITY_REMOVED);

    // when - then
    assertThat(underTest.handlesRecord(groupDeletedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<GroupRecordValue> groupRecord =
        factory.generateRecordWithIntent(ValueType.GROUP, GroupIntent.ENTITY_REMOVED);

    // when
    final var idList = underTest.generateIds(groupRecord);

    // then
    final var value = groupRecord.getValue();
    assertThat(idList)
        .containsExactly(
            GroupIndex.JOIN_RELATION_FACTORY.createChildId(
                value.getGroupId(), value.getEntityId()));
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
    final var memberId = Strings.newRandomValidIdentityId();
    final var joinRelation = GroupIndex.JOIN_RELATION_FACTORY.createChild("111");
    final var inputEntity =
        new GroupMemberEntity().setId("111").setMemberId(memberId).setJoin(joinRelation);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .deleteWithRouting(indexName, inputEntity.getId(), String.valueOf(joinRelation.parent()));
  }
}
