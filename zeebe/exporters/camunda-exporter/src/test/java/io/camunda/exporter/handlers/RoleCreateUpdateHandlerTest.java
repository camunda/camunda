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
import io.camunda.webapps.schema.entities.usermanagement.RoleEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableRoleRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class RoleCreateUpdateHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-role";
  private final RoleCreateUpdateHandler underTest = new RoleCreateUpdateHandler(indexName);

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
    final Record<RoleRecordValue> roleCreatedRecord =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.CREATED);
    final Record<RoleRecordValue> roleUpdatedRecord =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.UPDATED);

    // when - then
    assertThat(underTest.handlesRecord(roleCreatedRecord)).isTrue();
    assertThat(underTest.handlesRecord(roleUpdatedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<RoleRecordValue> roleRecord =
        factory.generateRecordWithIntent(ValueType.ROLE, RoleIntent.CREATED);

    // when
    final var idList = underTest.generateIds(roleRecord);

    // then
    assertThat(idList).containsExactly(roleRecord.getValue().getRoleId());
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
    final long recordKey = 123L;

    final RoleRecordValue roleRecordValue =
        ImmutableRoleRecordValue.builder()
            .from(factory.generateObject(RoleRecordValue.class))
            .withName("updated-role")
            .withRoleId("updated-roleId")
            .withRoleKey(recordKey)
            .build();

    final Record<RoleRecordValue> roleRecord =
        factory.generateRecord(
            ValueType.ROLE,
            r -> r.withIntent(RoleIntent.CREATED).withValue(roleRecordValue).withKey(recordKey));

    // when
    final RoleEntity roleEntity = new RoleEntity().setName("role").setRoleId("roleId");
    underTest.updateEntity(roleRecord, roleEntity);

    // then
    assertThat(roleEntity.getName()).isEqualTo("updated-role");
    assertThat(roleEntity.getRoleId()).isEqualTo("updated-roleId");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final RoleEntity inputEntity = new RoleEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }
}
