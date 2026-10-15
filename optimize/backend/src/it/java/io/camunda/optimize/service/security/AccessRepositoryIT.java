/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.AccessMappingRuleDto;
import io.camunda.optimize.dto.optimize.query.AccessRoleDto;
import io.camunda.optimize.rest.security.csl.OptimizeMembershipAdapter;
import io.camunda.optimize.service.db.repository.AccessRepository;
import io.camunda.optimize.service.db.repository.TenantMembershipRepository;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * POC: runs the mapping rule and role stores, and the membership port on top of them, against the
 * real {@code access-mapping-rule} and {@code access-role} indices, for Elasticsearch and
 * OpenSearch.
 */
public class AccessRepositoryIT extends AbstractBrokerlessZeebeCCSMIT {

  private AccessRepository repository;
  private OptimizeMembershipAdapter adapter;

  @BeforeEach
  void setUp() {
    repository = embeddedOptimizeExtension.getBean(AccessRepository.class);
    adapter =
        new OptimizeMembershipAdapter(
            embeddedOptimizeExtension.getBean(TenantMembershipRepository.class),
            repository,
            Duration.ZERO);
  }

  @Test
  void shouldInsertOnlyWhenMissing() {
    // given
    final String id = IdGenerator.getNextId();
    final var first = new AccessMappingRuleDto(id, "groups", "a");
    final var second = new AccessMappingRuleDto(id, "groups", "b");

    // when
    final boolean firstInserted = repository.createMappingRuleIfMissing(first);
    final boolean secondInserted = repository.createMappingRuleIfMissing(second);

    // then the existing row is kept untouched
    assertThat(firstInserted).isTrue();
    assertThat(secondInserted).isFalse();
    assertThat(repository.findAllMappingRules())
        .filteredOn(r -> id.equals(r.getId()))
        .singleElement()
        .satisfies(r -> assertThat(r.getClaimValue()).isEqualTo("a"));
  }

  @Test
  void shouldReplaceAndDeleteRoles() {
    // given
    final String id = IdGenerator.getNextId();
    final var role = new AccessRoleDto(id, "Original");
    role.setMappingRuleIds(List.of("r1"));
    repository.saveRole(role);

    // when
    role.setName("Renamed");
    role.setMappingRuleIds(List.of("r1", "r2"));
    repository.saveRole(role);

    // then
    assertThat(repository.findAllRoles())
        .filteredOn(r -> id.equals(r.getId()))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.getName()).isEqualTo("Renamed");
              assertThat(r.getMappingRuleIds()).containsExactly("r1", "r2");
            });

    // when
    repository.deleteRole(id);

    // then
    assertThat(repository.findAllRoles()).noneMatch(r -> id.equals(r.getId()));
  }

  @Test
  void shouldResolveRolesFromStoredRulesAndTokenClaims() {
    // given a rule on a list claim and a role that references it
    final String ruleId = IdGenerator.getNextId();
    final String roleId = IdGenerator.getNextId();
    final String group = IdGenerator.getNextId();
    repository.saveMappingRule(new AccessMappingRuleDto(ruleId, "groups", group));
    final var role = new AccessRoleDto(roleId, "Role");
    role.setMappingRuleIds(List.of(ruleId));
    repository.saveRole(role);
    final var query =
        new MembershipQuery(
            Map.of("groups", List.of("other", group)), "someone", PrincipalType.USER);

    // when
    final List<String> ruleIds = adapter.mappingRuleIds(query);
    final List<String> roleIds = adapter.roleIds(query.withMappingRuleIds(ruleIds));

    // then
    assertThat(ruleIds).contains(ruleId);
    assertThat(roleIds).contains(roleId);
  }
}
