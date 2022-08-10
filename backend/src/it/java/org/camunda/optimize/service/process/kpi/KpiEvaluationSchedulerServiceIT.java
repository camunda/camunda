/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.kpi;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.service.KpiEvaluationSchedulerService;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class KpiEvaluationSchedulerServiceIT extends AbstractIT {

  private static final String EXTERNAL_EVENT_GROUP = "testGroup";
  private static final String EXTERNAL_EVENT_SOURCE = "integrationTestSource";
  private static final String PROCESS_DEFINITION_KEY = "aProcessDefKey";
  private static final String BPMN_START_EVENT_ID = "StartEvent_1";
  private static final String USER_TASK_ID_ONE = "user_task_1";
  private static final String BPMN_END_EVENT_ID = "EndEvent_1";
  private static final String EVENT_PROCESS_NAME = "myEventProcess";

  @Test
  public void testKpiSchedulerScheduledSuccessfully() {
    assertThat(getKpiScheduler().isScheduledToRun()).isTrue();
  }

  @Test
  public void noResultsSavedWhenNoKpiReportExists() {
    // given
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(false);
    reportClient.createSingleProcessReport(reportDataDto);
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();

    // when
    runKpiSchedulerAndRefreshIndices();

    // then
    List<ProcessOverviewDto> processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();
    assertThat(processOverviewDtos).singleElement()
      .satisfies(element -> assertThat(element.getLastKpiEvaluationResults())
        .isEmpty());
  }

  @Test
  public void kpiResultsSavedWhenKpiReportExists() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    String reportId1 = createKpiReport(PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReport(PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    runKpiSchedulerAndRefreshIndices();

    // then
    List<ProcessOverviewDto> processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();
    assertThat(processOverviewDtos).singleElement()
      .satisfies(element -> assertThat(element.getLastKpiEvaluationResults())
        .isEqualTo(Map.of(reportId1, "1.0", reportId2, "1.0")));
  }

  @Test
  public void existingProcessOverviewDocumentGetsUpdatedWithKpiEvaluationInformation() {
    // given
    final String anotherDefinitionKey = "anotherDefinitionKey";
    final ProcessDefinitionEngineDto firstDef = deploySimpleProcessDefinition(PROCESS_DEFINITION_KEY);
    final ProcessDefinitionEngineDto secondDef = deploySimpleProcessDefinition(anotherDefinitionKey);
    importAllEngineEntitiesFromScratch();
    // setting the owner in order to create a document in process overview index
    processOverviewClient.updateProcess(firstDef.getKey(), DEFAULT_USERNAME, new ProcessDigestRequestDto());
    processOverviewClient.updateProcess(secondDef.getKey(), DEFAULT_USERNAME, new ProcessDigestRequestDto());
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    String reportId1 = createKpiReport(PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReport(PROCESS_DEFINITION_KEY);
    String reportId3 = createKpiReport(anotherDefinitionKey);
    String reportId4 = createKpiReport(anotherDefinitionKey);
    importAllEngineEntitiesFromLastIndex();

    // when
    runKpiSchedulerAndRefreshIndices();

    // then
    List<ProcessOverviewDto> processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();
    assertThat(processOverviewDtos).hasSize(2)
      .extracting(ProcessOverviewDto::getLastKpiEvaluationResults, ProcessOverviewDto::getOwner)
      .containsExactlyInAnyOrder(
        Tuple.tuple(Map.of(reportId1, "0.0", reportId2, "0.0"), DEFAULT_USERNAME),
        Tuple.tuple(Map.of(reportId3, "0.0", reportId4, "0.0"), DEFAULT_USERNAME)
      );
  }

  @Test
  public void kpiEvaluationValueGetsUpdatedWhenTheResultChanges() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition(PROCESS_DEFINITION_KEY);
    String reportId = createKpiReport(PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();
    runKpiSchedulerAndRefreshIndices();
    List<ProcessOverviewDto> processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();

    // then
    assertThat(processOverviewDtos).hasSize(1)
      .extracting(ProcessOverviewDto::getLastKpiEvaluationResults)
      .containsExactlyInAnyOrder(Map.of(reportId, "0.0"));

    // when
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromLastIndex();
    runKpiSchedulerAndRefreshIndices();

    // then
    processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();
    assertThat(processOverviewDtos).hasSize(1)
      .extracting(ProcessOverviewDto::getLastKpiEvaluationResults)
      .containsExactlyInAnyOrder(Map.of(reportId, "1.0"));
  }

  @Test
  public void kpiResultRemovedWhenReportIsNoLongerKpiReport() {
    // given
    deploySimpleProcessDefinition(PROCESS_DEFINITION_KEY);
    String reportId = createKpiReport(PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();
    runKpiSchedulerAndRefreshIndices();
    List<ProcessOverviewDto> processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();

    // then
    assertThat(processOverviewDtos).hasSize(1)
      .extracting(ProcessOverviewDto::getLastKpiEvaluationResults)
      .containsExactlyInAnyOrder(Map.of(reportId, "0.0"));

    // when
    final ProcessReportDataDto updatedReportData = createProcessReportDataDto(
      PROCESS_DEFINITION_KEY,
      false,
      ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE
    );
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionRequestDto.setData(updatedReportData);
    reportClient.updateSingleProcessReport(reportId, singleProcessReportDefinitionRequestDto);
    importAllEngineEntitiesFromLastIndex();
    runKpiSchedulerAndRefreshIndices();

    // then
    processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();
    assertThat(processOverviewDtos).hasSize(1)
      .extracting(ProcessOverviewDto::getLastKpiEvaluationResults)
      .containsExactlyInAnyOrder(Collections.emptyMap());
  }

  @Test
  public void kpiResultSavedWhenEvaluationResultIsNone() {
    // given
    deploySimpleProcessDefinition(PROCESS_DEFINITION_KEY);
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(PROCESS_DEFINITION_KEY)))
      .build();
    reportDataDto.setFilter(
      ProcessFilterBuilder.filter()
        .withDeletedIncident()
        .add().buildList());
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
    TargetDto targetDto = new TargetDto();
    targetDto.setValue("999");
    targetDto.setIsBelow(Boolean.TRUE);
    targetDto.setUnit(TargetValueUnit.HOURS);
    reportDataDto.getConfiguration().getTargetValue().getDurationProgress().setTarget(targetDto);
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionRequestDto.setData(reportDataDto);
    String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionRequestDto);
    importAllEngineEntitiesFromScratch();

    // when
    runKpiSchedulerAndRefreshIndices();

    // then
    List<ProcessOverviewDto> processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();
    Map<String, String> expectedValues = new HashMap();
    expectedValues.put(reportId, null);
    assertThat(processOverviewDtos).singleElement()
      .satisfies(process -> assertThat(process).extracting(ProcessOverviewDto::getLastKpiEvaluationResults)
        .isEqualTo(expectedValues));
  }

  @Test
  public void kpiResultGetsDeletedFromProcessOverviewIndexWhenReportIsDeleted() {
    // given
    deploySimpleProcessDefinition(PROCESS_DEFINITION_KEY);
    String reportId = createKpiReport(PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();
    runKpiSchedulerAndRefreshIndices();
    List<ProcessOverviewDto> processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();

    // then
    assertThat(processOverviewDtos).singleElement()
      .satisfies(element -> assertThat(element.getLastKpiEvaluationResults()).hasSize(1));

    // when
    reportClient.deleteReport(reportId);
    runKpiSchedulerAndRefreshIndices();

    // then
    processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();
    assertThat(processOverviewDtos).singleElement()
      .satisfies(process -> assertThat(process.getLastKpiEvaluationResults()).isEmpty());
  }

  @Test
  public void kpiReportGetsUpdatedToNonSingleNumberReport() {
    // given
    deploySimpleProcessDefinition(PROCESS_DEFINITION_KEY);
    String reportId = createKpiReport(PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();
    runKpiSchedulerAndRefreshIndices();
    List<ProcessOverviewDto> processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();

    // then
    assertThat(processOverviewDtos).singleElement()
      .satisfies(element -> assertThat(element.getLastKpiEvaluationResults()).hasSize(1));

    // when
    final ProcessReportDataDto updatedReportData = createProcessReportDataDto(
      PROCESS_DEFINITION_KEY,
      true,
      ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE
    );
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionRequestDto.setData(updatedReportData);
    reportClient.updateSingleProcessReport(reportId, singleProcessReportDefinitionRequestDto);
    importAllEngineEntitiesFromLastIndex();
    runKpiSchedulerAndRefreshIndices();
    processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();

    // then
    assertThat(processOverviewDtos).singleElement()
      .satisfies(element -> assertThat(element.getLastKpiEvaluationResults()).hasSize(0));
  }

  @Test
  public void eventBasedProcessDoesNotGetAddedToProcessDefinitionIndex() {
    // given
    String eventProcessDefinitionId = createAndPublishEventBasedProcess();
    createKpiReport(eventProcessDefinitionId);

    // when
    runKpiSchedulerAndRefreshIndices();

    // then
    List<ProcessOverviewDto> processOverviewDtos = getAllDocumentsOfProcessOverviewIndex();
    assertThat(processOverviewDtos).isEmpty();
  }

  @SneakyThrows
  protected void executeImportCycle() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler()
      .runImportRound(true)
      .get(10, TimeUnit.SECONDS);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(processDefinitionKey));
  }

  private KpiEvaluationSchedulerService getKpiScheduler() {
    return embeddedOptimizeExtension.getKpiSchedulerService();
  }

  private String createKpiReport(final String definitionKey) {
    ProcessReportDataDto processReportDataDto = createProcessReportDataDto(
      definitionKey,
      true,
      ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE
    );
    return reportClient.createSingleProcessReport(processReportDataDto);
  }

  private ProcessReportDataDto createProcessReportDataDto(final String definitionKey,
                                                          final boolean isKpi,
                                                          final ProcessReportDataType reportDataType) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(reportDataType)
      .definitions(List.of(new ReportDataDefinitionDto(definitionKey)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(isKpi);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setIsBelow(true);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setTarget("1");
    return reportDataDto;
  }

  private String createAndPublishEventBasedProcess() {
    final String startedEvent = "startedEvent";
    final String finishedEvent = "finishedEvent";
    ingestTestEvent(startedEvent);
    ingestTestEvent(finishedEvent);
    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto(
      startedEvent, finishedEvent
    );
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);
    publishEventBasedProcess(eventProcessMappingId);
    return eventProcessMappingId;
  }

  private void publishEventBasedProcess(final String eventProcessMappingId) {
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    // two cycles needed as the definition gets published with the next cycle after publish finished
    executeImportCycle();
    executeImportCycle();
  }

  private void ingestTestEvent(final String eventName) {
    embeddedOptimizeExtension.getEventService()
      .saveEventBatch(
        Collections.singletonList(
          EventDto.builder()
            .id(IdGenerator.getNextId())
            .eventName(eventName)
            .timestamp(OffsetDateTime.now().toInstant().toEpochMilli())
            .traceId("myTraceId")
            .group("testGroup")
            .source(EXTERNAL_EVENT_SOURCE)
            .data(ImmutableMap.of("var", "value"))
            .build()
        )
      );
  }

  private EventProcessMappingDto buildSimpleEventProcessMappingDto(final String ingestedStartEventName,
                                                                   final String ingestedEndEventName) {
    return buildSimpleEventProcessMappingDto(
      EventMappingDto.builder()
        .end(EventTypeDto.builder()
               .group(EXTERNAL_EVENT_GROUP)
               .source(EXTERNAL_EVENT_SOURCE)
               .eventName(ingestedStartEventName)
               .build())
        .build(),
      EventMappingDto.builder()
        .end(EventTypeDto.builder()
               .group(EXTERNAL_EVENT_GROUP)
               .source(EXTERNAL_EVENT_SOURCE)
               .eventName(ingestedEndEventName)
               .build())
        .build()
    );
  }

  private EventProcessMappingDto buildSimpleEventProcessMappingDto(final EventMappingDto startEventMapping,
                                                                   final EventMappingDto endEventMapping) {
    final Map<String, EventMappingDto> eventMappings = Map.of(
      BPMN_START_EVENT_ID,
      startEventMapping,
      BPMN_END_EVENT_ID,
      endEventMapping
    );
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      eventMappings, EVENT_PROCESS_NAME, createTwoEventAndOneTaskActivitiesProcessDefinitionXml()
    );
  }

  private List<ProcessOverviewDto> getAllDocumentsOfProcessOverviewIndex() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_OVERVIEW_INDEX_NAME,
      ProcessOverviewDto.class
    );
  }

  private void runKpiSchedulerAndRefreshIndices() {
    getKpiScheduler().runKpiImportTask();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private static String createTwoEventAndOneTaskActivitiesProcessDefinitionXml() {
    return convertBpmnModelToXmlString(getSingleUserTaskDiagram(
      "aProcessName",
      BPMN_START_EVENT_ID,
      BPMN_END_EVENT_ID,
      USER_TASK_ID_ONE
    ));
  }

  private static String convertBpmnModelToXmlString(final BpmnModelInstance bpmnModel) {
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModel);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }
}
