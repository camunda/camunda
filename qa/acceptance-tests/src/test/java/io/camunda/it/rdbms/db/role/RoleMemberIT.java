/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.role;

import static io.camunda.security.api.model.authz.AuthorizationResourceType.ROLE;
import static io.camunda.security.api.model.authz.PermissionType.READ;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.RoleMemberDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.RoleFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.RoleMemberFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.sort.RoleMemberSort;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class RoleMemberIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldFindRoleMember(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleMemberDbReader reader = rdbmsService.getRoleMemberReader();

    RoleFixtures.createAndSaveRandomRoles(rdbmsWriters, b -> b);
    final var role = RoleFixtures.createAndSaveRole(rdbmsWriters, b -> b);
    final var role2 = RoleFixtures.createAndSaveRole(rdbmsWriters, b -> b);
    addUserToRole(rdbmsWriters, role.roleId(), "user-1");
    addUserToRole(rdbmsWriters, role.roleId(), "user-2");
    addUserToRole(rdbmsWriters, role2.roleId(), "user-3");
    addUserToRole(rdbmsWriters, role2.roleId(), "user-4");

    final var searchResult =
        reader.search(
            new RoleMemberQuery(
                RoleMemberFilter.of(b -> b.memberType(EntityType.USER).roleId(role.roleId())),
                RoleMemberSort.of(b -> b),
                SearchQueryPage.of(b -> b)),
            ResourceAccessChecks.disabled());

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(RoleMemberEntity::id))
        .contains("user-1", "user-2");
  }

  @TestTemplate
  public void shouldFindRoleMemberByMemberIdLike(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleMemberDbReader reader = rdbmsService.getRoleMemberReader();

    // given
    final var role = RoleFixtures.createAndSaveRole(rdbmsWriters, b -> b);
    addUserToRole(rdbmsWriters, role.roleId(), "jonny");
    addUserToRole(rdbmsWriters, role.roleId(), "mrjonas");
    addUserToRole(rdbmsWriters, role.roleId(), "jane");
    addUserToRole(rdbmsWriters, role.roleId(), "mark");

    // when
    final var searchResult =
        reader.search(
            new RoleMemberQuery(
                RoleMemberFilter.of(
                    b ->
                        b.memberType(EntityType.USER)
                            .roleId(role.roleId())
                            .memberIdOperations(List.of(Operation.like("*jo*")))),
                RoleMemberSort.of(b -> b),
                SearchQueryPage.of(b -> b)),
            ResourceAccessChecks.disabled());

    // then
    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items().stream().map(RoleMemberEntity::id))
        .containsExactlyInAnyOrder("jonny", "mrjonas");
  }

  @TestTemplate
  public void shouldOnlyFindMembersOfAuthorizedRoles(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final RoleMemberDbReader reader = rdbmsService.getRoleMemberReader();

    // given
    final var roleA = RoleFixtures.createAndSaveRole(rdbmsWriters, b -> b);
    final var roleB = RoleFixtures.createAndSaveRole(rdbmsWriters, b -> b);
    addUserToRole(rdbmsWriters, roleA.roleId(), "member-a-" + roleA.roleId());
    addUserToRole(rdbmsWriters, roleB.roleId(), "member-b-" + roleB.roleId());
    final var accessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(
                    a ->
                        a.resourceType(ROLE)
                            .permissionType(READ)
                            .resourceIds(List.of(roleA.roleId())))),
            TenantCheck.disabled());

    // when
    final var ofUnauthorizedRole =
        reader.search(
            new RoleMemberQuery(
                RoleMemberFilter.of(b -> b.roleId(roleB.roleId())),
                RoleMemberSort.of(b -> b),
                SearchQueryPage.of(b -> b)),
            accessChecks);
    final var ofAuthorizedRole =
        reader.search(
            new RoleMemberQuery(
                RoleMemberFilter.of(b -> b.roleId(roleA.roleId())),
                RoleMemberSort.of(b -> b),
                SearchQueryPage.of(b -> b)),
            accessChecks);
    final var unfiltered =
        reader.search(
            new RoleMemberQuery(
                RoleMemberFilter.of(b -> b), RoleMemberSort.of(b -> b), SearchQueryPage.of(b -> b)),
            accessChecks);

    // then
    assertThat(ofUnauthorizedRole.total()).isZero();
    assertThat(ofUnauthorizedRole.items()).isEmpty();
    assertThat(ofAuthorizedRole.items().stream().map(RoleMemberEntity::id))
        .containsExactly("member-a-" + roleA.roleId());
    assertThat(unfiltered.items().stream().map(RoleMemberEntity::id))
        .containsExactly("member-a-" + roleA.roleId());
    assertThat(unfiltered.total()).isEqualTo(1);
  }

  private void addUserToRole(
      final RdbmsWriters rdbmsWriters, final String roleId, final String entityId) {
    rdbmsWriters.getRoleWriter().addMember(new RoleMemberDbModel(roleId, entityId, "USER"));
    rdbmsWriters.flush();
  }
}
