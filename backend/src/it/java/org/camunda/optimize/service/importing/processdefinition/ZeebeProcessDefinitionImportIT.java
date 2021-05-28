/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.processdefinition;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.dto.optimize.DataImportSourceType;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import org.camunda.optimize.service.util.importing.ZeebeConstants;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class ZeebeProcessDefinitionImportIT extends AbstractZeebeIT {

  private static final String START_EVENT = "start";
  private static final String SERVICE_TASK = "service_task";
  private static final String END_EVENT = "end";

  @Test
  public void importZeebeProcess_allDataSavedToDefinition() {
    // given
    final String processName = "someProcess";
    final BpmnModelInstance simpleProcess = createSimpleBpmmProcess(processName);
    final Process deployedProcess = zeebeExtension.deployProcess(simpleProcess);
    waitUntilNumberOfDefinitionsExported(1);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions())
      .singleElement()
      .satisfies(importedDef -> {
        assertThat(importedDef.getId()).isEqualTo(String.valueOf(deployedProcess.getProcessDefinitionKey()));
        assertThat(importedDef.getKey()).isEqualTo(String.valueOf(deployedProcess.getProcessDefinitionKey()));
        assertThat(importedDef.getVersion()).isEqualTo(String.valueOf(deployedProcess.getVersion()));
        assertThat(importedDef.getVersionTag()).isNull();
        assertThat(importedDef.getType()).isEqualTo(DefinitionType.PROCESS);
        assertThat(importedDef.isEventBased()).isFalse();
        assertThat(importedDef.getBpmn20Xml()).isEqualTo(Bpmn.convertToString(simpleProcess));
        assertThat(importedDef.getName()).isEqualTo(processName);
        assertThat(importedDef.getDataSource().getType()).isEqualTo(DataImportSourceType.ZEEBE);
        assertThat(importedDef.getDataSource().getName()).isEqualTo(ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX);
        assertThat(importedDef.getTenantId()).isNull();
        assertThat(importedDef.isDeleted()).isFalse();
        assertThat(importedDef.getUserTaskNames()).isEmpty();
        assertThat(importedDef.getFlowNodeData()).containsExactlyInAnyOrder(
          new FlowNodeDataDto(START_EVENT, START_EVENT, "startEvent"),
          new FlowNodeDataDto(SERVICE_TASK, SERVICE_TASK, "serviceTask"),
          new FlowNodeDataDto(END_EVENT, null, "endEvent")
        );
      });
  }

  @Test
  public void importZeebeProcess_unnamedProcessUsesProcessIdAsName() {
    // given
    final BpmnModelInstance noNameStartEventProcess = Bpmn.createExecutableProcess()
      .startEvent(START_EVENT).name(START_EVENT).done();
    final Process deployedProcess = zeebeExtension.deployProcess(noNameStartEventProcess);
    waitUntilNumberOfDefinitionsExported(1);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions())
      .singleElement()
      .satisfies(importedDef -> {
        assertThat(importedDef.getId()).isEqualTo(String.valueOf(deployedProcess.getProcessDefinitionKey()));
        assertThat(importedDef.getKey()).isEqualTo(String.valueOf(deployedProcess.getProcessDefinitionKey()));
        assertThat(importedDef.getVersion()).isEqualTo(String.valueOf(deployedProcess.getVersion()));
        assertThat(importedDef.getVersionTag()).isNull();
        assertThat(importedDef.getType()).isEqualTo(DefinitionType.PROCESS);
        assertThat(importedDef.isEventBased()).isFalse();
        assertThat(importedDef.getBpmn20Xml()).isEqualTo(Bpmn.convertToString(noNameStartEventProcess));
        assertThat(importedDef.getName()).isEqualTo(deployedProcess.getBpmnProcessId());
        assertThat(importedDef.getDataSource().getType()).isEqualTo(DataImportSourceType.ZEEBE);
        assertThat(importedDef.getDataSource().getName()).isEqualTo(ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX);
        assertThat(importedDef.getTenantId()).isNull();
        assertThat(importedDef.isDeleted()).isFalse();
        assertThat(importedDef.getUserTaskNames()).isEmpty();
        assertThat(importedDef.getFlowNodeData()).containsExactlyInAnyOrder(
          new FlowNodeDataDto(START_EVENT, START_EVENT, "startEvent")
        );
      });
  }

  @Test
  public void importZeebeProcess_multipleProcessesDeployed() {
    // given
    final String firstProcessName = "firstProcess";
    zeebeExtension.deployProcess(createSimpleBpmmProcess(firstProcessName));
    final String secondProcessName = "secondProcess";
    zeebeExtension.deployProcess(createSimpleBpmmProcess(secondProcessName));
    waitUntilNumberOfDefinitionsExported(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(2)
      .extracting(DefinitionOptimizeResponseDto::getName)
      .containsExactlyInAnyOrder(firstProcessName, secondProcessName);
  }

  @Test
  public void importZeebeProcess_multipleProcessesDeployedOnDifferentDays() {
    // given
    final String firstProcessName = "firstProcess";
    zeebeExtension.deployProcess(createSimpleBpmmProcess(firstProcessName));

    zeebeExtension.getZeebeClock().setCurrentTime(Instant.now().plus(1, ChronoUnit.DAYS));
    final String secondProcessName = "secondProcess";
    zeebeExtension.deployProcess(createSimpleBpmmProcess(secondProcessName));
    waitUntilNumberOfDefinitionsExported(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(2)
      .extracting(DefinitionOptimizeResponseDto::getName)
      .containsExactlyInAnyOrder(firstProcessName, secondProcessName);
  }

  @Test
  public void importZeebeProcess_multipleVersionsOfSameProcess() {
    // given
    final String processName = "someProcess";
    final BpmnModelInstance simpleProcess = createSimpleBpmmProcess(processName);
    final Process firstVersion = zeebeExtension.deployProcess(simpleProcess);
    final Process secondVersion = zeebeExtension.deployProcess(createStartEndProcess(processName));
    waitUntilNumberOfDefinitionsExported(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(2)
      .extracting(DefinitionOptimizeResponseDto::getId, DefinitionOptimizeResponseDto::getVersion)
      .containsExactlyInAnyOrder(
        Tuple.tuple(String.valueOf(firstVersion.getProcessDefinitionKey()), String.valueOf(firstVersion.getVersion())),
        Tuple.tuple(String.valueOf(secondVersion.getProcessDefinitionKey()), String.valueOf(secondVersion.getVersion()))
      );
  }

  @Test
  public void importZeebeProcess_multipleProcessOverMultipleBatches() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setMaxImportPageSize(1);

    final String firstProcessName = "firstProcess";
    zeebeExtension.deployProcess(createSimpleBpmmProcess(firstProcessName));
    final String secondProcessName = "secondProcess";
    zeebeExtension.deployProcess(createSimpleBpmmProcess(secondProcessName));
    waitUntilNumberOfDefinitionsExported(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(1)
      .extracting(DefinitionOptimizeResponseDto::getName).containsExactlyInAnyOrder(firstProcessName);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessDefinitions()).hasSize(2)
      .extracting(DefinitionOptimizeResponseDto::getName)
      .containsExactlyInAnyOrder(firstProcessName, secondProcessName);
  }

  private BpmnModelInstance createSimpleBpmmProcess(final String processName) {
    return Bpmn.createExecutableProcess(processName)
      .name(processName)
      .startEvent(START_EVENT).name(START_EVENT)
      .serviceTask(SERVICE_TASK).zeebeJobType(SERVICE_TASK).name(SERVICE_TASK)
      .endEvent(END_EVENT).name(null)
      .done();
  }

  private BpmnModelInstance createStartEndProcess(final String processName) {
    return Bpmn.createExecutableProcess(processName)
      .name(processName)
      .startEvent(START_EVENT).name(START_EVENT)
      .endEvent(END_EVENT).name(null)
      .done();
  }

  @SneakyThrows
  private void waitUntilNumberOfDefinitionsExported(final int expectedDefinitionsCount) {
    final String expectedIndex =
      zeebeExtension.getZeebeRecordPrefix() + "-" + ElasticsearchConstants.ZEEBE_PROCESS_INDEX_NAME;
    Awaitility.dontCatchUncaughtExceptions()
      .timeout(5, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
          .getHighLevelClient().indices().exists(new GetIndexRequest(expectedIndex), RequestOptions.DEFAULT)
      ).isTrue());
    final CountRequest definitionCountRequest =
      new CountRequest(expectedIndex)
        .query(boolQuery().must(termQuery(ZeebeProcessDefinitionRecordDto.Fields.intent, ProcessIntent.CREATED)));
    Awaitility.catchUncaughtExceptions()
      .timeout(5, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
          .getHighLevelClient()
          .count(definitionCountRequest, RequestOptions.DEFAULT)
          .getCount())
        .isEqualTo(expectedDefinitionsCount));
  }

}
