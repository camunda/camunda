/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel.Builder;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class AuthorizationEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final AuthorizationDbModel model =
        new Builder()
            .authorizationKey(1L)
            .ownerId("ownerId")
            .ownerType("USER")
            .resourceType("PROCESS_DEFINITION")
            .resourceMatcher((short) 1)
            .resourceId("resourceId")
            .resourcePropertyName("resourcePropertyName")
            .permissionTypes(Set.of(PermissionType.READ, PermissionType.CREATE))
            .build();

    // When
    final AuthorizationEntity entity = AuthorizationEntityMapper.toEntity(model);

    // Then
    assertThat(entity.authorizationKey()).isEqualTo(1L);
    assertThat(entity.ownerId()).isEqualTo("ownerId");
    assertThat(entity.ownerType()).isEqualTo("USER");
    assertThat(entity.resourceType()).isEqualTo("PROCESS_DEFINITION");
    assertThat(entity.resourceMatcher()).isEqualTo((short) 1);
    assertThat(entity.resourceId()).isEqualTo("resourceId");
    assertThat(entity.resourcePropertyName()).isEqualTo("resourcePropertyName");
    assertThat(entity.permissionTypes())
        .containsExactlyInAnyOrder(PermissionType.READ, PermissionType.CREATE);
  }

  @Test
  public void testToEntityWithNullPermissionTypes() {
    // Given
    final AuthorizationDbModel model =
        new Builder()
            .authorizationKey(1L)
            .ownerId("ownerId")
            .ownerType("USER")
            .resourceType("PROCESS_DEFINITION")
            .resourceMatcher(null)
            .resourceId("resourceId")
            .resourcePropertyName(null)
            .permissionTypes(null)
            .build();

    // When
    final AuthorizationEntity entity = AuthorizationEntityMapper.toEntity(model);

    // Then — compact constructor in AuthorizationEntity defaults null to an empty set
    assertThat(entity.permissionTypes()).isNotNull().isEmpty();
  }
}
