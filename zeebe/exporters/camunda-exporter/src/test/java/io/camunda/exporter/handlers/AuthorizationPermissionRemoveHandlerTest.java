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
import io.camunda.security.entity.Permission;
import io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue.PermissionValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.ImmutableAuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutablePermissionValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AuthorizationPermissionRemoveHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-authorization";
  private final AuthorizationPermissionRemovedHandler underTest =
      new AuthorizationPermissionRemovedHandler(indexName);

  @Test
  void shouldHandleRecord() {
    // given
    final Record<AuthorizationRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.AUTHORIZATION, AuthorizationIntent.PERMISSION_REMOVED);

    // when
    final boolean result = underTest.handlesRecord(record);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final AuthorizationRecordValue value =
        ImmutableAuthorizationRecordValue.builder()
            .withOwnerKey(6741987849931465728L)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.USER)
            .withPermissions(
                List.of(
                    ImmutablePermissionValue.builder()
                        .withPermissionType(PermissionType.DELETE)
                        .withResourceIds(List.of("vMgnykrx"))
                        .build()))
            .build();

    final Record<AuthorizationRecordValue> record =
        factory.generateRecord(ValueType.AUTHORIZATION, r -> r.withValue(value));

    // when
    final List<String> ids = underTest.generateIds(record);

    // then
    assertThat(ids)
        .containsExactly(
            String.format(
                "%s-%s-%s-%s",
                record.getValue().getOwnerKey(),
                record.getValue().getResourceType().name(),
                getFirstPermission(value.getPermissions()).type(),
                getFirstPermission(value.getPermissions()).resourceIds().stream()
                    .findFirst()
                    .get()));
  }

  private Permission getFirstPermission(final List<PermissionValue> permissionValues) {
    return permissionValues.stream()
        .findFirst()
        .map(
            permissionValue ->
                new Permission(
                    permissionValue.getPermissionType(), permissionValue.getResourceIds()))
        .orElseThrow();
  }

  @Test
  void shouldUpdateEntity() {
    // given

    final long recordKey = 123L;

    final PermissionValue permissionValue =
        ImmutablePermissionValue.builder()
            .from(factory.generateObject(PermissionValue.class))
            .withPermissionType(PermissionType.UPDATE)
            .withResourceIds(List.of("resource1"))
            .build();

    final AuthorizationRecordValue authValue =
        ImmutableAuthorizationRecordValue.builder()
            .from(factory.generateObject(AuthorizationRecordValue.class))
            .withPermissions(List.of(permissionValue))
            .withOwnerKey(recordKey)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.USER)
            .build();

    final Record<AuthorizationRecordValue> record =
        factory.generateRecord(ValueType.AUTHORIZATION, r -> r.withValue(authValue));

    final AuthorizationEntity entity = new AuthorizationEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getOwnerKey()).isEqualTo(recordKey);
    assertThat(entity.getOwnerType()).isEqualTo("USER");
    assertThat(entity.getResourceType()).isEqualTo("USER");

    // Assert permissions
    assertThat(entity.getPermissionType()).isEqualTo(PermissionType.UPDATE);
    assertThat(entity.getResourceId()).isEqualTo("resource1");
  }

  @Test
  void shouldFlushEntity() throws PersistenceException {
    // given
    final AuthorizationEntity entity =
        new AuthorizationEntity()
            .setId("123-USER-READ-resource1")
            .setOwnerKey(123L)
            .setOwnerType("USER")
            .setResourceType("USER")
            .setPermissionType(PermissionType.READ)
            .setResourceId("resource1");

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    // then
    verify(mockRequest, times(1)).delete(indexName, entity.getId());
  }
}
