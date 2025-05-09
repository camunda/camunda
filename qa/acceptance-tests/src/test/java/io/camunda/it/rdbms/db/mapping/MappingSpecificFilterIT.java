/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.mapping;

import static io.camunda.it.rdbms.db.fixtures.GroupFixtures.createAndSaveGroup;
import static io.camunda.it.rdbms.db.fixtures.MappingFixtures.*;
import static io.camunda.it.rdbms.db.fixtures.MappingFixtures.createAndSaveMapping;
import static io.camunda.it.rdbms.db.fixtures.RoleFixtures.createAndSaveRole;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.MappingReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.GroupFixtures;
import io.camunda.it.rdbms.db.fixtures.MappingFixtures;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.RoleFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.filter.MappingFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.sort.MappingSort;
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
public class MappingSpecificFilterIT {

  @Autowired private RdbmsService rdbmsService;

  @Autowired private MappingReader mappingReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @Test
  public void shouldFilterMappingsForGroup() {
    // Create and save a mapping
    final var mapping1 = MappingFixtures.createRandomized();
    final var mapping2 = MappingFixtures.createRandomized();
    final var mapping3 = MappingFixtures.createRandomized();
    createAndSaveMapping(rdbmsService, mapping1);
    createAndSaveMapping(rdbmsService, mapping2);
    createAndSaveMapping(rdbmsService, mapping3);

    final var group = GroupFixtures.createRandomized(b -> b);
    final var anotherGroup = GroupFixtures.createRandomized(b -> b);
    createAndSaveGroup(rdbmsWriter, group);
    createAndSaveGroup(rdbmsWriter, anotherGroup);

    addMappingToGroup(group.groupId(), mapping1.mappingId());
    addMappingToGroup(group.groupId(), mapping2.mappingId());
    addMappingToGroup(anotherGroup.groupId(), mapping3.mappingId());

    final var mappings =
        mappingReader.search(
            new MappingQuery(
                new MappingFilter.Builder().groupId(group.groupId()).build(),
                MappingSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(mappings.total()).isEqualTo(2);
  }

  @Test
  public void shouldFilterMappingsForRole() {
    final var role = RoleFixtures.createRandomized(b -> b);
    final var anotherRole = RoleFixtures.createRandomized(b -> b);
    createAndSaveRole(rdbmsWriter, role);
    createAndSaveRole(rdbmsWriter, anotherRole);

    final var mappingId1 = nextStringId();
    final var mappingId2 = nextStringId();
    final var mappingId3 = nextStringId();
    Arrays.asList(mappingId1, mappingId2, mappingId3)
        .forEach(
            mappingId ->
                createAndSaveMapping(rdbmsWriter, createRandomized(m -> m.mappingId(mappingId))));

    addMappingToRole(role.roleId(), mappingId1);
    addMappingToRole(anotherRole.roleId(), mappingId2);
    addMappingToRole(anotherRole.roleId(), mappingId3);

    final var mappings =
        mappingReader.search(
            new MappingQuery(
                new MappingFilter.Builder().roleId(role.roleId()).build(),
                MappingSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(mappings.total()).isEqualTo(1);
    assertThat(mappings.items())
        .hasSize(1)
        .extracting(MappingEntity::mappingId)
        .containsOnly(mappingId1);
  }

  private void addMappingToGroup(final String groupId, final String mappingId) {
    rdbmsWriter.getGroupWriter().addMember(new GroupMemberDbModel(groupId, mappingId, "MAPPING"));
    rdbmsWriter.flush();
  }

  private void addMappingToRole(final String roledId, final String mappingId) {
    rdbmsWriter
        .getRoleWriter()
        .addMember(new RoleMemberDbModel(roledId, mappingId, MAPPING.name()));
    rdbmsWriter.flush();
  }
}
