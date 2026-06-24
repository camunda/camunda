/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.flownodeinstance;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstance;
import static io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures;
import io.camunda.it.rdbms.db.util.RdbmsDataJdbcTest;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.sort.FlowNodeInstanceSort;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@RdbmsDataJdbcTest
@TestPropertySource(
    properties = {"spring.liquibase.enabled=false", "camunda.data.secondary-storage.type=rdbms"})
public class FlowNodeInstanceSpecificFilterIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private RdbmsServiceFactory rdbmsServiceFactory;
  private RdbmsService rdbmsService;

  private FlowNodeInstanceDbReader elementInstanceReader;

  private RdbmsWriters rdbmsWriters;

  @BeforeEach
  public void beforeAll() {
    rdbmsService = rdbmsServiceFactory.createRdbmsService(DEFAULT_PHYSICAL_TENANT_ID);
    rdbmsWriters = rdbmsService.createWriter(0L);
    elementInstanceReader = rdbmsService.getFlowNodeInstanceReader();
  }

  @ParameterizedTest
  @MethodSource("shouldFindFlowNodeInstanceWithSpecificFilterParameters")
  public void shouldFindFlowNodeInstanceWithSpecificFilter(final FlowNodeInstanceFilter filter) {
    createAndSaveRandomFlowNodeInstances(
        rdbmsWriters,
        b -> b.state(FlowNodeState.COMPLETED).type(FlowNodeType.BOUNDARY_EVENT).incidentKey(null));
    createAndSaveRandomFlowNodeInstance(
        rdbmsWriters,
        FlowNodeInstanceFixtures.createRandomized(
            b ->
                b.flowNodeInstanceKey(42L)
                    .flowNodeId("unique-element-42")
                    .processInstanceKey(123L)
                    .processDefinitionId("unique-process-124")
                    .processDefinitionKey(124L)
                    .flowNodeScopeKey(124L)
                    .state(FlowNodeState.ACTIVE)
                    .type(FlowNodeType.SERVICE_TASK)
                    .tenantId("unique-tenant-1")
                    .incidentKey(125L)
                    .treePath("unique-tree-path-42")));

    final var searchResult =
        elementInstanceReader.search(
            new FlowNodeInstanceQuery(
                filter,
                FlowNodeInstanceSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().flowNodeInstanceKey()).isEqualTo(42L);
  }

  static List<FlowNodeInstanceFilter> shouldFindFlowNodeInstanceWithSpecificFilterParameters() {
    return List.of(
        FlowNodeInstanceFilter.of(b -> b.flowNodeInstanceKeys(42L)),
        FlowNodeInstanceFilter.of(b -> b.flowNodeIds("unique-element-42")),
        FlowNodeInstanceFilter.of(b -> b.processInstanceKeys(123L)),
        FlowNodeInstanceFilter.of(b -> b.processDefinitionKeys(124L)),
        FlowNodeInstanceFilter.of(b -> b.processDefinitionIds("unique-process-124")),
        FlowNodeInstanceFilter.of(b -> b.elementInstanceScopeKeys(124L)),
        FlowNodeInstanceFilter.of(b -> b.states(FlowNodeState.ACTIVE.name())),
        FlowNodeInstanceFilter.of(b -> b.types(FlowNodeType.SERVICE_TASK)),
        FlowNodeInstanceFilter.of(b -> b.tenantIds("unique-tenant-1")),
        FlowNodeInstanceFilter.of(b -> b.hasIncident(true)),
        FlowNodeInstanceFilter.of(b -> b.incidentKeys(125L)),
        FlowNodeInstanceFilter.of(b -> b.treePaths("unique-tree-path-42")));
  }
}
