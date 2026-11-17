/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.deployedresource;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromResourceIds;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static io.camunda.it.rdbms.db.fixtures.DeployedResourceFixtures.createAndSaveDeployedResource;
import static io.camunda.it.rdbms.db.fixtures.DeployedResourceFixtures.createAndSaveRandomDeployedResources;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DeployedResourceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.DeployedResourceDbModel;
import io.camunda.it.rdbms.db.fixtures.DeployedResourceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.DeployedResourceQuery;
import io.camunda.search.sort.DeployedResourceSort;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.core.authz.ResourceAccessChecks;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class DeployedResourceIT {

  public static final int PARTITION_ID = 0;

  @TestTemplate
  public void shouldSaveAndFindDeployedResourceByKey(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final DeployedResourceDbModel resource = DeployedResourceFixtures.createRandomized(b -> b);
    createAndSaveDeployedResource(rdbmsWriters, resource);

    // when
    final var entity = reader.getByKey(resource.resourceKey(), ResourceAccessChecks.disabled());

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.resourceKey()).isEqualTo(resource.resourceKey());
    assertThat(entity.resourceId()).isEqualTo(resource.resourceId());
    assertThat(entity.resourceName()).isEqualTo(resource.resourceName());
    assertThat(entity.resourceType()).isEqualTo(resource.resourceType());
    assertThat(entity.version()).isEqualTo(resource.version());
    assertThat(entity.versionTag()).isEqualTo(resource.versionTag());
    assertThat(entity.deploymentKey()).isEqualTo(resource.deploymentKey());
    assertThat(entity.tenantId()).isEqualTo(resource.tenantId());
    assertThat(entity.resourceContent()).isEqualTo(resource.resourceContent());
  }

  @TestTemplate
  public void shouldSaveAndFindDeployedResourceMetadataByKey(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final DeployedResourceDbModel resource = DeployedResourceFixtures.createRandomized(b -> b);
    createAndSaveDeployedResource(rdbmsWriters, resource);

    // when
    final var entity =
        reader.getByKeyMetadata(resource.resourceKey(), ResourceAccessChecks.disabled());

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.resourceKey()).isEqualTo(resource.resourceKey());
    assertThat(entity.resourceId()).isEqualTo(resource.resourceId());
    assertThat(entity.resourceName()).isEqualTo(resource.resourceName());
    assertThat(entity.resourceType()).isEqualTo(resource.resourceType());
    assertThat(entity.version()).isEqualTo(resource.version());
    assertThat(entity.versionTag()).isEqualTo(resource.versionTag());
    assertThat(entity.deploymentKey()).isEqualTo(resource.deploymentKey());
    assertThat(entity.tenantId()).isEqualTo(resource.tenantId());
    assertThat(entity.resourceContent()).isNull();
  }

  @TestTemplate
  public void shouldFindDeployedResourceByResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final DeployedResourceDbModel resource = DeployedResourceFixtures.createRandomized(b -> b);
    createAndSaveDeployedResource(rdbmsWriters, resource);
    createAndSaveRandomDeployedResources(rdbmsWriters);

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.resourceIds(resource.resourceId()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().resourceId()).isEqualTo(resource.resourceId());
  }

  @TestTemplate
  public void shouldFindDeployedResourceByResourceType(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final String uniqueType = "FORM-" + nextKey();
    final DeployedResourceDbModel resource =
        createAndSaveDeployedResource(rdbmsWriters, b -> b.resourceType(uniqueType));
    createAndSaveRandomDeployedResources(rdbmsWriters);

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.resourceTypes(uniqueType))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    // then
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().resourceKey()).isEqualTo(resource.resourceKey());
  }

  @TestTemplate
  public void shouldFindDeployedResourceByDeploymentKey(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final Long deploymentKey = nextKey();
    final DeployedResourceDbModel resource =
        createAndSaveDeployedResource(rdbmsWriters, b -> b.deploymentKey(deploymentKey));
    createAndSaveRandomDeployedResources(rdbmsWriters);

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.deploymentKeyOperations(Operation.eq(deploymentKey)))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    // then
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().resourceKey()).isEqualTo(resource.resourceKey());
  }

  @TestTemplate
  public void shouldFindDeployedResourceByVersion(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final Long deploymentKey = nextKey();
    final DeployedResourceDbModel resource =
        createAndSaveDeployedResource(
            rdbmsWriters, b -> b.deploymentKey(deploymentKey).version(42));
    createAndSaveRandomDeployedResources(
        rdbmsWriters, b -> b.deploymentKey(deploymentKey).version(1));

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.deploymentKeyOperations(Operation.eq(deploymentKey)).versions(42))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    // then
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().resourceKey()).isEqualTo(resource.resourceKey());
  }

  @TestTemplate
  public void shouldFindDeployedResourceByVersionTag(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final String versionTag = "v-unique-" + nextKey();
    final DeployedResourceDbModel resource =
        createAndSaveDeployedResource(rdbmsWriters, b -> b.versionTag(versionTag));
    createAndSaveRandomDeployedResources(rdbmsWriters);

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.versionTags(versionTag))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    // then
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().resourceKey()).isEqualTo(resource.resourceKey());
  }

  @TestTemplate
  public void shouldFindDeployedResourceByTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final String tenantId = "tenant-unique-" + nextKey();
    final DeployedResourceDbModel resource =
        createAndSaveDeployedResource(rdbmsWriters, b -> b.tenantId(tenantId));
    createAndSaveRandomDeployedResources(rdbmsWriters);

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.tenantIds(tenantId))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    // then
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().resourceKey()).isEqualTo(resource.resourceKey());
  }

  @TestTemplate
  public void shouldFindDeployedResourceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final DeployedResourceDbModel resource = DeployedResourceFixtures.createRandomized(b -> b);
    createAndSaveDeployedResource(rdbmsWriters, resource);
    createAndSaveRandomDeployedResources(rdbmsWriters);

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.resourceKeyOperations(Operation.eq(resource.resourceKey()))
                                    .resourceIds(resource.resourceId())
                                    .resourceNames(resource.resourceName())
                                    .resourceTypes(resource.resourceType())
                                    .versions(resource.version())
                                    .versionTags(resource.versionTag())
                                    .deploymentKeyOperations(Operation.eq(resource.deploymentKey()))
                                    .tenantIds(resource.tenantId()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    // then
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().resourceKey()).isEqualTo(resource.resourceKey());
  }

  @TestTemplate
  public void shouldFindAllDeployedResourcesPaged(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final Long deploymentKey = nextKey();
    createAndSaveRandomDeployedResources(rdbmsWriters, b -> b.deploymentKey(deploymentKey));

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.deploymentKeyOperations(Operation.eq(deploymentKey)))
                        .sort(s -> s.resourceKey().asc())
                        .page(p -> p.from(0).size(5))));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllDeployedResourcesPagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final Long deploymentKey = nextKey();
    createAndSaveRandomDeployedResources(rdbmsWriters, 120, b -> b.deploymentKey(deploymentKey));

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.deploymentKeyOperations(Operation.eq(deploymentKey)))
                        .sort(s -> s.resourceKey().asc())
                        .page(p -> p.from(0).size(5))));

    // then
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isTrue();
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindDeployedResourceWithSearchAfter(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final Long deploymentKey = nextKey();
    createAndSaveRandomDeployedResources(rdbmsWriters, b -> b.deploymentKey(deploymentKey));

    final var sort = DeployedResourceSort.of(s -> s.resourceName().asc().resourceKey().asc());

    final var fullResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.deploymentKeyOperations(Operation.eq(deploymentKey)))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var firstPage =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.deploymentKeyOperations(Operation.eq(deploymentKey)))
                        .sort(sort)
                        .page(p -> p.from(0).size(10))));

    // when
    final var nextPage =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.deploymentKeyOperations(Operation.eq(deploymentKey)))
                        .sort(sort)
                        .page(p -> p.size(10).after(firstPage.endCursor()))));

    // then
    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(10);
    assertThat(nextPage.items()).isEqualTo(fullResult.items().subList(10, 20));
  }

  @TestTemplate
  public void shouldDeleteDeployedResource(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final Long deploymentKey = nextKey();
    final var resource1 =
        createAndSaveDeployedResource(rdbmsWriters, b -> b.deploymentKey(deploymentKey));
    final var resource2 =
        createAndSaveDeployedResource(rdbmsWriters, b -> b.deploymentKey(deploymentKey));
    final var resource3 =
        createAndSaveDeployedResource(rdbmsWriters, b -> b.deploymentKey(deploymentKey));

    // when
    rdbmsWriters.getResourceWriter().delete(resource2.resourceKey());
    rdbmsWriters.flush();

    // then
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(
                b ->
                    b.filter(f -> f.deploymentKeyOperations(Operation.eq(deploymentKey)))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(DeployedResourceEntity::resourceKey))
        .containsExactlyInAnyOrder(resource1.resourceKey(), resource3.resourceKey());
  }

  @TestTemplate
  public void shouldFindDeployedResourceByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final DeployedResourceDbModel resource = createAndSaveDeployedResource(rdbmsWriters, b -> b);
    createAndSaveRandomDeployedResources(rdbmsWriters);

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(b -> b),
            resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.RESOURCE, resource.resourceId()));

    // then
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().resourceKey()).isEqualTo(resource.resourceKey());
  }

  @TestTemplate
  public void shouldFindDeployedResourceByAuthorizedTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DeployedResourceDbReader reader = rdbmsService.getResourceDbReader();

    final DeployedResourceDbModel resource = createAndSaveDeployedResource(rdbmsWriters, b -> b);
    createAndSaveRandomDeployedResources(rdbmsWriters);

    // when
    final var searchResult =
        reader.search(
            DeployedResourceQuery.of(b -> b),
            resourceAccessChecksFromTenantIds(resource.tenantId()));

    // then
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().resourceKey()).isEqualTo(resource.resourceKey());
  }
}
