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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    final AuthorizationRecordValue value = factory.generateObject(AuthorizationRecordValue.class);

    final Record<AuthorizationRecordValue> record =
        factory.generateRecord(ValueType.AUTHORIZATION, r -> r.withValue(value));

    // when
    final List<String> ids = underTest.generateIds(record);

    // then
    assertThat(ids)
        .containsExactly(
            String.format(
                "%s-%s",
                record.getValue().getOwnerKey(), record.getValue().getResourceType().name()));
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
    assertThat(entity.getPermissions()).hasSize(1);
    assertThat(entity.getPermissions().get(0).type()).isEqualTo(PermissionType.UPDATE);
    assertThat(entity.getPermissions().get(0).resourceIds()).containsExactly("resource1");
  }

  @Test
  void shouldFlushEntity() throws PersistenceException {
    // given
    final AuthorizationEntity entity =
        new AuthorizationEntity()
            .setId("123-DEPLOYMENT")
            .setOwnerKey(123L)
            .setOwnerType("USER")
            .setResourceType("DEPLOYMENT")
            .setPermissions(
                List.of(new Permission(PermissionType.READ, Set.of("resource1", "resource2"))));

    final BatchRequest mockRequest = mock(BatchRequest.class);

    final String expectedScript =
        """
        if (ctx._source.permissions != null) {
        for (p in params.inputPermissions) {
          for (permission in ctx._source.permissions) {
            if (permission.type == p.type) {
              // Remove matching resource IDs
              permission.resourceIds.removeAll(p.resourceIds);
            }
          }
        }
        // Remove permissions with empty resourceIds
        ctx._source.permissions.removeIf(permission -> permission.resourceIds.isEmpty());
        if (ctx._source.permissions.isEmpty()) {
          ctx.op = 'delete';
        }
      }
      """;

    // Expected parameters

    final Map<String, Object> inputPermissions =
        Map.of(
            "inputPermissions",
            List.of(Map.of("type", "READ", "resourceIds", List.of("resource1", "resource2"))));
    // when
    underTest.flush(entity, mockRequest);

    // then
    final ArgumentCaptor<Map<String, Object>> actualParamsCaptor =
        ArgumentCaptor.forClass(Map.class);

    verify(mockRequest, times(1))
        .updateWithScript(
            eq(indexName), eq(entity.getId()), eq(expectedScript), actualParamsCaptor.capture());

    assertThat(actualParamsCaptor.getValue())
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(inputPermissions);
  }
}
