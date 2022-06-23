/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.none.distributedby;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_NONE_KEY;
import static org.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE_BY_PROCESS;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class ProcessInstanceFrequencyByNoneByProcessReportEvaluationIT extends AbstractIT {

  @Test
  public void reportEvaluationWithSingleProcessDefinitionSource() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartServiceTaskProcess();
    importAllEngineEntitiesFromScratch();
    final String processDisplayName = "processDisplayName";
    final String processIdentifier = IdGenerator.getNextId();
    ReportDataDefinitionDto definition = new ReportDataDefinitionDto(
      processIdentifier, processInstanceDto.getProcessDefinitionKey(), processDisplayName);
    final ProcessReportDataDto reportData = createCountGroupedByNoneByProcessReport(List.of(definition));

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(GROUP_NONE_KEY)
      .distributedByContains(processIdentifier, 1., processDisplayName)
      .doAssert(evaluationResponse.getResult());
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSources() {
    // given
    final String firstProcessIdentifier = "first";
    final String secondProcessIdentifier = "second";
    final ProcessInstanceEngineDto firstProcess = deployAndStartUserTaskProcess(firstProcessIdentifier);
    final ProcessInstanceEngineDto secondProcess = deployAndStartServiceTaskProcess(secondProcessIdentifier);
    engineIntegrationExtension.startProcessInstance(secondProcess.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    ReportDataDefinitionDto firstDefinition =
      createReportDefinition(firstProcess.getProcessDefinitionKey(), firstProcessIdentifier);
    ReportDataDefinitionDto secondDefinition =
      createReportDefinition(secondProcess.getProcessDefinitionKey(), secondProcessIdentifier);
    final ProcessReportDataDto reportData =
      createCountGroupedByNoneByProcessReport(List.of(firstDefinition, secondDefinition));

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(GROUP_NONE_KEY)
      .distributedByContains(firstProcessIdentifier, 1., firstProcessIdentifier)
      .distributedByContains(secondProcessIdentifier, 2., secondProcessIdentifier)
      .doAssert(evaluationResponse.getResult());
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSourcesForSameProcess() {
    // given
    final ProcessInstanceEngineDto processInstance = deployAndStartUserTaskProcess();
    importAllEngineEntitiesFromScratch();
    final String firstDefinitionSourceIdentifier = "first";
    final String secondDefinitionSourceIdentifier = "second";
    ReportDataDefinitionDto firstDefinition =
      createReportDefinition(processInstance.getProcessDefinitionKey(), firstDefinitionSourceIdentifier);
    ReportDataDefinitionDto secondDefinition =
      createReportDefinition(processInstance.getProcessDefinitionKey(), secondDefinitionSourceIdentifier);
    final ProcessReportDataDto reportData =
      createCountGroupedByNoneByProcessReport(List.of(firstDefinition, secondDefinition));

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(GROUP_NONE_KEY)
      .distributedByContains(firstDefinitionSourceIdentifier, 1., firstDefinitionSourceIdentifier)
      .distributedByContains(secondDefinitionSourceIdentifier, 1., secondDefinitionSourceIdentifier)
      .doAssert(evaluationResponse.getResult());
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSourcesWithOverlappingVersionBuckets() {
    // given
    final ProcessInstanceEngineDto v1instance = deployAndStartUserTaskProcess();
    final ProcessInstanceEngineDto v2Instance = deployAndStartUserTaskProcess();
    engineIntegrationExtension.startProcessInstance(v2Instance.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    final String allVersionDefinition = "allVersions";
    final String latestDefinition = "latestVersion";
    final String specificDefinition = "specificVersion";
    ReportDataDefinitionDto allVersionsDef =
      createReportDefinition(v1instance.getProcessDefinitionKey(), allVersionDefinition, ReportConstants.ALL_VERSIONS);
    ReportDataDefinitionDto latestVersionDef =
      createReportDefinition(v1instance.getProcessDefinitionKey(), latestDefinition, ReportConstants.LATEST_VERSION);
    ReportDataDefinitionDto specificVersionDef =
      createReportDefinition(
        v1instance.getProcessDefinitionKey(),
        specificDefinition,
        v1instance.getProcessDefinitionVersion()
      );
    final ProcessReportDataDto reportData = createCountGroupedByNoneByProcessReport(
      List.of(allVersionsDef, latestVersionDef, specificVersionDef));

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(GROUP_NONE_KEY)
      .distributedByContains(allVersionDefinition, 3., allVersionDefinition)
      .distributedByContains(latestDefinition, 2., latestDefinition)
      .distributedByContains(specificDefinition, 1., specificDefinition)
      .doAssert(evaluationResponse.getResult());
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSourcesWithNoDataInOneBucket() {
    // given
    final ProcessDefinitionEngineDto v1definition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram());
    final ProcessDefinitionEngineDto v2definition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram());
    engineIntegrationExtension.startProcessInstance(v2definition.getId());
    importAllEngineEntitiesFromScratch();
    final String v1processIdentifier = "firstVersion";
    final String v2processIdentifier = "secondVersion";
    ReportDataDefinitionDto v1processDefSource =
      createReportDefinition(v1definition.getKey(), v1processIdentifier, "1");
    ReportDataDefinitionDto v2processDefSource =
      createReportDefinition(v2definition.getKey(), v2processIdentifier, "2");
    final ProcessReportDataDto reportData = createCountGroupedByNoneByProcessReport(
      List.of(v1processDefSource, v2processDefSource));

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(GROUP_NONE_KEY)
      .distributedByContains(v1processIdentifier, 0., v1processIdentifier)
      .distributedByContains(v2processIdentifier, 1., v2processIdentifier)
      .doAssert(evaluationResponse.getResult());
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionNoDataForAnySource() {
    // given
    final ProcessDefinitionEngineDto v1definition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram());
    final ProcessDefinitionEngineDto v2definition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleServiceTaskProcess());
    importAllEngineEntitiesFromScratch();
    final String v1processIdentifier = "firstVersion";
    final String v2processIdentifier = "secondVersion";
    ReportDataDefinitionDto v1processDefSource =
      createReportDefinition(v1definition.getKey(), v1processIdentifier, ALL_VERSIONS);
    ReportDataDefinitionDto v2processDefSource =
      createReportDefinition(v2definition.getKey(), v2processIdentifier, ALL_VERSIONS);
    final ProcessReportDataDto reportData = createCountGroupedByNoneByProcessReport(
      List.of(v1processDefSource, v2processDefSource));

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(0L)
      .processInstanceCountWithoutFilters(0L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(GROUP_NONE_KEY)
      .distributedByContains(v1processIdentifier, 0., v1processIdentifier)
      .distributedByContains(v2processIdentifier, 0., v2processIdentifier)
      .doAssert(evaluationResponse.getResult());
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSourcesWithTenantSelection() {
    // given
    final List<String> allTenants = Arrays.asList(null, "tenant");
    final String procDefKey = deployAndStartMultiTenantUserTaskProcess(allTenants);
    importAllEngineEntitiesFromScratch();
    final String tenantSourceIdentifier = "sourceIdA";
    ReportDataDefinitionDto tenantSource =
      createReportDefinition(procDefKey, tenantSourceIdentifier, List.of("tenant"));
    final String defaultSourceIdentifier = "sourceIdB";
    ReportDataDefinitionDto defaultTenantSource = createReportDefinition(
      procDefKey, defaultSourceIdentifier, DEFAULT_TENANT_IDS);
    final String allTenantSourceIdentifier = "sourceIdC";
    ReportDataDefinitionDto bothTenantSource = createReportDefinition(
      procDefKey, allTenantSourceIdentifier, allTenants);
    final ProcessReportDataDto reportData = createCountGroupedByNoneByProcessReport(
      List.of(tenantSource, defaultTenantSource, bothTenantSource));

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(GROUP_NONE_KEY)
      .distributedByContains(tenantSourceIdentifier, 1., tenantSourceIdentifier)
      .distributedByContains(defaultSourceIdentifier, 1., defaultSourceIdentifier)
      .distributedByContains(allTenantSourceIdentifier, 2., allTenantSourceIdentifier)
      .doAssert(evaluationResponse.getResult());
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSourcesAndFilterApplied() {
    // given
    final String firstProcessIdentifier = "first";
    final String secondProcessIdentifier = "second";
    final ProcessInstanceEngineDto firstProcess = deployAndStartUserTaskProcess(firstProcessIdentifier);
    final ProcessInstanceEngineDto secondProcess = deployAndStartServiceTaskProcess(secondProcessIdentifier);
    engineIntegrationExtension.startProcessInstance(secondProcess.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    ReportDataDefinitionDto firstDefinition =
      createReportDefinition(firstProcess.getProcessDefinitionKey(), firstProcessIdentifier);
    ReportDataDefinitionDto secondDefinition =
      createReportDefinition(secondProcess.getProcessDefinitionKey(), secondProcessIdentifier);
    final ProcessReportDataDto reportData =
      createCountGroupedByNoneByProcessReport(List.of(firstDefinition, secondDefinition));
    reportData.setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo(firstProcessIdentifier).add().buildList());

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(3L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(GROUP_NONE_KEY)
      .distributedByContains(firstProcessIdentifier, 0., firstProcessIdentifier)
      .distributedByContains(secondProcessIdentifier, 2., secondProcessIdentifier)
      .doAssert(evaluationResponse.getResult());
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> engineIntegrationExtension.deployAndStartProcess(
        BpmnModels.getSingleUserTaskDiagram(processKey),
        tenant
      ));
    return processKey;
  }

  private ReportDataDefinitionDto createReportDefinition(final String key,
                                                         final String definitionSourceIdentifier,
                                                         final String version) {
    final ReportDataDefinitionDto reportDefinition = createReportDefinition(key, definitionSourceIdentifier);
    reportDefinition.setVersion(version);
    return reportDefinition;
  }

  private ReportDataDefinitionDto createReportDefinition(final String key,
                                                         final String sourceIdentifier,
                                                         final List<String> tenants) {
    final ReportDataDefinitionDto reportDefinition = createReportDefinition(key, sourceIdentifier, ALL_VERSIONS);
    reportDefinition.setTenantIds(tenants);
    return reportDefinition;
  }

  private ReportDataDefinitionDto createReportDefinition(final String key,
                                                         final String definitionSourceIdentifier) {
    return new ReportDataDefinitionDto(definitionSourceIdentifier, key, definitionSourceIdentifier);
  }

  private ProcessReportDataDto createCountGroupedByNoneByProcessReport(final List<ReportDataDefinitionDto> definitionDtos) {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE_BY_PROCESS)
      .build();
    reportData.setDefinitions(definitionDtos);
    return reportData;
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcess() {
    return engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram("processId"));
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcess(final String processId) {
    return engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(processId));
  }

  private ProcessInstanceEngineDto deployAndStartServiceTaskProcess() {
    return engineIntegrationExtension.deployAndStartProcess(getSingleServiceTaskProcess("processId"));
  }

  private ProcessInstanceEngineDto deployAndStartServiceTaskProcess(final String processId) {
    return engineIntegrationExtension.deployAndStartProcess(getSingleServiceTaskProcess(processId));
  }

}
