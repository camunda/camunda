/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.mappingrules;

import static io.camunda.it.rdbms.db.fixtures.GroupFixtures.createAndSaveGroup;
import static io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures.*;
import static io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures.createAndSaveMappingRule;
import static io.camunda.it.rdbms.db.fixtures.RoleFixtures.createAndSaveRole;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.MappingRuleDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.GroupFixtures;
import io.camunda.it.rdbms.db.fixtures.MappingRuleFixtures;
import io.camunda.it.rdbms.db.fixtures.RoleFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.sort.MappingRuleSort;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
public class MappingRuleSpecificFilterIT {

  @Autowired private RdbmsService rdbmsService;

  @Autowired private MappingRuleDbReader mappingRuleReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @Test
  public void shouldFilterMappingRulesForGroup() {
    // Create and save a mapping rule
    final var mappingRule1 = MappingRuleFixtures.createRandomized();
    final var mappingRule2 = MappingRuleFixtures.createRandomized();
    final var mappingRule3 = MappingRuleFixtures.createRandomized();
    createAndSaveMappingRule(rdbmsWriter, mappingRule1);
    createAndSaveMappingRule(rdbmsWriter, mappingRule2);
    createAndSaveMappingRule(rdbmsWriter, mappingRule3);

    final var group = GroupFixtures.createRandomized(b -> b);
    final var anotherGroup = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriter, group);
    createAndSaveGroup(rdbmsWriter, anotherGroup);

    assignMappingRuleToGroup(group.groupId(), mappingRule1.mappingRuleId());
    assignMappingRuleToGroup(group.groupId(), mappingRule2.mappingRuleId());
    assignMappingRuleToGroup(anotherGroup.groupId(), mappingRule3.mappingRuleId());

    final var mappingRules =
        mappingRuleReader.search(
            new MappingRuleQuery(
                new MappingRuleFilter.Builder().groupId(group.groupId()).build(),
                MappingRuleSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(mappingRules.total()).isEqualTo(2);
  }

  @Test
  public void shouldFilterMappingRulesForRole() {
    final var role = RoleFixtures.createRandomized(b -> b);
    final var anotherRole = RoleFixtures.createRandomized(b -> b);
    createAndSaveRole(rdbmsWriter, role);
    createAndSaveRole(rdbmsWriter, anotherRole);

    final var mappingRuleId1 = nextStringId();
    final var mappingRuleId2 = nextStringId();
    final var mappingRuleId3 = nextStringId();
    Arrays.asList(mappingRuleId1, mappingRuleId2, mappingRuleId3)
        .forEach(
            mappingId ->
                createAndSaveMappingRule(
                    rdbmsWriter, createRandomized(m -> m.mappingRuleId(mappingId))));

    assignMappingRuleToRole(role.roleId(), mappingRuleId1);
    assignMappingRuleToRole(anotherRole.roleId(), mappingRuleId2);
    assignMappingRuleToRole(anotherRole.roleId(), mappingRuleId3);

    final var mappingRules =
        mappingRuleReader.search(
            new MappingRuleQuery(
                new MappingRuleFilter.Builder().roleId(role.roleId()).build(),
                MappingRuleSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(mappingRules.total()).isEqualTo(1);
    assertThat(mappingRules.items())
        .hasSize(1)
        .extracting(MappingRuleEntity::mappingRuleId)
        .containsOnly(mappingRuleId1);
  }

  private void assignMappingRuleToGroup(final String groupId, final String mappingRuleId) {
    rdbmsWriter
        .getGroupWriter()
        .addMember(new GroupMemberDbModel(groupId, mappingRuleId, MAPPING_RULE.name()));
    rdbmsWriter.flush();
  }

  private void assignMappingRuleToRole(final String roledId, final String mappingRuleId) {
    rdbmsWriter
        .getRoleWriter()
        .addMember(new RoleMemberDbModel(roledId, mappingRuleId, MAPPING_RULE.name()));
    rdbmsWriter.flush();
  }
}
