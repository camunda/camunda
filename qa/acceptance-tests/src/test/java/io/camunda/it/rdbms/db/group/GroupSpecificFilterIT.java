/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.group;

import static io.camunda.it.rdbms.db.fixtures.GroupFixtures.createAndSaveGroup;
import static io.camunda.it.rdbms.db.fixtures.GroupFixtures.createAndSaveRandomGroups;
import static io.camunda.it.rdbms.db.fixtures.GroupFixtures.createRandomized;
import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveTenant;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.GroupDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.GroupFixtures;
import io.camunda.it.rdbms.db.fixtures.GroupMemberFixtures;
import io.camunda.it.rdbms.db.fixtures.TenantFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.sort.GroupSort;
import io.camunda.zeebe.protocol.record.value.EntityType;
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
@TestPropertySource(
    properties = {"spring.liquibase.enabled=false", "camunda.data.secondary-storage.type=rdbms"})
public class GroupSpecificFilterIT {

  @Autowired private RdbmsService rdbmsService;

  @Autowired private GroupDbReader groupReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @Test
  public void shouldFilterGroupsForTenant() {
    final var tenant = TenantFixtures.createRandomized(b -> b);
    final var anotherTenant = TenantFixtures.createRandomized(b -> b);
    createAndSaveTenant(rdbmsWriter, tenant);
    createAndSaveTenant(rdbmsWriter, anotherTenant);

    final var group1 = createRandomized(b -> b);
    final var group2 = createRandomized(b -> b);
    final var group3 = createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriter, group1);
    createAndSaveGroup(rdbmsWriter, group2);
    createAndSaveGroup(rdbmsWriter, group3);
    addGroupToTenant(tenant.tenantId(), group1.groupId());
    addGroupToTenant(anotherTenant.tenantId(), group2.groupId());
    addGroupToTenant(anotherTenant.tenantId(), group3.groupId());

    final var groups =
        groupReader.search(
            new GroupQuery(
                new GroupFilter.Builder().tenantId(tenant.tenantId()).build(),
                GroupSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));
    assertThat(groups.total()).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("shouldFindWithSpecificFilterParameters")
  public void shouldFindWithSpecificFilter(final GroupFilter filter) {
    createAndSaveRandomGroups(rdbmsWriter);
    createAndSaveGroup(
        rdbmsWriter,
        GroupFixtures.createRandomized(
            b -> b.groupId("groupId").groupKey(1337L).name("Group 1337")));
    GroupMemberFixtures.createAndSaveRandomGroupMember(
        rdbmsWriter,
        b -> b.groupId("groupId").entityId("entityId").entityType(EntityType.USER.name()));

    final var searchResult =
        groupReader.search(
            new GroupQuery(
                filter, GroupSort.of(b -> b), SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().groupKey()).isEqualTo(1337L);
  }

  static List<GroupFilter> shouldFindWithSpecificFilterParameters() {
    return List.of(
        new GroupFilter.Builder().groupKey(1337L).build(),
        new GroupFilter.Builder().name("Group 1337").build(),
        new GroupFilter.Builder().memberId("entityId").childMemberType(EntityType.USER).build());
  }

  private void addGroupToTenant(final String tenantId, final String entityId) {
    rdbmsWriter.getTenantWriter().addMember(new TenantMemberDbModel(tenantId, entityId, "GROUP"));
    rdbmsWriter.flush();
  }
}
