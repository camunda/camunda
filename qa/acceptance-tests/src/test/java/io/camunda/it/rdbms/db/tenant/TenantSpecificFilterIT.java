/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.tenant;

import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveRandomTenants;
import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveTenant;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.TenantDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.TenantFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.sort.TenantSort;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@DataJdbcTest
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@TestPropertySource(
    properties = {"spring.liquibase.enabled=false", "camunda.data.secondary-storage.type=rdbms"})
public class TenantSpecificFilterIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private RdbmsService rdbmsService;

  @Autowired private TenantDbReader tenantReader;

  private RdbmsWriters rdbmsWriters;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriters = rdbmsService.createWriter(0L);
  }

  @Test
  public void shouldFindTenantWithChildMemberWithMemberIdsByType() {
    createAndSaveRandomTenants(rdbmsWriters, b -> b);
    final var tenant = createAndSaveTenant(rdbmsWriters, b -> b);
    addGroupToTenant(tenant.tenantId(), "group-1");
    addGroupToTenant(tenant.tenantId(), "group-2");
    addUserToTenant(tenant.tenantId(), "user-1");
    addUserToTenant(tenant.tenantId(), "user-2");

    final var searchResult =
        tenantReader.search(
            new TenantQuery(
                TenantFilter.of(b -> b.memberIdsByType(Map.of(EntityType.USER, Set.of("user-1")))),
                TenantSort.of(b -> b),
                SearchQueryPage.of(b -> b)));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().key()).isEqualTo(tenant.tenantKey());
  }

  @Test
  public void shouldFindTenantWithChildMemberWithChildMemberId() {
    createAndSaveRandomTenants(rdbmsWriters, b -> b);
    final var tenant = createAndSaveTenant(rdbmsWriters, b -> b);
    addGroupToTenant(tenant.tenantId(), "group-1");
    addGroupToTenant(tenant.tenantId(), "group-2");
    addUserToTenant(tenant.tenantId(), "user-1");
    addUserToTenant(tenant.tenantId(), "user-2");

    final var searchResult =
        tenantReader.search(
            new TenantQuery(
                TenantFilter.of(
                    b -> b.childMemberType(EntityType.USER).memberIds(Set.of("user-1"))),
                TenantSort.of(b -> b),
                SearchQueryPage.of(b -> b)));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().key()).isEqualTo(tenant.tenantKey());
  }

  @ParameterizedTest
  @MethodSource("shouldFindTenantWithSpecificFilterParameters")
  public void shouldFindTenantWithSpecificFilter(final TenantFilter filter) {
    createAndSaveRandomTenants(rdbmsWriters, b -> b);
    createAndSaveTenant(
        rdbmsWriters,
        TenantFixtures.createRandomized(
            b -> b.tenantKey(42L).tenantId("tenant-42").name("Tenant 42")));

    final var searchResult =
        tenantReader.search(
            new TenantQuery(
                filter, TenantSort.of(b -> b), SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().key()).isEqualTo(42L);
  }

  static List<TenantFilter> shouldFindTenantWithSpecificFilterParameters() {
    return List.of(
        TenantFilter.of(b -> b.key(42L)),
        TenantFilter.of(b -> b.tenantId("tenant-42")),
        TenantFilter.of(b -> b.name("Tenant 42")));
  }

  private void addGroupToTenant(final String tenantId, final String entityId) {
    rdbmsWriters.getTenantWriter().addMember(new TenantMemberDbModel(tenantId, entityId, "GROUP"));
    rdbmsWriters.flush();
  }

  private void addUserToTenant(final String tenantId, final String entityId) {
    rdbmsWriters.getTenantWriter().addMember(new TenantMemberDbModel(tenantId, entityId, "USER"));
    rdbmsWriters.flush();
  }
}
