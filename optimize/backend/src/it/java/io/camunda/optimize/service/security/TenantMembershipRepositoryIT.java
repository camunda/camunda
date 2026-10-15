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
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto.MemberType;
import io.camunda.optimize.rest.security.csl.OptimizeMembershipAdapter;
import io.camunda.optimize.service.db.repository.TenantMembershipRepository;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * POC: runs the tenant-membership store and the CSL membership adapter on top of the real {@code
 * tenant-membership} index, for both Elasticsearch and OpenSearch.
 */
public class TenantMembershipRepositoryIT extends AbstractBrokerlessZeebeCCSMIT {

  private TenantMembershipRepository repository;

  @BeforeEach
  void setUp() {
    repository = embeddedOptimizeExtension.getBean(TenantMembershipRepository.class);
  }

  @Test
  void shouldAssignIdempotentlyAndListByTenant() {
    // given
    final String tenant = IdGenerator.getNextId();
    final String user = IdGenerator.getNextId();

    // when
    repository.assign(new TenantMembershipDto(tenant, MemberType.USER, user));
    repository.assign(new TenantMembershipDto(tenant, MemberType.USER, user));

    // then
    assertThat(repository.findByTenant(tenant))
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.getMemberType()).isEqualTo(MemberType.USER);
              assertThat(m.getMemberId()).isEqualTo(user);
            });
  }

  @Test
  void shouldNotMixMemberTypesWithTheSameId() {
    // given
    final String tenant = IdGenerator.getNextId();
    final String name = IdGenerator.getNextId();
    repository.assign(new TenantMembershipDto(tenant, MemberType.GROUP, name));

    // when / then
    assertThat(repository.findTenantIds(MemberType.GROUP, Set.of(name))).containsExactly(tenant);
    assertThat(repository.findTenantIds(MemberType.USER, Set.of(name))).isEmpty();
  }

  @Test
  void shouldRemoveAnAssignment() {
    // given
    final String tenant = IdGenerator.getNextId();
    final String user = IdGenerator.getNextId();
    repository.assign(new TenantMembershipDto(tenant, MemberType.USER, user));

    // when
    repository.unassign(tenant, MemberType.USER, user);

    // then
    assertThat(repository.findTenantIds(MemberType.USER, Set.of(user))).isEmpty();
  }

  @Test
  void shouldResolveTenantsForUserAndItsGroupsThroughTheMembershipPort() {
    // given
    final String direct = IdGenerator.getNextId();
    final String viaGroup = IdGenerator.getNextId();
    final String user = IdGenerator.getNextId();
    final String group = IdGenerator.getNextId();
    repository.assign(new TenantMembershipDto(direct, MemberType.USER, user));
    repository.assign(new TenantMembershipDto(viaGroup, MemberType.GROUP, group));
    final var adapter = new OptimizeMembershipAdapter(repository, null, java.time.Duration.ZERO);

    // when
    final List<String> tenantIds =
        adapter.tenantIds(
            new MembershipQuery(
                Map.of(), user, PrincipalType.USER, List.of(), List.of(group), List.of()));

    // then
    assertThat(tenantIds).containsExactlyInAnyOrder(direct, viaGroup);
  }
}
