/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.authz.ResourceAccessChecks;
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

  @Nested
  class ReaderFailureTests {

    private static final Map<EntityType, Set<String>> OWNERS =
        Map.of(EntityType.USER, Set.of("user1"));

    @Test
    void shouldWrapUnexpectedReaderFailureAsSearchServerFailed() {
      // given - e.g. the RDBMS reader surfaces MyBatis/Spring exceptions unwrapped
      final var cause = new IllegalStateException("relation \"tb_authorizations\" does not exist");
      final var failingRepository = new SearchAuthorizationScopeRepository(failingReader(cause));

      // when
      final var errors =
          List.of(
              catchThrowable(
                  () ->
                      failingRepository.findAuthorizedScopes(
                          OWNERS,
                          AuthorizationResourceType.PROCESS_DEFINITION,
                          PermissionType.READ_PROCESS_DEFINITION)),
              catchThrowable(
                  () ->
                      failingRepository.hasAuthorizedScope(
                          OWNERS,
                          AuthorizationResourceType.PROCESS_DEFINITION,
                          PermissionType.READ_PROCESS_DEFINITION,
                          List.of("proc-1"))),
              catchThrowable(
                  () ->
                      failingRepository.findPermissionTypes(
                          OWNERS,
                          AuthorizationResourceType.PROCESS_DEFINITION,
                          List.of("proc-1"))));

      // then
      assertThat(errors)
          .allSatisfy(
              error ->
                  assertThat(error)
                      .isInstanceOfSatisfying(
                          CamundaSearchException.class,
                          e -> {
                            assertThat(e.getReason()).isEqualTo(Reason.SEARCH_SERVER_FAILED);
                            assertThat(e).hasCause(cause);
                          }));
    }

    @Test
    void shouldKeepReasonOfReaderFailureThatIsAlreadyASearchException() {
      // given
      final var failure =
          new CamundaSearchException("Failed to execute findAll query", Reason.CONNECTION_FAILED);
      final var failingRepository = new SearchAuthorizationScopeRepository(failingReader(failure));

      // when
      final var error =
          catchThrowable(
              () ->
                  failingRepository.findPermissionTypes(
                      OWNERS, AuthorizationResourceType.PROCESS_DEFINITION, List.of("proc-1")));

      // then
      assertThat(error).isSameAs(failure);
    }

    private static FakeAuthorizationReader failingReader(final RuntimeException failure) {
      return new FakeAuthorizationReader() {
        @Override
        public SearchQueryResult<AuthorizationEntity> search(
            final AuthorizationQuery query, final ResourceAccessChecks resourceAccessChecks) {
          throw failure;
        }
      };
    }
  }
}
