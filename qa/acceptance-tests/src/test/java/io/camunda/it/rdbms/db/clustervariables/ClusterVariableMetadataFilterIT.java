/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.clustervariables;

import static io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures.createAndSaveVariables;
import static io.camunda.it.rdbms.db.fixtures.ClusterVariableFixtures.createRandomTenantClusterVariable;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.generateRandomString;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel.ClusterVariableDbModelBuilder;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ClusterVariableEntity.MetadataEntry;
import io.camunda.search.entities.ClusterVariableScope;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.MetadataValueFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.sort.ClusterVariableSort;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ClusterVariableMetadataFilterIT {

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataEqFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName, tenantId, new MetadataEntry("kind", "CREDENTIAL", null));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(Operation.eq("CREDENTIAL")));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataNeqFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName, tenantId, new MetadataEntry("kind", "CREDENTIAL", null));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(Operation.neq("OTHER")));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataGtFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName, tenantId, new MetadataEntry("schemaVersion", "2", 2.0));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("schemaVersion")
            .valueOperation(UntypedOperation.of(Operation.gt(1.0)));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataGteFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName, tenantId, new MetadataEntry("schemaVersion", "2", 2.0));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("schemaVersion")
            .valueOperation(UntypedOperation.of(Operation.gte(2.0)));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataLtFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName, tenantId, new MetadataEntry("schemaVersion", "2", 2.0));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("schemaVersion")
            .valueOperation(UntypedOperation.of(Operation.lt(3.0)));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataLteFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName, tenantId, new MetadataEntry("schemaVersion", "2", 2.0));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("schemaVersion")
            .valueOperation(UntypedOperation.of(Operation.lte(2.0)));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataInFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName, tenantId, new MetadataEntry("kind", "CREDENTIAL", null));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(Operation.in("CREDENTIAL", "OTHER")));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataLikeFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName,
            tenantId,
            new MetadataEntry("schemaRef", "io.camunda.connector.slack:1", null));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("schemaRef")
            .valueOperation(UntypedOperation.of(Operation.like("*slack*")));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataExistsFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName, tenantId, new MetadataEntry("kind", "CREDENTIAL", null));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(Operation.exists(true)));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldFindClusterVariableWithMetadataNotExistsFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        createRandomTenantClusterVariable(generateRandomString(50))
            .copy(
                b ->
                    ((ClusterVariableDbModelBuilder) b)
                        .name(varName)
                        .tenantId(tenantId)
                        .scope(ClusterVariableScope.TENANT));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(Operation.exists(false)));

    searchAndAssertMetadataFilter(rdbmsService, varName, tenantId, metadataFilter);
  }

  @TestTemplate
  public void shouldExcludeClusterVariableNotMatchingMetadataFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final String tenantId = nextStringId();
    final String varName = "var-name-" + nextStringId();
    final ClusterVariableDbModel clusterVariable =
        givenClusterVariableWithMetadata(
            varName, tenantId, new MetadataEntry("kind", "PLAIN", null));
    createAndSaveVariables(rdbmsService, clusterVariable);

    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(Operation.eq("CREDENTIAL")));

    final var searchResult =
        rdbmsService
            .getClusterVariableReader()
            .search(
                new ClusterVariableQuery(
                    new ClusterVariableFilter.Builder()
                        .scopes(ClusterVariableScope.TENANT.name())
                        .tenantIds(tenantId)
                        .names(varName)
                        .metadataOperations(metadataFilter.build())
                        .build(),
                    ClusterVariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isZero();
    assertThat(searchResult.items()).isEmpty();
  }

  private static ClusterVariableDbModel givenClusterVariableWithMetadata(
      final String varName, final String tenantId, final MetadataEntry... metadata) {
    return createRandomTenantClusterVariable(generateRandomString(50))
        .copy(
            b ->
                ((ClusterVariableDbModelBuilder) b)
                    .name(varName)
                    .tenantId(tenantId)
                    .scope(ClusterVariableScope.TENANT)
                    .metadata(List.of(metadata)));
  }

  private static void searchAndAssertMetadataFilter(
      final RdbmsService rdbmsService,
      final String varName,
      final String tenantId,
      final MetadataValueFilter.Builder metadataFilter) {
    final var searchResult =
        rdbmsService
            .getClusterVariableReader()
            .search(
                new ClusterVariableQuery(
                    new ClusterVariableFilter.Builder()
                        .scopes(ClusterVariableScope.TENANT.name())
                        .tenantIds(tenantId)
                        .names(varName)
                        .metadataOperations(metadataFilter.build())
                        .build(),
                    ClusterVariableSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().name()).isEqualTo(varName);
  }
}
