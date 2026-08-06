/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.EntityType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthenticatedOwnerIdsUtilTest {

  @Test
  void shouldCollectUserAndGroupOwnerIds() {
    // given
    final var authentication =
        CamundaAuthentication.of(b -> b.user("foo").group("groupId").tenant("<default>"));

    // when
    final var ownerIds = AuthenticatedOwnerIdsUtil.collect(authentication);

    // then — tenant membership is not an owner type; only user/group are reachable
    assertThat(ownerIds)
        .isEqualTo(
            Map.of(
                EntityType.USER, Set.of("foo"),
                EntityType.GROUP, Set.of("groupId")));
  }

  @Test
  void shouldCollectRolesAndMappingRulesInAdditionToDirectGrants() {
    // given
    final var authentication =
        CamundaAuthentication.of(
            b -> b.user("foo").role("roleId").mappingRule("mappingRuleId").group("groupId"));

    // when
    final var ownerIds = AuthenticatedOwnerIdsUtil.collect(authentication);

    // then
    assertThat(ownerIds)
        .isEqualTo(
            Map.of(
                EntityType.USER, Set.of("foo"),
                EntityType.GROUP, Set.of("groupId"),
                EntityType.ROLE, Set.of("roleId"),
                EntityType.MAPPING_RULE, Set.of("mappingRuleId")));
  }

  @Test
  void shouldCollectClientIdForClientCredentialsAuthentication() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.clientId("myClient"));

    // when
    final var ownerIds = AuthenticatedOwnerIdsUtil.collect(authentication);

    // then
    assertThat(ownerIds).isEqualTo(Map.of(EntityType.CLIENT, Set.of("myClient")));
  }

  @Test
  void shouldReturnEmptyMapForAnonymousAuthentication() {
    // given
    final var authentication = CamundaAuthentication.anonymous();

    // when
    final var ownerIds = AuthenticatedOwnerIdsUtil.collect(authentication);

    // then
    assertThat(ownerIds).isEmpty();
  }

  @Test
  void shouldReturnEmptyMapForAuthenticationWithNoIdentity() {
    // given — no user, client, group, role, or mapping rule
    final var authentication = CamundaAuthentication.none();

    // when
    final var ownerIds = AuthenticatedOwnerIdsUtil.collect(authentication);

    // then
    assertThat(ownerIds).isEmpty();
  }
}
