/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccessStoreDocumentTest {

  @Test
  void shouldIdentifyRolesAndMembersLikeTheOrchestrationCluster() {
    // given
    final RoleMemberDto member = new RoleMemberDto(EntityType.MAPPING_RULE, "finance");

    // when / then
    assertThat(RoleDto.documentId("analysts")).isEqualTo("role-analysts");
    assertThat(member.documentId("analysts")).isEqualTo("member-analysts-mapping_rule-finance");
  }

  @Test
  void shouldGiveMembershipsWithHyphenatedIdsDistinctDocumentIds() {
    // given two memberships that concatenate to the same string
    final String first = new RoleMemberDto(EntityType.USER, "b-user-c").documentId("a");
    final String second = new RoleMemberDto(EntityType.USER, "c").documentId("a-user-b");

    // when / then
    assertThat(first).isNotEqualTo(second);
    assertThat(new RoleMemberDto(EntityType.USER, "b%2Dc").documentId("a"))
        .isNotEqualTo(new RoleMemberDto(EntityType.USER, "b-c").documentId("a"));
  }

  @Test
  void shouldMarkRolesAsJoinParentsAndMembersAsChildren() {
    // when
    final Map<String, Object> role = new RoleDto("analysts", "Analysts", null).toDocument();
    final Map<String, Object> member =
        new RoleMemberDto(EntityType.USER, "demo").toDocument("analysts");

    // then
    assertThat(role).containsEntry("join", Map.of("name", "role"));
    assertThat(member)
        .containsEntry("join", Map.of("name", "member", "parent", "role-analysts"))
        .containsEntry("memberType", "USER")
        .containsEntry("memberId", "demo");
  }

  @Test
  void shouldStoreTheResourceMatcherAsNumber() {
    // given
    final AuthorizationDto authorization =
        new AuthorizationDto(
            "1",
            AuthorizationOwnerType.ROLE,
            "analysts",
            AuthorizationResourceType.COMPONENT,
            AuthorizationResourceMatcher.ANY,
            "*",
            null,
            Set.of(PermissionType.ACCESS));

    // when
    final Map<String, Object> document = authorization.toDocument();

    // then
    assertThat(document.get("resourceMatcher")).isEqualTo(AuthorizationResourceMatcher.ANY.value());
    assertThat(AuthorizationDto.fromDocument(document)).isEqualTo(authorization);
  }

  @Test
  void shouldReadTheResourceMatcherAsTheDatabaseReturnsIt() {
    // given a document as deserialized from the database, with the matcher as an Integer
    final Map<String, Object> document =
        new AuthorizationDto(
                "1",
                AuthorizationOwnerType.USER,
                "demo",
                AuthorizationResourceType.COMPONENT,
                AuthorizationResourceMatcher.ID,
                "optimize",
                null,
                Set.of(PermissionType.ACCESS))
            .toDocument();
    document.put("resourceMatcher", (int) AuthorizationResourceMatcher.ID.value());

    // when / then
    assertThat(AuthorizationDto.fromDocument(document).resourceMatcher())
        .isEqualTo(AuthorizationResourceMatcher.ID);
  }
}
