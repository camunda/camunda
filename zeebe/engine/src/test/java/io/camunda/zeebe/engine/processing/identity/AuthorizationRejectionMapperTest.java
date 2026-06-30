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
}
