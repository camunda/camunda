/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.access.AuthorizationDto;
import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuthorizationRepositoryIT extends AbstractBrokerlessZeebeCCSMIT {

  private AuthorizationRepository authorizationRepository;

  @BeforeEach
  void setup() {
    authorizationRepository = embeddedOptimizeExtension.getBean(AuthorizationRepository.class);
  }

  @Test
  void shouldReadBackEveryAuthorizationField() {
    // given
    final AuthorizationDto authorization =
        new AuthorizationDto(
            uniqueId(),
            AuthorizationOwnerType.MAPPING_RULE,
            "finance-department",
            AuthorizationResourceType.COMPONENT,
            AuthorizationResourceMatcher.ID,
            "optimize",
            null,
            Set.of(PermissionType.ACCESS, PermissionType.READ));

    // when
    authorizationRepository.upsert(authorization);

    // then
    assertThat(authorizationRepository.get(authorization.id())).contains(authorization);
    assertThat(authorizationRepository.getAll()).containsExactly(authorization);
  }

  @Test
  void shouldReplaceAuthorizationOnUpsert() {
    // given
    final AuthorizationDto original = componentAccess(uniqueId(), "optimize");
    authorizationRepository.upsert(original);
    final AuthorizationDto changed = componentAccess(original.id(), "optimize-admin");

    // when
    authorizationRepository.upsert(changed);

    // then
    assertThat(authorizationRepository.getAll()).containsExactly(changed);
  }

  @Test
  void shouldDeleteOnlyTheGivenAuthorization() {
    // given
    final AuthorizationDto deleted = componentAccess(uniqueId(), "optimize");
    final AuthorizationDto kept = componentAccess(uniqueId(), "optimize-admin");
    authorizationRepository.upsert(deleted);
    authorizationRepository.upsert(kept);

    // when
    authorizationRepository.delete(deleted.id());
    authorizationRepository.delete(uniqueId());

    // then
    assertThat(authorizationRepository.get(deleted.id())).isEmpty();
    assertThat(authorizationRepository.getAll()).containsExactly(kept);
  }

  private static AuthorizationDto componentAccess(final String id, final String resourceId) {
    return new AuthorizationDto(
        id,
        AuthorizationOwnerType.USER,
        "demo",
        AuthorizationResourceType.COMPONENT,
        AuthorizationResourceMatcher.ID,
        resourceId,
        null,
        Set.of(PermissionType.ACCESS));
  }

  private static String uniqueId() {
    return "authorization-" + UUID.randomUUID();
  }
}
