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
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.ImmutableAuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class AuthorizationCreatedUpdatedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-authentication";
  private final AuthorizationCreatedUpdatedHandler underTest =
      new AuthorizationCreatedUpdatedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.AUTHORIZATION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(AuthorizationEntity.class);
  }

  @Test
  void shouldHandleCreatedRecord() {
    // given
    final Record<AuthorizationRecordValue> authorizationRecord =
        factory.generateRecordWithIntent(ValueType.AUTHORIZATION, AuthorizationIntent.CREATED);

    // when - then
    assertThat(underTest.handlesRecord(authorizationRecord)).isTrue();
  }

  @Test
  void shouldHandleUpdatedRecord() {
    // given
    final Record<AuthorizationRecordValue> authorizationRecord =
        factory.generateRecordWithIntent(ValueType.AUTHORIZATION, AuthorizationIntent.UPDATED);

    // when - then
    assertThat(underTest.handlesRecord(authorizationRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<AuthorizationRecordValue> authorizationRecord =
        factory.generateRecordWithIntent(ValueType.AUTHORIZATION, AuthorizationIntent.CREATED);

    // when
    final var idList = underTest.generateIds(authorizationRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(authorizationRecord.getKey()));
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

    final var authorizationRecordValue =
        ImmutableAuthorizationRecordValue.builder()
            .from(factory.generateObject(AuthorizationRecordValue.class))
            .withAuthorizationKey(456L)
            .withOwnerId("foo")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceMatcher(AuthorizationResourceMatcher.ANY)
            .withResourceId("*")
            .withPermissionTypes(List.of(PermissionType.CREATE, PermissionType.DELETE))
            .build();

    final Record<AuthorizationRecordValue> authorizationRecord =
        factory.generateRecord(
            ValueType.AUTHORIZATION,
            r ->
                r.withIntent(AuthorizationIntent.CREATED)
                    .withValue(authorizationRecordValue)
                    .withKey(recordKey));

    // when
    final var authorizationEntity =
        new AuthorizationEntity()
            .setAuthorizationKey(789L)
            .setOwnerId("bar")
            .setOwnerType(AuthorizationOwnerType.GROUP.name())
            .setResourceMatcher(AuthorizationResourceMatcher.ID.value())
            .setResourceId("resourceId")
            .setPermissionTypes(Set.of(PermissionType.UPDATE));
    underTest.updateEntity(authorizationRecord, authorizationEntity);

    // then
    assertThat(authorizationEntity.getAuthorizationKey())
        .isEqualTo(authorizationRecordValue.getAuthorizationKey());
    assertThat(authorizationEntity.getOwnerId()).isEqualTo(authorizationRecordValue.getOwnerId());
    assertThat(authorizationEntity.getOwnerType())
        .isEqualTo(authorizationRecordValue.getOwnerType().name());
    assertThat(authorizationEntity.getResourceMatcher())
        .isEqualTo(authorizationRecordValue.getResourceMatcher().value());
    assertThat(authorizationEntity.getResourceId())
        .isEqualTo(authorizationRecordValue.getResourceId());
    assertThat(authorizationEntity.getPermissionTypes())
        .isEqualTo(authorizationRecordValue.getPermissionTypes());
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final var inputEntity = new AuthorizationEntity().setId("111");
    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }
}
