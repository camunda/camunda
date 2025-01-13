/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.authorization;

import static io.camunda.it.rdbms.db.fixtures.AuthorizationFixtures.createAndSaveAuthorization;
import static io.camunda.it.rdbms.db.fixtures.AuthorizationFixtures.createAndSaveRandomAuthorizations;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.AuthorizationReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.it.rdbms.db.fixtures.AuthorizationFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.sort.AuthorizationSort;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AuthorizationIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final AuthorizationReader authorizationReader = rdbmsService.getAuthorizationReader();

    final var authorization = AuthorizationFixtures.createRandomized(b -> b);
    createAndSaveAuthorization(rdbmsWriter, authorization);

    final var instances =
        authorizationReader.find(
            authorization.ownerKey(), authorization.ownerType(), authorization.resourceType());

    compareAuthorizations(instances, authorization);
  }

  @TestTemplate
  public void shouldSaveAndUpdate(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final AuthorizationReader authorizationReader = rdbmsService.getAuthorizationReader();

    final var authorization =
        AuthorizationFixtures.createRandomized(
            b -> b.resourceId("resource1").permissionType(PermissionType.CREATE));

    createAndSaveAuthorization(rdbmsWriter, authorization);

    final var authorizationUpdate1 =
        AuthorizationFixtures.createRandomized(
            b ->
                b.ownerKey(authorization.ownerKey())
                    .ownerType(authorization.ownerType())
                    .resourceType(authorization.resourceType())
                    .resourceId("resource2")
                    .permissionType(PermissionType.CREATE));
    final var authorizationUpdate2 =
        AuthorizationFixtures.createRandomized(
            b ->
                b.ownerKey(authorization.ownerKey())
                    .ownerType(authorization.ownerType())
                    .resourceType(authorization.resourceType())
                    .resourceId("resource3")
                    .permissionType(PermissionType.CREATE));
    final var authorizationUpdate3 =
        AuthorizationFixtures.createRandomized(
            b ->
                b.ownerKey(authorization.ownerKey())
                    .ownerType(authorization.ownerType())
                    .resourceType(authorization.resourceType())
                    .resourceId("resource4")
                    .permissionType(PermissionType.CREATE));
    rdbmsWriter
        .getAuthorizationWriter()
        .addPermissions(List.of(authorizationUpdate1, authorizationUpdate2, authorizationUpdate3));
    rdbmsWriter.flush();

    final var instances =
        authorizationReader.find(
            authorization.ownerKey(), authorization.ownerType(), authorization.resourceType());

    assertThat(instances).isNotNull();
    assertThat(instances).hasSize(4);
    assertThat(instances)
        .extracting(AuthorizationEntity::resourceId)
        .containsExactlyInAnyOrder("resource1", "resource2", "resource3", "resource4");
  }

  @TestTemplate
  public void shouldSaveAndDelete(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final AuthorizationReader authorizationReader = rdbmsService.getAuthorizationReader();

    final var authorization = AuthorizationFixtures.createRandomized(b -> b);
    createAndSaveAuthorization(rdbmsWriter, authorization);
    final var instances =
        authorizationReader.find(
            authorization.ownerKey(), authorization.ownerType(), authorization.resourceType());
    assertThat(instances).isNotEmpty();

    rdbmsWriter.getAuthorizationWriter().removePermission(authorization);
    rdbmsWriter.flush();

    final var deletedInstance =
        authorizationReader.find(
            authorization.ownerKey(), authorization.ownerType(), authorization.resourceType());
    assertThat(deletedInstance).isEmpty();
  }

  @TestTemplate
  public void shouldFindByResourceId(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final AuthorizationReader authorizationReader = rdbmsService.getAuthorizationReader();

    final var authorization = AuthorizationFixtures.createRandomized(b -> b);
    createAndSaveAuthorization(rdbmsWriter, authorization);

    final var resourceId = authorization.resourceId();
    final var searchResult =
        authorizationReader.search(
            new AuthorizationQuery(
                new AuthorizationFilter.Builder().resourceIds(resourceId).build(),
                AuthorizationSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    compareAuthorizations(instance, authorization);
  }

  @TestTemplate
  public void shouldFindAllPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final AuthorizationReader authorizationReader = rdbmsService.getAuthorizationReader();

    createAndSaveRandomAuthorizations(rdbmsWriter, b -> b.ownerType("TEST"));

    final var searchResult =
        authorizationReader.search(
            new AuthorizationQuery(
                new AuthorizationFilter.Builder().ownerType("TEST").build(),
                AuthorizationSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final AuthorizationReader authorizationReader = rdbmsService.getAuthorizationReader();

    final var authorization = AuthorizationFixtures.createRandomized(b -> b);
    createAndSaveRandomAuthorizations(rdbmsWriter);
    createAndSaveAuthorization(rdbmsWriter, authorization);

    final var searchResult =
        authorizationReader.search(
            new AuthorizationQuery(
                new AuthorizationFilter.Builder()
                    .ownerKeys(authorization.ownerKey())
                    .ownerType(authorization.ownerType())
                    .resourceType(authorization.resourceType())
                    .permissionType(authorization.permissionType())
                    .resourceIds(authorization.resourceId())
                    .build(),
                AuthorizationSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    compareAuthorizations(searchResult.items().getFirst(), authorization);
  }

  @TestTemplate
  public void shouldFindWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final AuthorizationReader authorizationReader = rdbmsService.getAuthorizationReader();

    createAndSaveRandomAuthorizations(rdbmsWriter, b -> b.ownerType("ITEST"));
    final var sort =
        AuthorizationSort.of(s -> s.ownerType().asc().resourceType().desc().ownerKey().asc());
    final var searchResult =
        authorizationReader.search(
            AuthorizationQuery.of(
                b -> b.filter(f -> f.ownerType("ITEST")).sort(sort).page(p -> p.from(0).size(20))));

    final var instanceAfter = searchResult.items().get(9);
    final var nextPage =
        authorizationReader.search(
            AuthorizationQuery.of(
                b ->
                    b.filter(f -> f.ownerType("ITEST"))
                        .sort(sort)
                        .page(
                            p ->
                                p.size(5)
                                    .searchAfter(
                                        new Object[] {
                                          instanceAfter.ownerType(),
                                          instanceAfter.resourceType(),
                                          instanceAfter.ownerKey()
                                        }))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items())
        .usingRecursiveComparison()
        .ignoringFields("permissions")
        .isEqualTo(searchResult.items().subList(10, 15));
  }

  private static void compareAuthorizations(
      final List<AuthorizationEntity> instances, final AuthorizationDbModel authorization) {
    assertThat(instances).isNotNull();
    instances.forEach(
        i -> {
          if (i.permissionType() == authorization.permissionType()
              && i.resourceType().equals(authorization.resourceId())) {
            compareAuthorizations(i, authorization);
          }
        });
  }

  private static void compareAuthorizations(
      final AuthorizationEntity instance, final AuthorizationDbModel authorization) {
    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(authorization);
  }
}
