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
import io.camunda.webapps.schema.entities.usermanagement.TenantEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableTenantRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class TenantCreateUpdateHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tenant";
  private final TenantCreateUpdateHandler underTest = new TenantCreateUpdateHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.TENANT);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(TenantEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<TenantRecordValue> tenantCreatedRecord =
        factory.generateRecordWithIntent(ValueType.TENANT, TenantIntent.CREATED);
    final Record<TenantRecordValue> tenantUpdatedRecord =
        factory.generateRecordWithIntent(ValueType.TENANT, TenantIntent.UPDATED);

    // when - then
    assertThat(underTest.handlesRecord(tenantCreatedRecord)).isTrue();
    assertThat(underTest.handlesRecord(tenantUpdatedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<TenantRecordValue> tenantRecord =
        factory.generateRecordWithIntent(ValueType.TENANT, TenantIntent.CREATED);

    // when
    final var idList = underTest.generateIds(tenantRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(tenantRecord.getKey()));
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

    final TenantRecordValue tenantRecordValue =
        ImmutableTenantRecordValue.builder()
            .from(factory.generateObject(TenantRecordValue.class))
            .withName("updated-tenant")
            .withTenantId("updated-tenantId")
            .withTenantKey(recordKey)
            .build();

    final Record<TenantRecordValue> tenantRecord =
        factory.generateRecord(
            ValueType.TENANT,
            r ->
                r.withIntent(TenantIntent.CREATED).withValue(tenantRecordValue).withKey(recordKey));

    // when
    final TenantEntity tenantEntity = new TenantEntity().setName("tenant").setTenantId("tenantId");
    underTest.updateEntity(tenantRecord, tenantEntity);

    // then
    assertThat(tenantEntity.getName()).isEqualTo("updated-tenant");
    assertThat(tenantEntity.getTenantId()).isEqualTo("updated-tenantId");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final TenantEntity inputEntity = new TenantEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }
}
