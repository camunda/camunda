/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import org.junit.jupiter.api.Test;

class AuthorizationRejectionMapperTest {

  @Test
  void shouldMapTenantRejectionToForbidden() {
    // given
    final var rejection = new AuthorizationRejection.Tenant("my-tenant");
    // when
    final Rejection result = AuthorizationRejectionMapper.toRejection(rejection);
    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason()).contains("my-tenant");
  }

  @Test
  void shouldMapPermissionRejectionToForbidden() {
    // given
    final var rejection =
        new AuthorizationRejection.Permission(
            AuthorizationResourceType.AUTHORIZATION, PermissionType.CREATE, "resource-1");
    // when
    final Rejection result = AuthorizationRejectionMapper.toRejection(rejection);
    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason()).contains("AUTHORIZATION").contains("CREATE");
  }

  @Test
  void shouldMapPermissionRejectionToBareForbiddenWithoutResourceIds() {
    // given a permission rejection carrying a concrete resource id
    final var rejection =
        new AuthorizationRejection.Permission(
            AuthorizationResourceType.GROUP, PermissionType.DELETE, "group-1");
    // when mapped via the bare mapper (identity-processor path)
    final Rejection result = AuthorizationRejectionMapper.toBareRejection(rejection);
    // then the message omits the resource-identifier suffix, matching the pre-migration message
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason())
        .isEqualTo("Insufficient permissions to perform operation 'DELETE' on resource 'GROUP'");
    assertThat(result.reason()).doesNotContain("required resource identifiers");
  }

  @Test
  void shouldMapTenantRejectionIdenticallyForBareAndSuffixedMappers() {
    // given
    final var rejection = new AuthorizationRejection.Tenant("my-tenant");
    // when / then a non-permission rejection is mapped identically by both mappers
    assertThat(AuthorizationRejectionMapper.toBareRejection(rejection).reason())
        .isEqualTo(AuthorizationRejectionMapper.toRejection(rejection).reason());
  }

  @Test
  void shouldBuildNoPrincipalRejectionWithStableMessage() {
    // when
    final Rejection result = AuthorizationRejectionMapper.noPrincipal();
    // then — stable message used by the UserTask/Job command paths when no identity claim is
    // present
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason())
        .isEqualTo("No authenticated user or client could be determined for the request.");
  }

  @Test
  void shouldMapPropertyRejectionToForbidden() {
    // given
    final var rejection =
        new AuthorizationRejection.Property(
            AuthorizationResourceType.USER_TASK,
            PermissionType.UPDATE,
            new java.util.TreeSet<>(java.util.Set.of("assignee", "candidateUsers")));
    // when
    final Rejection result = AuthorizationRejectionMapper.toRejection(rejection);
    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason())
        .contains("USER_TASK")
        .contains("UPDATE")
        .contains("assignee, candidateUsers");
  }
}
