/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.role;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.GroupFixtures.createAndSaveGroup;
import static io.camunda.it.rdbms.db.fixtures.RoleFixtures.createAndSaveRandomRolesWithMembers;
import static io.camunda.it.rdbms.db.fixtures.RoleFixtures.createAndSaveRole;
import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveTenant;
import static io.camunda.it.rdbms.db.fixtures.UserFixtures.createAndSaveUser;
import static io.camunda.zeebe.protocol.record.value.EntityType.ROLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.RoleDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.GroupFixtures;
import io.camunda.it.rdbms.db.fixtures.RoleFixtures;
import io.camunda.it.rdbms.db.fixtures.RoleMemberFixtures;
import io.camunda.it.rdbms.db.fixtures.TenantFixtures;
import io.camunda.it.rdbms.db.fixtures.UserFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.sort.RoleSort;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@DataJdbcTest
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@TestPropertySource(properties = {"spring.liquibase.enabled=false", "camunda.database.type=rdbms"})
public class RoleSpecificFilterIT {
  public static final String ROLE_ID = "roleId";
  public static final long ROLE_KEY = 1337L;
  public static final String ROLE_NAME = "Role 1337";
  public static final String ENTITY_ID = "entityId";
  public static final EntityType ENTITY_TYPE = EntityType.USER;

  @Autowired private RdbmsService rdbmsService;

  @Autowired private RoleDbReader roleReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @Test
  public void shouldFilterRolesForGroup() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var anotherRoleId = Strings.newRandomValidIdentityId();
    final var group = GroupFixtures.createRandomized(b -> b);
    final var userId = Strings.newRandomValidIdentityId();
    createAndSaveUser(
        rdbmsWriter,
        UserFixtures.createRandomized(
            b -> b.username(userId).name("User 1337").password("password")));
    createAndSaveGroup(rdbmsWriter, group);
    createAndSaveRole(
        rdbmsWriter, RoleFixtures.createRandomized(b -> b.roleId(roleId).name("Role 1337")));
    createAndSaveRole(
        rdbmsWriter,
        RoleFixtures.createRandomized(b -> b.roleId(anotherRoleId).name("Another Role 1337")));

    addUserToRole(roleId, userId);
    addGroupToRole(roleId, group.groupId());
    addGroupToRole(anotherRoleId, group.groupId());

    final var roles =
        roleReader.search(
            new RoleQuery(
                new RoleFilter.Builder()
                    .memberId(group.groupId())
                    .childMemberType(EntityType.GROUP)
                    .build(),
                RoleSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(roles.total()).isEqualTo(2);
  }

  @Test
  public void shouldFilterRolesForTenant() {
    final var tenant = TenantFixtures.createRandomized(b -> b);
    final var anotherTenant = TenantFixtures.createRandomized(b -> b);
    createAndSaveTenant(rdbmsWriter, tenant);
    createAndSaveTenant(rdbmsWriter, anotherTenant);

    final var roleId1 = nextStringId();
    final var roleId2 = nextStringId();
    final var roleId3 = nextStringId();
    Arrays.asList(roleId1, roleId2, roleId3)
        .forEach(
            roleId ->
                RoleFixtures.createAndSaveRole(
                    rdbmsWriter, RoleFixtures.createRandomized(r -> r.roleId(roleId))));

    addRoleToTenant(tenant.tenantId(), roleId1);
    addRoleToTenant(anotherTenant.tenantId(), roleId2);
    addRoleToTenant(anotherTenant.tenantId(), roleId3);

    final var roles =
        roleReader.search(
            new RoleQuery(
                new RoleFilter.Builder().tenantId(tenant.tenantId()).build(),
                RoleSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(roles.total()).isEqualTo(1);
    assertThat(roles.items()).hasSize(1).extracting(RoleEntity::roleId).containsOnly(roleId1);
  }

  @ParameterizedTest
  @MethodSource("shouldFindWithSpecificFilterParameters")
  public void shouldFindWithSpecificFilter(final RoleFilter filter) {
    createAndSaveRandomRolesWithMembers(rdbmsWriter);
    createAndSaveRole(
        rdbmsWriter,
        RoleFixtures.createRandomized(b -> b.roleId(ROLE_ID).roleKey(ROLE_KEY).name(ROLE_NAME)));
    RoleMemberFixtures.createAndSaveRandomRoleMember(
        rdbmsWriter, b -> b.roleId(ROLE_ID).entityId(ENTITY_ID).entityType(ENTITY_TYPE.name()));

    final var searchResult =
        roleReader.search(
            new RoleQuery(filter, RoleSort.of(b -> b), SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().roleKey()).isEqualTo(ROLE_KEY);
  }

  static List<RoleFilter> shouldFindWithSpecificFilterParameters() {
    return List.of(
        new RoleFilter.Builder().roleId(ROLE_ID).build(),
        new RoleFilter.Builder().name(ROLE_NAME).build(),
        new RoleFilter.Builder().memberId(ENTITY_ID).childMemberType(ENTITY_TYPE).build());
  }

  private void addGroupToRole(final String roleId, final String entityId) {
    rdbmsWriter.getRoleWriter().addMember(new RoleMemberDbModel(roleId, entityId, "GROUP"));
    rdbmsWriter.flush();
  }

  private void addUserToRole(final String roleId, final String entityId) {
    rdbmsWriter.getRoleWriter().addMember(new RoleMemberDbModel(roleId, entityId, "USER"));
    rdbmsWriter.flush();
  }

  private void addRoleToTenant(final String tenantId, final String roleId) {
    rdbmsWriter.getTenantWriter().addMember(new TenantMemberDbModel(tenantId, roleId, ROLE.name()));
    rdbmsWriter.flush();
  }
}
