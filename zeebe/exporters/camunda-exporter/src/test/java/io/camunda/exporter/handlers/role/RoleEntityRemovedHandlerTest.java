/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.usermanagement.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.RoleEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableRoleRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class RoleEntityRemovedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-role";
  private final RoleEntityRemovedHandler underTest = new RoleEntityRemovedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.ROLE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(RoleEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<RoleRecordValue> roleRecord =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.ENTITY_REMOVED);

    // when - then
    assertThat(underTest.handlesRecord(roleRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<RoleRecordValue> roleRecord =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.ENTITY_REMOVED);

    // when
    final var idList = underTest.generateIds(roleRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(roleRecord.getKey()));
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
  void shouldRemoveEntityOnFlush() throws PersistenceException {
    // given
    final RoleEntity inputEntity =
        new RoleEntity().setId("111").setAssignedMemberKeys(Set.of(100L, 200L));
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(RoleIndex.ASSIGNEDMEMBERKEYS, inputEntity.getAssignedMemberKeys());

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).update(indexName, inputEntity.getId(), updateFields);
  }

  @Test
  void shouldRemoveRecordValueOnEntityWhenUpdating() throws PersistenceException {
    // given
    final RoleRecordValue roleRecordValue =
        ImmutableRoleRecordValue.builder()
            .from(factory.generateObject(RoleRecordValue.class))
            .withEntityKey(200L)
            .build();

    final Record<RoleRecordValue> roleRecord =
        factory.generateRecord(
            ValueType.ROLE,
            r -> r.withIntent(RoleIntent.ENTITY_REMOVED).withValue(roleRecordValue));

    final RoleEntity inputEntity =
        new RoleEntity().setId("111").setAssignedMemberKeys(Set.of(100L, 200L));

    // when
    underTest.updateEntity(roleRecord, inputEntity);

    // then
    assertThat(inputEntity.getAssignedMemberKeys().size()).isEqualTo(1);
  }
}
