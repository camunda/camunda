/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.flownodeinstance;

import static io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures.createAndSaveFlowNodeInstance;
import static io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.sort.FlowNodeInstanceSort;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@DataJdbcTest
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@TestPropertySource(properties = {"spring.liquibase.enabled=false", "camunda.database.type=rdbms"})
public class FlowNodeInstanceSpecificFilterIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private RdbmsService rdbmsService;

  @Autowired private FlowNodeInstanceReader flowNodeInstanceReader;

  private RdbmsWriter rdbmsWriter;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriter = rdbmsService.createWriter(0L);
  }

  @ParameterizedTest
  @MethodSource("shouldFindFlowNodeInstanceWithSpecificFilterParameters")
  public void shouldFindFlowNodeInstanceWithSpecificFilter(final FlowNodeInstanceFilter filter) {
    createAndSaveRandomFlowNodeInstances(
        rdbmsWriter,
        b -> b.state(FlowNodeState.COMPLETED).type(FlowNodeType.BOUNDARY_EVENT).incidentKey(null));
    createAndSaveFlowNodeInstance(
        rdbmsWriter,
        FlowNodeInstanceFixtures.createRandomized(
            b ->
                b.flowNodeInstanceKey(42L)
                    .flowNodeId("unique-flowNode-42")
                    .processInstanceKey(123L)
                    .processDefinitionId("unique-process-124")
                    .processDefinitionKey(124L)
                    .state(FlowNodeState.ACTIVE)
                    .type(FlowNodeType.SERVICE_TASK)
                    .tenantId("unique-tenant-1")
                    .incidentKey(125L)
                    .treePath("unique-tree-path-42")));

    final var searchResult =
        flowNodeInstanceReader.search(
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
        FlowNodeInstanceFilter.of(b -> b.flowNodeIds("unique-flowNode-42")),
        FlowNodeInstanceFilter.of(b -> b.processInstanceKeys(123L)),
        FlowNodeInstanceFilter.of(b -> b.processDefinitionKeys(124L)),
        FlowNodeInstanceFilter.of(b -> b.processDefinitionIds("unique-process-124")),
        FlowNodeInstanceFilter.of(b -> b.states(FlowNodeState.ACTIVE)),
        FlowNodeInstanceFilter.of(b -> b.types(FlowNodeType.SERVICE_TASK)),
        FlowNodeInstanceFilter.of(b -> b.tenantIds("unique-tenant-1")),
        FlowNodeInstanceFilter.of(b -> b.hasIncident(true)),
        FlowNodeInstanceFilter.of(b -> b.incidentKeys(125L)),
        FlowNodeInstanceFilter.of(b -> b.treePaths("unique-tree-path-42")));
  }
}
