/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.usermanagement.index.TenantIndex;
import io.camunda.webapps.schema.entities.usermanagement.TenantEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableTenantRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class TenantEntityAddedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tenant";
  private final TenantEntityAddedHandler underTest = new TenantEntityAddedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.TENANT);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<TenantRecordValue> entityAddedRecord =
        factory.generateRecordWithIntent(ValueType.TENANT, TenantIntent.ENTITY_ADDED);

    // when - then
    assertThat(underTest.handlesRecord(entityAddedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<TenantRecordValue> tenantRecord =
        factory.generateRecordWithIntent(ValueType.TENANT, TenantIntent.ENTITY_ADDED);

    // when
    final var idList = underTest.generateIds(tenantRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(tenantRecord.getKey()));
  }

  @Test
  void shouldAddEntityKeyToAssignedMembers() {
    // given
    final long existingKey = 100L;
    final long newKey = 123L;

    // Create a TenantEntity with an existing key in a mutable assignedMemberKeys set
    final TenantEntity tenantEntity = new TenantEntity();
    tenantEntity.setAssignedMemberKeys(new HashSet<>(Set.of(existingKey)));

    // Create a record for the new key to be added
    final TenantRecordValue tenantRecordValue =
        ImmutableTenantRecordValue.builder()
            .from(factory.generateObject(TenantRecordValue.class))
            .withEntityKey(newKey)
            .build();

    final Record<TenantRecordValue> tenantRecord =
        factory.generateRecord(
            ValueType.TENANT,
            r -> r.withIntent(TenantIntent.ENTITY_ADDED).withValue(tenantRecordValue));

    // when
    underTest.updateEntity(tenantRecord, tenantEntity);

    // then
    assertThat(tenantEntity.getAssignedMemberKeys()).containsExactlyInAnyOrder(existingKey, newKey);
  }

  @Test
  void shouldFlushOnlyAssignedMemberKeysField() throws PersistenceException {
    // given
    final TenantEntity tenantEntity = new TenantEntity().setId("tenant-1");
    tenantEntity.setAssignedMemberKeys(Set.of(123L, 456L)); // Simulate keys already present
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(tenantEntity, mockRequest);

    // then
    final Map<String, Object> expectedUpdateFields =
        Map.of(TenantIndex.ASSIGNED_MEMBER_KEYS, Set.of(123L, 456L));
    verify(mockRequest, times(1)).update(indexName, tenantEntity.getId(), expectedUpdateFields);
  }
}
