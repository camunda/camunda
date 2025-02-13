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
import io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class AuthorizationDeletedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-authentication";
  private final AuthorizationDeletedHandler underTest = new AuthorizationDeletedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.AUTHORIZATION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(AuthorizationEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<AuthorizationRecordValue> authorizationRecord =
        factory.generateRecordWithIntent(ValueType.AUTHORIZATION, AuthorizationIntent.DELETED);

    // when - then
    assertThat(underTest.handlesRecord(authorizationRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<AuthorizationRecordValue> authorizationRecord =
        factory.generateRecordWithIntent(ValueType.AUTHORIZATION, AuthorizationIntent.DELETED);

    // when
    final var idList = underTest.generateIds(authorizationRecord);

    // then
    assertThat(idList)
        .containsExactly(String.valueOf(authorizationRecord.getValue().getAuthorizationKey()));
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
  void shouldDeleteEntityOnFlush() throws PersistenceException {
    // given
    final AuthorizationEntity inputEntity = new AuthorizationEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).delete(indexName, inputEntity.getId());
  }
}
