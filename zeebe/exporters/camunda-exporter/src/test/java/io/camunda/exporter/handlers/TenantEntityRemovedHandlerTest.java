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
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import io.camunda.webapps.schema.entities.usermanagement.TenantMemberEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class TenantEntityRemovedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tenant";
  private final TenantEntityRemovedHandler underTest = new TenantEntityRemovedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.TENANT);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(TenantMemberEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<TenantRecordValue> tenantRemovedRecord =
        factory.generateRecordWithIntent(ValueType.TENANT, TenantIntent.ENTITY_REMOVED);

    // when - then
    assertThat(underTest.handlesRecord(tenantRemovedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<TenantRecordValue> tenantRecord =
        factory.generateRecordWithIntent(ValueType.TENANT, TenantIntent.ENTITY_REMOVED);

    // when
    final var idList = underTest.generateIds(tenantRecord);

    // then
    final var value = tenantRecord.getValue();
    assertThat(idList)
        .containsExactly(TenantMemberEntity.getChildKey(value.getTenantId(), value.getEntityId()));
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
  void shouldUpdateTenantEntityOnFlush() throws PersistenceException {
    // given
    final var joinRelation = TenantIndex.JOIN_RELATION_FACTORY.createChild("111");
    final var inputEntity =
        new TenantMemberEntity().setId("111").setMemberId("member-id-1").setJoin(joinRelation);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .deleteWithRouting(indexName, inputEntity.getId(), String.valueOf(joinRelation.parent()));
  }
}
