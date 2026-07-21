/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class AuthzModelMapperTest {

  @Test
  void shouldRoundTripAuthorizationOwnerType() {
    assertThat(
            AuthzModelMapper.fromProtocol(AuthzModelMapper.toProtocol(AuthorizationOwnerType.USER)))
        .isEqualTo(AuthorizationOwnerType.USER);
  }

  @Test
  void shouldRoundTripAuthorizationResourceMatcher() {
    assertThat(
            AuthzModelMapper.fromProtocol(
                AuthzModelMapper.toProtocol(AuthorizationResourceMatcher.PROPERTY)))
        .isEqualTo(AuthorizationResourceMatcher.PROPERTY);
  }

  @Test
  void shouldRoundTripAuthorizationResourceType() {
    assertThat(
            AuthzModelMapper.fromProtocol(
                AuthzModelMapper.toProtocol(AuthorizationResourceType.RESOURCE)))
        .isEqualTo(AuthorizationResourceType.RESOURCE);
  }

  @Test
  void shouldRoundTripEntityType() {
    assertThat(AuthzModelMapper.fromProtocol(AuthzModelMapper.toProtocol(EntityType.MAPPING_RULE)))
        .isEqualTo(EntityType.MAPPING_RULE);
  }

  @Test
  void shouldRoundTripPermissionTypeSet() {
    final Set<PermissionType> permissions =
        new HashSet<>(Arrays.asList(PermissionType.READ, PermissionType.UPDATE));
    assertThat(
            AuthzModelMapper.fromProtocolPermissionTypes(
                AuthzModelMapper.toProtocolPermissionTypes(permissions)))
        .containsExactlyInAnyOrderElementsOf(permissions);
  }

  @Test
  void shouldRoundTripSuspendProcessInstancePermissionTypes() {
    final Set<PermissionType> permissions =
        new HashSet<>(
            Arrays.asList(
                PermissionType.SUSPEND_PROCESS_INSTANCE,
                PermissionType.CREATE_BATCH_OPERATION_SUSPEND_PROCESS_INSTANCE));
    assertThat(
            AuthzModelMapper.fromProtocolPermissionTypes(
                AuthzModelMapper.toProtocolPermissionTypes(permissions)))
        .containsExactlyInAnyOrderElementsOf(permissions);
  }

  @Test
  void shouldRoundTripAuthorizationScope() {
    final AuthorizationScope scope =
        new AuthorizationScope(AuthorizationResourceMatcher.PROPERTY, "", "tenantId");
    assertThat(AuthzModelMapper.fromProtocol(AuthzModelMapper.toProtocol(scope))).isEqualTo(scope);
  }

  @Test
  void shouldMapEveryCslAuthorizationResourceTypeToProtocol() {
    for (final AuthorizationResourceType value : AuthorizationResourceType.values()) {
      assertThat(AuthzModelMapper.fromProtocol(AuthzModelMapper.toProtocol(value)))
          .as("round trip of %s", value)
          .isEqualTo(value);
    }
  }

  @Test
  void shouldMapEveryCslPermissionTypeToProtocol() {
    for (final PermissionType value : PermissionType.values()) {
      assertThat(AuthzModelMapper.fromProtocol(AuthzModelMapper.toProtocol(value)))
          .as("round trip of %s", value)
          .isEqualTo(value);
    }
  }

  @Test
  @Disabled(
      "Blocked until a CSL release includes the BACKUP and EXPORTER resource types: https://github.com/camunda/camunda-security-library/pull/550, https://github.com/camunda/camunda-security-library/pull/549")
  void shouldMapEveryProtocolAuthorizationResourceTypeToCsl() {
    for (final io.camunda.zeebe.protocol.record.value.AuthorizationResourceType value :
        io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.values()) {
      assertThat(AuthzModelMapper.toProtocol(AuthzModelMapper.fromProtocol(value)))
          .as("round trip of %s", value)
          .isEqualTo(value);
    }
  }

  @Test
  @Disabled(
      "Blocked until a CSL release includes the PAUSE and RESTORE permission types: https://github.com/camunda/camunda-security-library/pull/550, https://github.com/camunda/camunda-security-library/pull/549")
  void shouldMapEveryProtocolPermissionTypeToCsl() {
    for (final io.camunda.zeebe.protocol.record.value.PermissionType value :
        io.camunda.zeebe.protocol.record.value.PermissionType.values()) {
      assertThat(AuthzModelMapper.toProtocol(AuthzModelMapper.fromProtocol(value)))
          .as("round trip of %s", value)
          .isEqualTo(value);
    }
  }
}
