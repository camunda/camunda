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
import io.camunda.optimize.dto.optimize.access.RoleDto;
import io.camunda.optimize.dto.optimize.access.RoleMemberDto;
import io.camunda.security.api.model.authz.EntityType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoleRepositoryIT extends AbstractBrokerlessZeebeCCSMIT {

  private RoleRepository roleRepository;

  @BeforeEach
  void setup() {
    roleRepository = embeddedOptimizeExtension.getBean(RoleRepository.class);
  }

  @Test
  void shouldReplaceRoleOnUpsert() {
    // given
    final String roleId = uniqueId("role");
    roleRepository.upsert(new RoleDto(roleId, "Analysts", null));

    // when
    roleRepository.upsert(new RoleDto(roleId, "Report analysts", "Can open Optimize"));

    // then
    assertThat(roleRepository.get(roleId))
        .contains(new RoleDto(roleId, "Report analysts", "Can open Optimize"));
    assertThat(roleRepository.getAll())
        .containsExactly(new RoleDto(roleId, "Report analysts", "Can open Optimize"));
  }

  @Test
  void shouldReturnEmptyForUnknownRole() {
    // when / then
    assertThat(roleRepository.get(uniqueId("role"))).isEmpty();
  }

  @Test
  void shouldNotListMembersAsRoles() {
    // given
    final String roleId = uniqueId("role");
    roleRepository.upsert(new RoleDto(roleId, "Analysts", null));
    roleRepository.addMember(roleId, new RoleMemberDto(EntityType.USER, uniqueId("user")));

    // when / then
    assertThat(roleRepository.getAll()).extracting(RoleDto::roleId).containsExactly(roleId);
  }

  @Test
  void shouldAddMembersOnce() {
    // given
    final String roleId = uniqueId("role");
    final RoleMemberDto user = new RoleMemberDto(EntityType.USER, uniqueId("user"));
    final RoleMemberDto group = new RoleMemberDto(EntityType.GROUP, uniqueId("group"));
    roleRepository.upsert(new RoleDto(roleId, "Analysts", null));

    // when
    roleRepository.addMember(roleId, user);
    roleRepository.addMember(roleId, group);
    roleRepository.addMember(roleId, user);

    // then
    assertThat(roleRepository.getMembers(roleId)).containsExactlyInAnyOrder(user, group);
  }

  @Test
  void shouldRemoveOnlyTheGivenMember() {
    // given
    final String roleId = uniqueId("role");
    final RoleMemberDto removed = new RoleMemberDto(EntityType.USER, uniqueId("user"));
    final RoleMemberDto kept = new RoleMemberDto(EntityType.CLIENT, uniqueId("client"));
    roleRepository.upsert(new RoleDto(roleId, "Analysts", null));
    roleRepository.addMember(roleId, removed);
    roleRepository.addMember(roleId, kept);

    // when
    roleRepository.removeMember(roleId, removed);
    roleRepository.removeMember(roleId, new RoleMemberDto(EntityType.USER, uniqueId("user")));

    // then
    assertThat(roleRepository.getMembers(roleId)).containsExactly(kept);
  }

  @Test
  void shouldKeepMembershipsApartWhoseIdsConcatenateAlike() {
    // given role "<p>" with user "x-user-y" and role "<p>-user-x" with user "y"
    final String roleId = uniqueId("role");
    final String otherRoleId = roleId + "-user-x";
    final RoleMemberDto member = new RoleMemberDto(EntityType.USER, "x-user-y");
    final RoleMemberDto otherMember = new RoleMemberDto(EntityType.USER, "y");
    roleRepository.upsert(new RoleDto(roleId, "Analysts", null));
    roleRepository.upsert(new RoleDto(otherRoleId, "Admins", null));
    roleRepository.addMember(roleId, member);
    roleRepository.addMember(otherRoleId, otherMember);

    // when
    roleRepository.removeMember(roleId, member);

    // then
    assertThat(roleRepository.getMembers(roleId)).isEmpty();
    assertThat(roleRepository.getMembers(otherRoleId)).containsExactly(otherMember);
  }

  @Test
  void shouldFindRolesOfMembersOfTheGivenType() {
    // given
    final String firstRoleId = uniqueId("role");
    final String secondRoleId = uniqueId("role");
    final String unrelatedRoleId = uniqueId("role");
    final String firstUser = uniqueId("user");
    final String secondUser = uniqueId("user");
    roleRepository.upsert(new RoleDto(firstRoleId, "Analysts", null));
    roleRepository.upsert(new RoleDto(secondRoleId, "Admins", null));
    roleRepository.upsert(new RoleDto(unrelatedRoleId, "Viewers", null));
    roleRepository.addMember(firstRoleId, new RoleMemberDto(EntityType.USER, firstUser));
    roleRepository.addMember(secondRoleId, new RoleMemberDto(EntityType.USER, secondUser));
    // same id, other member type: must not count as the user
    roleRepository.addMember(unrelatedRoleId, new RoleMemberDto(EntityType.CLIENT, firstUser));

    // when / then
    assertThat(roleRepository.getRoleIdsOfMembers(EntityType.USER, List.of(firstUser, secondUser)))
        .containsExactlyInAnyOrder(firstRoleId, secondRoleId);
    assertThat(roleRepository.getRoleIdsOfMembers(EntityType.GROUP, List.of(firstUser))).isEmpty();
    assertThat(roleRepository.getRoleIdsOfMembers(EntityType.USER, List.of())).isEmpty();
  }

  @Test
  void shouldDeleteRoleWithItsMembers() {
    // given
    final String deletedRoleId = uniqueId("role");
    final String keptRoleId = uniqueId("role");
    final String user = uniqueId("user");
    roleRepository.upsert(new RoleDto(deletedRoleId, "Analysts", null));
    roleRepository.upsert(new RoleDto(keptRoleId, "Admins", null));
    roleRepository.addMember(deletedRoleId, new RoleMemberDto(EntityType.USER, user));
    roleRepository.addMember(keptRoleId, new RoleMemberDto(EntityType.USER, user));

    // when
    roleRepository.delete(deletedRoleId);
    roleRepository.delete(uniqueId("role"));

    // then
    assertThat(roleRepository.get(deletedRoleId)).isEmpty();
    assertThat(roleRepository.getMembers(deletedRoleId)).isEmpty();
    assertThat(roleRepository.getRoleIdsOfMembers(EntityType.USER, List.of(user)))
        .containsExactly(keptRoleId);
  }

  private static String uniqueId(final String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }
}
