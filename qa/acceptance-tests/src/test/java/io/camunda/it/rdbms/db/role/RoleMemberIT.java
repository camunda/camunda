/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.role;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.RoleMemberDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.RoleFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.filter.RoleMemberFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.sort.RoleMemberSort;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.time.OffsetDateTime;
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
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final RoleMemberDbReader reader = rdbmsService.getRoleMemberReader();

    RoleFixtures.createAndSaveRandomRoles(rdbmsWriter, b -> b);
    final var role = RoleFixtures.createAndSaveRole(rdbmsWriter, b -> b);
    final var role2 = RoleFixtures.createAndSaveRole(rdbmsWriter, b -> b);
    addUserToRole(rdbmsWriter, role.roleId(), "user-1");
    addUserToRole(rdbmsWriter, role.roleId(), "user-2");
    addUserToRole(rdbmsWriter, role2.roleId(), "user-3");
    addUserToRole(rdbmsWriter, role2.roleId(), "user-4");

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

  private void addUserToRole(
      final RdbmsWriter rdbmsWriter, final String roleId, final String entityId) {
    rdbmsWriter.getRoleWriter().addMember(new RoleMemberDbModel(roleId, entityId, "USER"));
    rdbmsWriter.flush();
  }
}
