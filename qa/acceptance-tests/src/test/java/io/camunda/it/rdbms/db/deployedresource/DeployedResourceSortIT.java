/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.deployedresource;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.DeployedResourceFixtures.createAndSaveRandomDeployedResources;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DeployedResourceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.DeployedResourceQuery;
import io.camunda.search.sort.DeployedResourceSort;
import io.camunda.search.sort.DeployedResourceSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class DeployedResourceSortIT {

  @TestTemplate
  public void shouldSortByResourceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.resourceKey().asc(),
        Comparator.comparing(DeployedResourceEntity::resourceKey));
  }

  @TestTemplate
  public void shouldSortByResourceKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.resourceKey().desc(),
        Comparator.comparing(DeployedResourceEntity::resourceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByResourceIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.resourceId().asc(),
        Comparator.comparing(DeployedResourceEntity::resourceId));
  }

  @TestTemplate
  public void shouldSortByResourceNameAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.resourceName().asc(),
        Comparator.comparing(DeployedResourceEntity::resourceName));
  }

  @TestTemplate
  public void shouldSortByVersionAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.version().asc(),
        Comparator.comparing(DeployedResourceEntity::version));
  }

  @TestTemplate
  public void shouldSortByVersionDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.version().desc(),
        Comparator.comparing(DeployedResourceEntity::version).reversed());
  }

  @TestTemplate
  public void shouldSortByVersionTagAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.versionTag().asc(),
        Comparator.comparing(
            DeployedResourceEntity::versionTag, Comparator.nullsFirst(Comparator.naturalOrder())));
  }

  @TestTemplate
  public void shouldSortByDeploymentKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.deploymentKey().asc(),
        Comparator.comparing(DeployedResourceEntity::deploymentKey));
  }

  @TestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(DeployedResourceEntity::tenantId));
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<DeployedResourceSort>> sortBuilder,
      final Comparator<DeployedResourceEntity> comparator) {
    final Long deploymentKey = nextKey();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(0L);
    createAndSaveRandomDeployedResources(rdbmsWriters, 20, b -> b.deploymentKey(deploymentKey));

    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();
    final var results =
        reader
            .search(
                DeployedResourceQuery.of(
                    b ->
                        b.filter(f -> f.deploymentKeyOperations(Operation.eq(deploymentKey)))
                            .sort(DeployedResourceSort.of(sortBuilder))
                            .page(p -> p.from(0).size(20))))
            .items();

    assertThat(results.size()).isGreaterThanOrEqualTo(20);
    assertThat(results).isSortedAccordingTo(comparator);
  }
}
