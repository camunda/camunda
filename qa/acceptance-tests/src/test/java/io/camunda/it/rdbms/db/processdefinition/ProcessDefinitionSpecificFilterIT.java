/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processdefinition;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveProcessDefinition;
import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveRandomProcessDefinitions;
import static io.camunda.security.api.model.authz.AuthorizationResourceType.PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.RdbmsDataJdbcTest;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@RdbmsDataJdbcTest
@TestPropertySource(
    properties = {"spring.liquibase.enabled=false", "camunda.data.secondary-storage.type=rdbms"})
public class ProcessDefinitionSpecificFilterIT {

  @Autowired private RdbmsServiceFactory rdbmsServiceFactory;
  private RdbmsService rdbmsService;

  private ProcessDefinitionDbReader processDefinitionReader;

  private RdbmsWriters rdbmsWriters;

  @BeforeEach
  public void beforeAll() {
    rdbmsService =
        rdbmsServiceFactory.createRdbmsService(
            DEFAULT_PHYSICAL_TENANT_ID, new SimpleMeterRegistry());
    rdbmsWriters = rdbmsService.createWriter(0L);
    processDefinitionReader = rdbmsService.getProcessDefinitionReader();
  }

  @ParameterizedTest
  @MethodSource("shouldFindProcessDefinitionWithSpecificFilterParameters")
  public void shouldFindProcessDefinitionWithSpecificFilter(final ProcessDefinitionFilter filter) {
    createAndSaveRandomProcessDefinitions(rdbmsWriters);
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(1337L)
                    .processDefinitionId("sorting-test-process")
                    .name("Sorting Test Process")
                    .resourceName("sorting-test-process.bpmn")
                    .versionTag("Version 1337")
                    .version(1337)
                    .tenantId("sorting-tenant1")));

    final var searchResult =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(
                b -> b.filter(filter).sort(s -> s).page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processDefinitionKey()).isEqualTo(1337L);
  }

  @Test
  public void shouldFindProcessDefinitionWithLatestVersion() {
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(4711L)
                    .processDefinitionId("sorting-test-process")
                    .name("Sorting Test Process")
                    .resourceName("sorting-test-process.bpmn")
                    .versionTag("Version 1337")
                    .version(1)
                    .tenantId("sorting-tenant1")));

    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(4712L)
                    .processDefinitionId("sorting-test-process")
                    .name("Sorting Test Process")
                    .resourceName("sorting-test-process.bpmn")
                    .versionTag("Version 1337")
                    .version(2)
                    .tenantId("sorting-tenant1")));

    final var searchResult =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.processDefinitionIds("sorting-test-process")
                                    .isLatestVersion(true))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processDefinitionId())
        .isEqualTo("sorting-test-process");
    assertThat(searchResult.items().getFirst().version()).isEqualTo(2L);
  }

  @Test
  public void shouldFindProcessDefinitionsByIdOrNameWithOr() {
    // given
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(9101L)
                    .processDefinitionId("or-invoice-process")
                    .name("Something else")
                    .tenantId("or-tenant")));
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(9102L)
                    .processDefinitionId("or-other-process")
                    .name("Send invoice")
                    .tenantId("or-tenant")));
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(9103L)
                    .processDefinitionId("or-unrelated")
                    .name("Unrelated")
                    .tenantId("or-tenant")));

    // when
    final var searchResult =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(
                b ->
                    b.filter(
                            FilterBuilders.processDefinition(
                                f ->
                                    f.tenantIds("or-tenant")
                                        .addOrOperation(
                                            FilterBuilders.processDefinition(
                                                o ->
                                                    o.processDefinitionIdOperations(
                                                        Operation.like("*invoice*"))))
                                        .addOrOperation(
                                            FilterBuilders.processDefinition(
                                                o ->
                                                    o.nameOperations(
                                                        Operation.like("*invoice*"))))))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    // then
    assertThat(searchResult.items())
        .extracting(d -> d.processDefinitionKey())
        .containsExactlyInAnyOrder(9101L, 9102L);
  }

  @Test
  public void shouldNotReturnOtherTenantsWhenUsingOr() {
    // given
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(9201L)
                    .processDefinitionId("tenant-or-process")
                    .name("Tenant invoice")
                    .tenantId("tenant-or-a")));
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(9202L)
                    .processDefinitionId("tenant-or-process")
                    .name("Tenant invoice")
                    .tenantId("tenant-or-b")));

    // when
    final var searchResult =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(
                b ->
                    b.filter(
                            FilterBuilders.processDefinition(
                                f ->
                                    f.tenantIds("tenant-or-a")
                                        .addOrOperation(
                                            FilterBuilders.processDefinition(
                                                o ->
                                                    o.nameOperations(
                                                        Operation.like("*invoice*"))))))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    // then
    assertThat(searchResult.items())
        .extracting(d -> d.processDefinitionKey())
        .containsExactly(9201L);
  }

  @Test
  public void shouldRespectAuthorizationWhenUsingOr() {
    // given
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(9301L)
                    .processDefinitionId("auth-allowed-process")
                    .name("Auth invoice")
                    .tenantId("auth-tenant")));
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(9302L)
                    .processDefinitionId("auth-forbidden-process")
                    .name("Auth invoice")
                    .tenantId("auth-tenant")));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(
                    a ->
                        a.resourceType(PROCESS_DEFINITION)
                            .resourceIds(List.of("auth-allowed-process")))),
            TenantCheck.disabled());

    // when
    final var searchResult =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(
                b ->
                    b.filter(
                            FilterBuilders.processDefinition(
                                f ->
                                    f.addOrOperation(
                                        FilterBuilders.processDefinition(
                                            o -> o.nameOperations(Operation.like("*invoice*"))))))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))),
            resourceAccessChecks);

    // then
    assertThat(searchResult.items())
        .extracting(d -> d.processDefinitionKey())
        .containsExactly(9301L);
  }

  @Test
  public void shouldPickLatestVersionAmongMatchesWhenUsingOrWithLatestVersion() {
    // given: v2 matches the search, v3 was renamed and does not (status quo of isLatestVersion)
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(9401L)
                    .processDefinitionId("latest-or-process")
                    .name("Invoice")
                    .version(2)
                    .tenantId("latest-or-tenant")));
    createAndSaveProcessDefinition(
        rdbmsWriters,
        ProcessDefinitionFixtures.createRandomized(
            b ->
                b.processDefinitionKey(9402L)
                    .processDefinitionId("latest-or-process")
                    .name("Billing")
                    .version(3)
                    .tenantId("latest-or-tenant")));

    // when
    final var searchResult =
        processDefinitionReader.search(
            ProcessDefinitionQuery.of(
                b ->
                    b.filter(
                            FilterBuilders.processDefinition(
                                f ->
                                    f.isLatestVersion(true)
                                        .tenantIds("latest-or-tenant")
                                        .addOrOperation(
                                            FilterBuilders.processDefinition(
                                                o ->
                                                    o.nameOperations(Operation.like("Invoice*"))))))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    // then
    assertThat(searchResult.items())
        .extracting(d -> d.processDefinitionKey())
        .containsExactly(9401L);
  }

  static List<ProcessDefinitionFilter> shouldFindProcessDefinitionWithSpecificFilterParameters() {
    return List.of(
        new ProcessDefinitionFilter.Builder().processDefinitionKeys(1337L).build(),
        new ProcessDefinitionFilter.Builder().processDefinitionIds("sorting-test-process").build(),
        new ProcessDefinitionFilter.Builder().names("Sorting Test Process").build(),
        new ProcessDefinitionFilter.Builder().resourceNames("sorting-test-process.bpmn").build(),
        new ProcessDefinitionFilter.Builder().versions(1337).build(),
        new ProcessDefinitionFilter.Builder().versionTags("Version 1337").build(),
        new ProcessDefinitionFilter.Builder().tenantIds("sorting-tenant1").build());
  }
}
