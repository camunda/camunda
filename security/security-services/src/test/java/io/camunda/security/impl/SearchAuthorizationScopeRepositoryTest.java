/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SearchAuthorizationScopeRepositoryTest {

  private FakeAuthorizationReader reader;
  private SearchAuthorizationScopeRepository repository;

  @BeforeEach
  void setUp() {
    reader = new FakeAuthorizationReader();
    repository = new SearchAuthorizationScopeRepository(reader);
  }

  @Nested
  class FindAuthorizedScopesTests {

    @Test
    void shouldReturnScopeForMatchingOwnerAndPermission() {
      // given
      reader.create(
          new AuthorizationEntity(
              1L,
              "user1",
              "USER",
              "PROCESS_DEFINITION",
              null,
              "proc-1",
              null,
              Set.of(PermissionType.READ_PROCESS_DEFINITION)));

      // when
      final var result =
          repository.findAuthorizedScopes(
              Map.of(EntityType.USER, Set.of("user1")),
              AuthorizationResourceType.PROCESS_DEFINITION,
              PermissionType.READ_PROCESS_DEFINITION);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getResourceId()).isEqualTo("proc-1");
    }

    @Test
    void shouldReturnEmptyWhenOwnerDoesNotMatch() {
      // given
      reader.create(
          new AuthorizationEntity(
              1L,
              "user2",
              "USER",
              "PROCESS_DEFINITION",
              null,
              "proc-1",
              null,
              Set.of(PermissionType.READ_PROCESS_DEFINITION)));

      // when
      final var result =
          repository.findAuthorizedScopes(
              Map.of(EntityType.USER, Set.of("user1")),
              AuthorizationResourceType.PROCESS_DEFINITION,
              PermissionType.READ_PROCESS_DEFINITION);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenPermissionDoesNotMatch() {
      // given
      reader.create(
          new AuthorizationEntity(
              1L,
              "user1",
              "USER",
              "PROCESS_DEFINITION",
              null,
              "proc-1",
              null,
              Set.of(PermissionType.CREATE_PROCESS_INSTANCE)));

      // when
      final var result =
          repository.findAuthorizedScopes(
              Map.of(EntityType.USER, Set.of("user1")),
              AuthorizationResourceType.PROCESS_DEFINITION,
              PermissionType.READ_PROCESS_DEFINITION);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class HasAuthorizedScopeTests {

    @Test
    void shouldReturnTrueWhenScopeMatchesById() {
      // given
      reader.create(
          new AuthorizationEntity(
              1L,
              "user1",
              "USER",
              "PROCESS_DEFINITION",
              null,
              "proc-1",
              null,
              Set.of(PermissionType.READ_PROCESS_DEFINITION)));

      // when
      final var result =
          repository.hasAuthorizedScope(
              Map.of(EntityType.USER, Set.of("user1")),
              AuthorizationResourceType.PROCESS_DEFINITION,
              PermissionType.READ_PROCESS_DEFINITION,
              List.of(AuthorizationScope.WILDCARD.getResourceId(), "proc-1"));

      // then
      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNoScopeMatches() {
      // given — no entities in reader

      // when
      final var result =
          repository.hasAuthorizedScope(
              Map.of(EntityType.USER, Set.of("user1")),
              AuthorizationResourceType.PROCESS_DEFINITION,
              PermissionType.READ_PROCESS_DEFINITION,
              List.of(AuthorizationScope.WILDCARD.getResourceId(), "proc-1"));

      // then
      assertThat(result).isFalse();
    }
  }

  @Nested
  class FindPermissionTypesTests {

    @Test
    void shouldCollectAllPermissionTypesFromMatchingEntity() {
      // given
      reader.create(
          new AuthorizationEntity(
              1L,
              "user1",
              "USER",
              "PROCESS_DEFINITION",
              null,
              "proc-1",
              null,
              Set.of(
                  PermissionType.READ_PROCESS_DEFINITION, PermissionType.CREATE_PROCESS_INSTANCE)));

      // when
      final var result =
          repository.findPermissionTypes(
              Map.of(EntityType.USER, Set.of("user1")),
              AuthorizationResourceType.PROCESS_DEFINITION,
              List.of(AuthorizationScope.WILDCARD.getResourceId(), "proc-1"));

      // then
      assertThat(result)
          .containsExactlyInAnyOrder(
              PermissionType.READ_PROCESS_DEFINITION, PermissionType.CREATE_PROCESS_INSTANCE);
    }

    @Test
    void shouldReturnEmptyWhenNoEntityMatches() {
      // given — no entities in reader

      // when
      final var result =
          repository.findPermissionTypes(
              Map.of(EntityType.USER, Set.of("user1")),
              AuthorizationResourceType.PROCESS_DEFINITION,
              List.of(AuthorizationScope.WILDCARD.getResourceId(), "proc-1"));

      // then
      assertThat(result).isEmpty();
    }
  }
}
