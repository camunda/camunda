/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processdefinition;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksNoPermissions;
import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveProcessDefinition;
import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceVersionStatisticsDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionInstanceVersionStatisticsIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldAggregateProcessInstanceVersionStatistics(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionInstanceVersionStatisticsDbReader reader =
        rdbmsService.getProcessDefinitionInstanceVersionStatisticsReader();

    // Create process definitions with multiple versions
    final var processDefinition1 =
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionId("test-process").version(1).name("Test Process V1"));
    final var processDefinition2 =
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionId("test-process").version(2).name("Test Process V2"));
    final var processDefinition3 =
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionId("test-process").version(3).name("Test Process V3"));

    createAndSaveProcessDefinition(rdbmsWriter, processDefinition1);
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition2);
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition3);

    // Create instances for version 1
    final var instance1 =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId("test-process")
                    .processDefinitionKey(processDefinition1.processDefinitionKey())
                    .version(1)
                    .state(ProcessInstanceState.ACTIVE)
                    .numIncidents(0));

    // Create instances for version 2
    final var instance2 =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId("test-process")
                    .processDefinitionKey(processDefinition2.processDefinitionKey())
                    .version(2)
                    .state(ProcessInstanceState.ACTIVE)
                    .numIncidents(1));
    final var instance3 =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId("test-process")
                    .processDefinitionKey(processDefinition2.processDefinitionKey())
                    .version(2)
                    .state(ProcessInstanceState.ACTIVE)
                    .numIncidents(0));

    // Create instances for version 3
    final var instance4 =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId("test-process")
                    .processDefinitionKey(processDefinition3.processDefinitionKey())
                    .version(3)
                    .state(ProcessInstanceState.ACTIVE)
                    .numIncidents(2));
    final var instance5 =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId("test-process")
                    .processDefinitionKey(processDefinition3.processDefinitionKey())
                    .version(3)
                    .state(ProcessInstanceState.ACTIVE)
                    .numIncidents(0));

    createAndSaveProcessInstance(rdbmsWriter, instance1);
    createAndSaveProcessInstance(rdbmsWriter, instance2);
    createAndSaveProcessInstance(rdbmsWriter, instance3);
    createAndSaveProcessInstance(rdbmsWriter, instance4);
    createAndSaveProcessInstance(rdbmsWriter, instance5);

    // Query statistics with filter for specific process definition
    final var query =
        ProcessDefinitionInstanceVersionStatisticsQuery.of(
            b ->
                b.filter(
                    ProcessDefinitionInstanceVersionStatisticsFilter.of(
                        f -> f.processDefinitionId("test-process"))));
    final var result = reader.aggregate(query, resourceAccessChecksNoPermissions());

    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(3); // Three versions
    assertThat(result.items()).hasSize(3);

    // Verify version 1 statistics
    final var version1Stats =
        result.items().stream().filter(s -> s.processDefinitionVersion() == 1).findFirst().orElseThrow();
    assertThat(version1Stats.processDefinitionId()).isEqualTo("test-process");
    assertThat(version1Stats.processDefinitionKey())
        .isEqualTo(processDefinition1.processDefinitionKey());
    assertThat(version1Stats.processDefinitionName()).isEqualTo("Test Process V1");
    assertThat(version1Stats.activeInstancesWithoutIncidentCount()).isEqualTo(1);
    assertThat(version1Stats.activeInstancesWithIncidentCount()).isEqualTo(0);

    // Verify version 2 statistics
    final var version2Stats =
        result.items().stream().filter(s -> s.processDefinitionVersion() == 2).findFirst().orElseThrow();
    assertThat(version2Stats.processDefinitionId()).isEqualTo("test-process");
    assertThat(version2Stats.processDefinitionKey())
        .isEqualTo(processDefinition2.processDefinitionKey());
    assertThat(version2Stats.processDefinitionName()).isEqualTo("Test Process V2");
    assertThat(version2Stats.activeInstancesWithoutIncidentCount()).isEqualTo(1);
    assertThat(version2Stats.activeInstancesWithIncidentCount()).isEqualTo(1);

    // Verify version 3 statistics
    final var version3Stats =
        result.items().stream().filter(s -> s.processDefinitionVersion() == 3).findFirst().orElseThrow();
    assertThat(version3Stats.processDefinitionId()).isEqualTo("test-process");
    assertThat(version3Stats.processDefinitionKey())
        .isEqualTo(processDefinition3.processDefinitionKey());
    assertThat(version3Stats.processDefinitionName()).isEqualTo("Test Process V3");
    assertThat(version3Stats.activeInstancesWithoutIncidentCount()).isEqualTo(1);
    assertThat(version3Stats.activeInstancesWithIncidentCount()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldReturnEmptyResultWhenNoActiveInstancesForVersion(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionInstanceVersionStatisticsDbReader reader =
        rdbmsService.getProcessDefinitionInstanceVersionStatisticsReader();

    // Create process definition but no active instances
    final var processDefinition =
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionId("test-process").version(1));
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition);

    final var query =
        ProcessDefinitionInstanceVersionStatisticsQuery.of(
            b ->
                b.filter(
                    ProcessDefinitionInstanceVersionStatisticsFilter.of(
                        f -> f.processDefinitionId("test-process"))));
    final var result = reader.aggregate(query, resourceAccessChecksNoPermissions());

    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(0);
    assertThat(result.items()).isEmpty();
  }
}
