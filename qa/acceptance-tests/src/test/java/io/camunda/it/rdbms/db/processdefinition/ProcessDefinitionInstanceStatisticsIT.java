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
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceStatisticsDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionInstanceStatisticsIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldAggregateProcessInstanceStatistics(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionInstanceStatisticsDbReader reader =
        rdbmsService.getProcessDefinitionInstanceStatisticsReader();

    // Create process definitions
    final var processDefinition1 =
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionId("process-1").version(1));
    final var processDefinition2 =
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionId("process-1").version(2));
    final var processDefinition3 =
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionId("process-2").version(1));

    createAndSaveProcessDefinition(rdbmsWriter, processDefinition1);
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition2);
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition3);

    // Create process instances for process-1
    final var instance1 =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId("process-1")
                    .processDefinitionKey(processDefinition1.processDefinitionKey())
                    .version(1)
                    .state(ProcessInstanceState.ACTIVE)
                    .numIncidents(0));
    final var instance2 =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId("process-1")
                    .processDefinitionKey(processDefinition2.processDefinitionKey())
                    .version(2)
                    .state(ProcessInstanceState.ACTIVE)
                    .numIncidents(1));
    final var instance3 =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId("process-1")
                    .processDefinitionKey(processDefinition2.processDefinitionKey())
                    .version(2)
                    .state(ProcessInstanceState.ACTIVE)
                    .numIncidents(0));

    // Create process instances for process-2
    final var instance4 =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processDefinitionId("process-2")
                    .processDefinitionKey(processDefinition3.processDefinitionKey())
                    .version(1)
                    .state(ProcessInstanceState.ACTIVE)
                    .numIncidents(2));

    createAndSaveProcessInstance(rdbmsWriter, instance1);
    createAndSaveProcessInstance(rdbmsWriter, instance2);
    createAndSaveProcessInstance(rdbmsWriter, instance3);
    createAndSaveProcessInstance(rdbmsWriter, instance4);

    // Query statistics
    final var query = ProcessDefinitionInstanceStatisticsQuery.of(b -> b);
    final var result = reader.aggregate(query, resourceAccessChecksNoPermissions());

    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(2); // Two process definitions
    assertThat(result.items()).hasSize(2);

    // Verify process-1 statistics
    final var process1Stats =
        result.items().stream()
            .filter(s -> s.processDefinitionId().equals("process-1"))
            .findFirst()
            .orElseThrow();
    assertThat(process1Stats.hasMultipleVersions()).isTrue();
    assertThat(process1Stats.activeInstancesWithoutIncidentCount()).isEqualTo(2);
    assertThat(process1Stats.activeInstancesWithIncidentCount()).isEqualTo(1);

    // Verify process-2 statistics
    final var process2Stats =
        result.items().stream()
            .filter(s -> s.processDefinitionId().equals("process-2"))
            .findFirst()
            .orElseThrow();
    assertThat(process2Stats.hasMultipleVersions()).isFalse();
    assertThat(process2Stats.activeInstancesWithoutIncidentCount()).isEqualTo(0);
    assertThat(process2Stats.activeInstancesWithIncidentCount()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldReturnEmptyResultWhenNoActiveInstances(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionInstanceStatisticsDbReader reader =
        rdbmsService.getProcessDefinitionInstanceStatisticsReader();

    // Create process definition but no active instances
    final var processDefinition =
        ProcessDefinitionFixtures.createRandomized(b -> b.processDefinitionId("test-process"));
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition);

    final var query = ProcessDefinitionInstanceStatisticsQuery.of(b -> b);
    final var result = reader.aggregate(query, resourceAccessChecksNoPermissions());

    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(0);
    assertThat(result.items()).isEmpty();
  }
}
