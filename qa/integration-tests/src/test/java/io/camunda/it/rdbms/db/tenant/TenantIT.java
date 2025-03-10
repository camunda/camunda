/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.tenant;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveRandomTenants;
import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveTenant;
import static io.camunda.it.rdbms.db.fixtures.UserFixtures.createAndSaveUser;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.TenantReader;
import io.camunda.db.rdbms.read.service.UserReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.TenantDbModel;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.it.rdbms.db.fixtures.TenantFixtures;
import io.camunda.it.rdbms.db.fixtures.UserFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.sort.TenantSort;
import io.camunda.search.sort.UserSort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class TenantIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSaveAndFindTenantByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final TenantReader reader = rdbmsService.getTenantReader();

    final var tenant = createAndSaveTenant(rdbmsWriter);

    final var actual = reader.findOne(tenant.tenantId()).orElseThrow();
    compareTenant(actual, tenant);
  }

  @TestTemplate
  public void shouldFindByTenantId(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final TenantReader reader = rdbmsService.getTenantReader();

    final var tenant = createAndSaveTenant(rdbmsWriter);

    final var searchResult =
        reader.search(
            new TenantQuery(
                new TenantFilter.Builder().tenantId(tenant.tenantId()).build(),
                TenantSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareTenant(searchResult.items().getFirst(), tenant);
  }

  @TestTemplate
  public void shouldSaveAndUpdate(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final TenantReader tenantReader = rdbmsService.getTenantReader();

    final var tenantId = nextStringId();
    final var tenant = TenantFixtures.createRandomized(b -> b.tenantId(tenantId));
    createAndSaveTenant(rdbmsWriter, tenant);

    final var tenantUpdate = TenantFixtures.createRandomized(b -> b.tenantId(tenant.tenantId()));
    rdbmsWriter.getTenantWriter().update(tenantUpdate);
    rdbmsWriter.flush();

    final var instance = tenantReader.findOne(tenant.tenantId()).orElse(null);

    compareTenant(instance, tenantUpdate);
  }

  @TestTemplate
  public void shouldSaveAndDelete(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final TenantReader tenantReader = rdbmsService.getTenantReader();

    final var tenant = TenantFixtures.createRandomized(b -> b);
    createAndSaveTenant(rdbmsWriter, tenant);
    final var instance = tenantReader.findOne(tenant.tenantId()).orElse(null);
    compareTenant(instance, tenant);

    rdbmsWriter.getTenantWriter().delete(tenant);
    rdbmsWriter.flush();

    final var deletedInstance = tenantReader.findOne(tenant.tenantId()).orElse(null);
    assertThat(deletedInstance).isNull();
  }

  @TestTemplate
  public void shouldFindAllPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final TenantReader reader = rdbmsService.getTenantReader();

    final var tenantName = "tenant-" + nextStringId();
    createAndSaveRandomTenants(rdbmsWriter, b -> b.name(tenantName));
    final var searchResult =
        reader.search(
            new TenantQuery(
                new TenantFilter.Builder().name(tenantName).build(),
                TenantSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final TenantReader reader = rdbmsService.getTenantReader();

    createAndSaveRandomTenants(rdbmsWriter, b -> b.name("test"));
    final var instance = createAndSaveTenant(rdbmsWriter);

    final var searchResult =
        reader.search(
            new TenantQuery(
                new TenantFilter.Builder()
                    .key(instance.tenantKey())
                    .tenantId(instance.tenantId())
                    .name(instance.name())
                    .build(),
                TenantSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().key()).isEqualTo(instance.tenantKey());
  }

  @TestTemplate
  public void shouldFindWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final TenantReader tenantReader = rdbmsService.getTenantReader();

    final var tenantName = nextStringId();
    createAndSaveRandomTenants(rdbmsWriter, b -> b.name(tenantName));
    final var sort = TenantSort.of(s -> s.name().asc().tenantId().asc());
    final var searchResult =
        tenantReader.search(
            TenantQuery.of(
                b -> b.filter(f -> f.name(tenantName)).sort(sort).page(p -> p.from(0).size(20))));

    final var instanceAfter = searchResult.items().get(9);
    final var nextPage =
        tenantReader.search(
            TenantQuery.of(
                b ->
                    b.filter(f -> f.name(tenantName))
                        .sort(sort)
                        .page(
                            p ->
                                p.size(5)
                                    .searchAfter(
                                        new Object[] {
                                          instanceAfter.name(),
                                          instanceAfter.tenantId(),
                                          instanceAfter.key()
                                        }))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(10, 15));
  }

  @TestTemplate
  public void shouldAddMemberToTenant(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final UserReader userReader = rdbmsService.getUserReader();
    final var tenant = TenantFixtures.createRandomized(b -> b);
    createAndSaveTenant(rdbmsWriter, tenant);
    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveUser(rdbmsWriter, user);

    // when
    rdbmsWriter
        .getTenantWriter()
        .addMember(new TenantMemberDbModel(tenant.tenantId(), user.username(), "USER"));
    rdbmsWriter.flush();

    final var users =
        userReader.search(
            new UserQuery(
                new UserFilter.Builder().tenantId(tenant.tenantId()).build(),
                UserSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));
    assertThat(users.total()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldRemoveMemberFromTenant(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final UserReader userReader = rdbmsService.getUserReader();
    final var tenant = TenantFixtures.createRandomized(b -> b);
    createAndSaveTenant(rdbmsWriter, tenant);
    final var user = UserFixtures.createRandomized(b -> b);
    createAndSaveUser(rdbmsWriter, user);
    rdbmsWriter
        .getTenantWriter()
        .addMember(new TenantMemberDbModel(tenant.tenantId(), user.username(), "USER"));

    // when
    rdbmsWriter
        .getTenantWriter()
        .removeMember(new TenantMemberDbModel(tenant.tenantId(), user.username(), "USER"));
    rdbmsWriter.flush();

    final var users =
        userReader.search(
            new UserQuery(
                new UserFilter.Builder().tenantId(tenant.tenantId()).build(),
                UserSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));
    assertThat(users.total()).isEqualTo(0);
  }

  private static void compareTenant(final TenantEntity actual, final TenantDbModel expected) {
    assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFields("assignedMemberKeys", "key")
        .isEqualTo(expected);

    assertThat(actual.tenantId()).isEqualTo(expected.tenantId());
  }
}
