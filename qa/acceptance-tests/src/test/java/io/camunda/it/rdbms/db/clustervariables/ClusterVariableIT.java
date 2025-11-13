/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.clustervariables;

import static io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures.createAndSaveRandomGlobalClusterVariablesAndReturnOne;
import static io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures.createAndSaveRandomTenantClusterVariablesAndReturnOne;
import static io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures.createAndSaveVariables;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.generateRandomString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ClusterVariableDbReader;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel.ClusterVariableDbModelBuilder;
import io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.sort.ClusterVariableSort;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ClusterVariableIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindTenantClusterVariableByNameAndResource(
      final CamundaRdbmsTestApplication testApplication) {
    final ClusterVariableDbModel randomizedVariable =
        createAndSaveRandomTenantClusterVariablesAndReturnOne(testApplication);

    final var instance =
        testApplication
            .getRdbmsService()
            .getClusterVariableReader()
            .getTenantScopedClusterVariable(
                randomizedVariable.resourceId(), randomizedVariable.name());

    assertThat(instance).isNotNull();
    assertVariableDbModelEqualToEntity(randomizedVariable, instance);
    assertThat(instance.fullValue()).isNull();
    assertThat(instance.isPreview()).isFalse();
  }

  @TestTemplate
  public void shouldSaveAndFindGlobalClusterVariableByNameAndResource(
      final CamundaRdbmsTestApplication testApplication) {
    final ClusterVariableDbModel randomizedVariable =
        createAndSaveRandomGlobalClusterVariablesAndReturnOne(testApplication);

    final var instance =
        testApplication
            .getRdbmsService()
            .getClusterVariableReader()
            .getGloballyScopedClusterVariable(randomizedVariable.name());

    assertThat(instance).isNotNull();
    assertVariableDbModelEqualToEntity(randomizedVariable, instance);
    assertThat(instance.fullValue()).isNull();
    assertThat(instance.isPreview()).isFalse();
  }

  @TestTemplate
  public void shouldSaveAndFindBigTenantClusterVariableByNameAndResource(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String bigValue = generateRandomString(9000);
    final ClusterVariableDbModel randomizedVariable =
        ClusterVariableFixtures.createRandomTenantClusterVariable(bigValue);
    createAndSaveVariables(rdbmsService, randomizedVariable);

    final var instance =
        rdbmsService
            .getClusterVariableReader()
            .getTenantScopedClusterVariable(
                randomizedVariable.resourceId(), randomizedVariable.name());

    assertThat(instance).isNotNull();
    assertThat(instance.isPreview()).isTrue();
    assertThat(instance.fullValue()).isEqualTo(bigValue);
    assertThat(instance.value()).hasSizeLessThan(instance.fullValue().length());
  }

  @TestTemplate
  public void shouldSaveAndFindBigGlobalClusterVariableByNameAndResource(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String bigValue = generateRandomString(9000);
    final ClusterVariableDbModel randomizedVariable =
        ClusterVariableFixtures.createRandomGlobalClusterVariable(bigValue);
    createAndSaveVariables(rdbmsService, randomizedVariable);

    final var instance =
        rdbmsService
            .getClusterVariableReader()
            .getGloballyScopedClusterVariable(randomizedVariable.name());

    assertThat(instance).isNotNull();
    assertThat(instance.isPreview()).isTrue();
    assertThat(instance.fullValue()).isEqualTo(bigValue);
    assertThat(instance.value()).hasSizeLessThan(instance.fullValue().length());
  }

  @TestTemplate
  public void shouldFindAllTenantClusterVariablesPaged(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String resourceId = CommonFixtures.nextStringId();
    ClusterVariableFixtures.createAndSaveRandomsTenantClusterVariablesWithFixedResourceId(
        rdbmsService, resourceId);

    final var searchResult =
        rdbmsService
            .getClusterVariableReader()
            .search(
                new ClusterVariableQuery(
                    new ClusterVariableFilter.Builder()
                        .scopes("TENANT")
                        .resourceIds(resourceId)
                        .build(),
                    ClusterVariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllGlobalClusterVariablesPaged(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    ClusterVariableFixtures.createAndSaveRandomsGlobalClusterVariablesWithFixed(
        rdbmsService, "value");

    final var searchResult =
        rdbmsService
            .getClusterVariableReader()
            .search(
                new ClusterVariableQuery(
                    new ClusterVariableFilter.Builder().values("value").scopes("GLOBAL").build(),
                    ClusterVariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllTenantClusterVariablesPageValuesAreNull(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String resourceId = CommonFixtures.nextStringId();
    ClusterVariableFixtures.createAndSaveRandomsTenantClusterVariablesWithFixedResourceId(
        rdbmsService, resourceId);

    final var searchResult =
        rdbmsService
            .getClusterVariableReader()
            .search(
                new ClusterVariableQuery(
                    new ClusterVariableFilter.Builder()
                        .scopes("TENANT")
                        .resourceIds(resourceId)
                        .build(),
                    ClusterVariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(20);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final ClusterVariableDbReader clusterVariableReader = rdbmsService.getClusterVariableReader();

    final String resourceId = CommonFixtures.nextStringId();
    final String variableName = CommonFixtures.nextStringId();
    final ClusterVariableDbModel randomizedVariable =
        ClusterVariableFixtures.createRandomTenantClusterVariable(generateRandomString(100));
    final ClusterVariableDbModel variableWithFixedName =
        randomizedVariable.copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(variableName)
                    .resourceId(resourceId)
                    .scope("TENANT"));
    createAndSaveVariables(rdbmsService, variableWithFixedName);

    final var searchResult =
        clusterVariableReader.search(
            new ClusterVariableQuery(
                new ClusterVariableFilter.Builder()
                    .names(variableName)
                    .scopes("TENANT")
                    .resourceIds(resourceId)
                    .build(),
                ClusterVariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().name()).isEqualTo(variableName);
    assertThat(searchResult.items().getFirst().resourceId()).isEqualTo(resourceId);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithSearchAfter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final ClusterVariableDbReader clusterVariableReader = rdbmsService.getClusterVariableReader();

    final String resourceId = CommonFixtures.nextStringId();
    ClusterVariableFixtures.createAndSaveRandomsTenantClusterVariablesWithFixedResourceId(
        rdbmsService, resourceId);

    final var sort = ClusterVariableSort.of(s -> s.name().asc().scope().asc());
    final var searchResult =
        clusterVariableReader.search(
            ClusterVariableQuery.of(
                b ->
                    b.filter(f -> f.scopes("TENANT").resourceIds(resourceId))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var firstPage =
        clusterVariableReader.search(
            ClusterVariableQuery.of(
                b ->
                    b.filter(f -> f.scopes("TENANT").resourceIds(resourceId))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        clusterVariableReader.search(
            ClusterVariableQuery.of(
                b ->
                    b.filter(f -> f.scopes("TENANT").resourceIds(resourceId))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  private void assertVariableDbModelEqualToEntity(
      final ClusterVariableDbModel dbModel, final ClusterVariableEntity entity) {
    assertThat(entity).usingRecursiveComparison().isEqualTo(dbModel);
  }
}
