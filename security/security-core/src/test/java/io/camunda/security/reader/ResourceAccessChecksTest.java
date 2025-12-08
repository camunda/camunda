/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.condition.AuthorizationCondition;
import io.camunda.security.auth.condition.AuthorizationConditions;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResourceAccessChecksTest {

  @Nested
  class GetAuthorizedResourceIdsByType {

    @Test
    void shouldReturnEmptyMapWhenAuthorizationDisabled() {
      // given
      final var checks = ResourceAccessChecks.disabled();

      // when
      final var result = checks.getAuthorizedResourceIdsByType();

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyMapWhenNoResourceIdsProvided() {
      // given
      final var authorization = Authorization.of(b -> b.processDefinition().readUserTask());

      final var checks =
          ResourceAccessChecks.of(
              AuthorizationCheck.enabled(authorization), TenantCheck.disabled());
      // when
      final var result = checks.getAuthorizedResourceIdsByType();

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyMapWhenNullAuthorizationCondition() {
      // given
      final var checks =
          ResourceAccessChecks.of(
              AuthorizationCheck.enabled((AuthorizationCondition) null), TenantCheck.disabled());
      // when
      final var result = checks.getAuthorizedResourceIdsByType();

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnIdsGroupedByResourceTypeForSingleAuthorization() {
      // given
      final var authorization =
          Authorization.of(
              builder ->
                  builder
                      .processDefinition()
                      .readUserTask()
                      .resourceIds(List.of("pd-1", "pd-2", "pd-2", "pd-N")));
      final var condition = AuthorizationConditions.single(authorization);
      final var checks =
          ResourceAccessChecks.of(AuthorizationCheck.enabled(condition), TenantCheck.disabled());

      // when
      final var result = checks.getAuthorizedResourceIdsByType();

      // then
      assertThat(result)
          .containsOnlyKeys(AuthorizationResourceType.PROCESS_DEFINITION.name())
          .extractingByKey(AuthorizationResourceType.PROCESS_DEFINITION.name(), as(LIST))
          .containsExactly("pd-1", "pd-2", "pd-N");
    }

    @Test
    void shouldMergeIdsForSameResourceTypeFromMultipleAuthorizationsWithoutDuplicates() {
      // given
      final var first =
          Authorization.of(
              builder ->
                  builder.processDefinition().readUserTask().resourceIds(List.of("pd-1", "pd-2")));
      final var second =
          Authorization.of(
              builder ->
                  builder
                      .processDefinition()
                      .readProcessDefinition() // different permission
                      .resourceIds(List.of(/* duplicate */ "pd-2", "pd-3", "pd-4")));
      final var condition = AuthorizationConditions.anyOf(first, second);
      final var checks =
          ResourceAccessChecks.of(AuthorizationCheck.enabled(condition), TenantCheck.disabled());

      final var result = checks.getAuthorizedResourceIdsByType();

      assertThat(result)
          .containsOnlyKeys(AuthorizationResourceType.PROCESS_DEFINITION.name())
          .extractingByKey(AuthorizationResourceType.PROCESS_DEFINITION.name(), as(LIST))
          .containsExactly("pd-1", "pd-2", "pd-3", "pd-4");
    }

    @Test
    void shouldGroupIdsByDifferentResourceTypes() {
      // given
      final var processDefAuth =
          Authorization.of(
              builder -> builder.processDefinition().readUserTask().resourceId("pd-1"));
      final var userTaskAuth =
          Authorization.of(
              builder -> builder.userTask().read().resourceIds(List.of("ut-1", "ut-2")));
      final var condition = AuthorizationConditions.anyOf(processDefAuth, userTaskAuth);
      final var checks =
          ResourceAccessChecks.of(AuthorizationCheck.enabled(condition), TenantCheck.disabled());

      // when
      final var result = checks.getAuthorizedResourceIdsByType();

      // then
      assertThat(result)
          .containsOnlyKeys(
              AuthorizationResourceType.PROCESS_DEFINITION.name(),
              AuthorizationResourceType.USER_TASK.name());
      assertThat(result.get(AuthorizationResourceType.PROCESS_DEFINITION.name()))
          .containsExactly("pd-1");
      assertThat(result.get(AuthorizationResourceType.USER_TASK.name()))
          .containsExactly("ut-1", "ut-2");
    }

    @Test
    void shouldIgnoreAuthorizationsWithoutIds() {
      // given
      final var withIds =
          Authorization.of(
              builder -> builder.processDefinition().readUserTask().resourceId("pd-1"));
      final var withoutIds = Authorization.of(builder -> builder.userTask().read());
      final var condition = AuthorizationConditions.anyOf(withIds, withoutIds);
      final var checks =
          ResourceAccessChecks.of(AuthorizationCheck.enabled(condition), TenantCheck.disabled());

      // when
      final var result = checks.getAuthorizedResourceIdsByType();

      // then
      assertThat(result)
          .containsOnlyKeys(AuthorizationResourceType.PROCESS_DEFINITION.name())
          .extractingByKey(AuthorizationResourceType.PROCESS_DEFINITION.name(), as(LIST))
          .containsExactly("pd-1");
    }

    @Test
    void shouldIgnoreAuthorizationsWithoutResourceTypeIds() {
      // given
      final var withoutResourceType =
          Authorization.of(builder -> builder.resourceIds(List.of("x-1", "x-2")));
      final var condition = AuthorizationConditions.single(withoutResourceType);
      final var checks =
          ResourceAccessChecks.of(AuthorizationCheck.enabled(condition), TenantCheck.disabled());

      // when
      final var result = checks.getAuthorizedResourceIdsByType();

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class HasAnyResourceId {

    @Test
    void shouldReturnTrueWhenAuthorizationDisabled() {
      // given - authorization check disabled
      final var checks = ResourceAccessChecks.disabled();

      // when - then - since disabled, they have access to any resource
      assertThat(checks.authorizationCheck().hasAnyResourceAccess()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenAuthorizationConditionIsNull() {
      // given
      final var checks =
          ResourceAccessChecks.of(
              AuthorizationCheck.enabled((AuthorizationCondition) null), TenantCheck.disabled());

      // when - then
      assertThat(checks.authorizationCheck().hasAnyResourceAccess()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenNoResourceIdsProvided() {
      // given
      final var authorization = Authorization.of(b -> b.processDefinition().readUserTask());
      final var checks =
          ResourceAccessChecks.of(
              AuthorizationCheck.enabled(authorization), TenantCheck.disabled());

      // when - then
      assertThat(checks.authorizationCheck().hasAnyResourceAccess()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenAnyAuthorizationHasIds() {
      // given
      final var withoutIds = Authorization.of(b -> b.processDefinition().readUserTask());
      final var withIds =
          Authorization.of(b -> b.userTask().read().resourceIds(List.of("ut-1", "ut-2")));
      final var condition = AuthorizationConditions.anyOf(withoutIds, withIds);
      final var checks =
          ResourceAccessChecks.of(AuthorizationCheck.enabled(condition), TenantCheck.disabled());

      // when - then
      assertThat(checks.authorizationCheck().hasAnyResourceAccess()).isTrue();
    }
  }
}
