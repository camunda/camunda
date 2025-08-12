/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.RoleMemberRemovedHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class RoleMemberRemovedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-role";
  private final RoleMemberRemovedHandler underTest = new RoleMemberRemovedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.ROLE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(RoleMemberEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<RoleRecordValue> roleRemovedRecord =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.ENTITY_REMOVED);

    // when - then
    assertThat(underTest.handlesRecord(roleRemovedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<RoleRecordValue> roleRecord =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.ENTITY_REMOVED);

    // when
    final var idList = underTest.generateIds(roleRecord);

    // then
    final var value = roleRecord.getValue();
    assertThat(idList)
        .containsExactly(
            RoleIndex.JOIN_RELATION_FACTORY.createChildId(
                value.getRoleId(), value.getEntityId(), value.getEntityType()));
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
  void shouldUpdateRoleEntityOnFlush() throws PersistenceException {
    // given
    final var joinRelation = RoleIndex.JOIN_RELATION_FACTORY.createChild("111");
    final var inputEntity =
        new RoleMemberEntity().setId("111").setMemberId("member-id-1").setJoin(joinRelation);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .deleteWithRouting(indexName, inputEntity.getId(), String.valueOf(joinRelation.parent()));
  }
}
