/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.process;

import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static io.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;
import static io.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static io.camunda.optimize.util.BpmnModelsC8.getSingleUserTaskDiagram;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.util.BpmnModelsC8;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;

@Tag("reportEvaluation")
public class AbstractProcessDefinitionITC8 extends AbstractCCSMIT {

  protected static final String TEST_ACTIVITY = "testActivity";
  protected static final String START_EVENT = "startEvent";
  protected static final String DEFAULT_VARIABLE_NAME = "foo";
  protected static final String DEFAULT_VARIABLE_VALUE = "bar";
  protected static final String FIRST_CANDIDATE_GROUP_ID = "firstGroup";
  protected static final String TEST_PROCESS = "aProcess";

  protected static final Map<String, VariableType> varNameToTypeMap =
      new HashMap<>(VariableType.values().length);

  static {
    varNameToTypeMap.put("dateVar", VariableType.DATE);
    varNameToTypeMap.put("boolVar", VariableType.BOOLEAN);
    varNameToTypeMap.put("shortVar", VariableType.SHORT);
    varNameToTypeMap.put("intVar", VariableType.INTEGER);
    varNameToTypeMap.put("longVar", VariableType.LONG);
    varNameToTypeMap.put("doubleVar", VariableType.DOUBLE);
    varNameToTypeMap.put("stringVar", VariableType.STRING);
  }

  protected ProcessInstanceEvent deployAndStartSimpleUserTaskProcess() {
    final var process = zeebeExtension.deployProcess(getSingleUserTaskDiagram());
    return zeebeExtension.startProcessInstanceForProcess(process.getBpmnProcessId());
  }

  protected Process deploySimpleOneUserTasksDefinition() {
    return deploySimpleOneUserTasksDefinition(TEST_PROCESS, null);
  }

  protected Process deploySimpleOneUserTasksDefinition(final String key, final String tenantId) {
    return zeebeExtension.deployProcess(getSingleUserTaskDiagram(key), tenantId);
  }

  protected ProcessInstanceEvent deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  protected ProcessInstanceEvent deployAndStartSimpleServiceTaskProcess(final String activityId) {
    return deployAndStartSimpleServiceTaskProcess(TEST_PROCESS, activityId, null);
  }

  protected ProcessInstanceEvent deployAndStartSimpleServiceTaskProcess(
      final String key, final String activityId, final String tenantId) {
    final BpmnModelInstance processModel =
        BpmnModelsC8.getSingleServiceTaskProcess(key, activityId);
    final Process process = zeebeExtension.deployProcess(processModel, tenantId);
    return zeebeExtension.startProcessInstanceWithVariables(
        process.getBpmnProcessId(), ImmutableMap.of(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE));
  }

  protected ProcessInstanceEvent deploySimpleServiceTaskProcessAndGetDefinition() {
    return deploySimpleServiceTaskProcessAndGetDefinition(TEST_PROCESS);
  }

  private ProcessInstanceEvent deploySimpleServiceTaskProcessAndGetDefinition(final String key) {
    final BpmnModelInstance processModel =
        BpmnModelsC8.getSingleServiceTaskProcess(key, TEST_ACTIVITY);
    final Process process = zeebeExtension.deployProcess(processModel);
    return zeebeExtension.startProcessInstanceForProcess(process.getBpmnProcessId());
  }

  protected String deployAndStartMultiTenantSimpleServiceTaskProcess(
      final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.forEach(
        tenant -> {
          if (tenant != null) {
            final TenantDto tenantDto = new TenantDto(tenant, tenant, DEFAULT_ENGINE_ALIAS);
            databaseIntegrationTestExtension.addEntryToDatabase(
                TENANT_INDEX_NAME, tenant, tenantDto);
          }
          deployAndStartSimpleServiceTaskProcess(processKey, TEST_ACTIVITY, tenant);
        });

    return processKey;
  }

  protected static Stream<List<ProcessFilterDto<?>>> viewLevelFilters() {
    return Stream.of(
        ProcessFilterBuilder.filter()
            .assignee()
            .id(DEFAULT_USERNAME)
            .filterLevel(FilterApplicationLevel.VIEW)
            .add()
            .buildList(),
        ProcessFilterBuilder.filter()
            .candidateGroups()
            .id(FIRST_CANDIDATE_GROUP_ID)
            .filterLevel(FilterApplicationLevel.VIEW)
            .add()
            .buildList(),
        ProcessFilterBuilder.filter()
            .flowNodeDuration()
            .flowNode(
                START_EVENT,
                DurationFilterDataDto.builder()
                    .operator(ComparisonOperator.GREATER_THAN)
                    .unit(DurationUnit.HOURS)
                    .value(1L)
                    .build())
            .filterLevel(FilterApplicationLevel.VIEW)
            .add()
            .buildList(),
        ProcessFilterBuilder.filter()
            .withOpenIncident()
            .filterLevel(FilterApplicationLevel.VIEW)
            .add()
            .buildList(),
        ProcessFilterBuilder.filter()
            .withResolvedIncident()
            .filterLevel(FilterApplicationLevel.VIEW)
            .add()
            .buildList());
  }
}
