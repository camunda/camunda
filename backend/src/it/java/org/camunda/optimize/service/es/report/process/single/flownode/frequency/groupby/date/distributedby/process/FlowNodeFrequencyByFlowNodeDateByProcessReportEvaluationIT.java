/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.date.distributedby.process;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class FlowNodeFrequencyByFlowNodeDateByProcessReportEvaluationIT extends AbstractIT {

  protected abstract ProcessReportDataType getReportDataType();

  protected abstract ProcessGroupByType getGroupByType();

  abstract void changeFlowNodeInstanceDate(final ProcessInstanceEngineDto processInstanceDto,
                                           final String flowNodeId,
                                           final OffsetDateTime date);

  @Test
  public void reportEvaluationWithSingleProcessDefinitionSource() {
    // given
    final OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeInstanceDate(instance, START_EVENT, now.minusDays(1));
    importAllEngineEntitiesFromScratch();
    final String processDisplayName = "processDisplayName";
    final String processIdentifier = IdGenerator.getNextId();
    ReportDataDefinitionDto definition =
      new ReportDataDefinitionDto(processIdentifier, instance.getProcessDefinitionKey(), processDisplayName);

    // when
    final ProcessReportDataDto reportData = createReport(Collections.singletonList(definition));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(instance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(definition.getVersions().get(0));
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy()).isNotNull();
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(DistributedByType.PROCESS);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getMeasures()).hasSize(1)
      .extracting(MeasureResponseDto::getProperty, MeasureResponseDto::getData)
      .containsExactly(
        Tuple.tuple(
          ViewProperty.FREQUENCY,
          List.of(
            createHyperMapResult(
              localDateTimeToString(now.minusDays(1)),
              new MapResultEntryDto(processIdentifier, 1.0, processDisplayName)
            ),
            createHyperMapResult(
              localDateTimeToString(now),
              new MapResultEntryDto(processIdentifier, 2.0, processDisplayName)
            )
          )
        )
      );
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSources() {
    // given
    final OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram("first"));
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeInstanceDate(firstInstance, START_EVENT, now.minusDays(1));
    changeFlowNodeInstanceDate(firstInstance, USER_TASK_1, now.minusDays(1));
    final ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.deployAndStartProcess(getDoubleUserTaskDiagram("second"));
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeInstanceDate(secondInstance, START_EVENT, now.minusDays(1));
    importAllEngineEntitiesFromScratch();
    final String firstDisplayName = "firstName";
    final String secondDisplayName = "secondName";
    final String firstIdentifier = "first";
    final String secondIdentifier = "second";
    ReportDataDefinitionDto firstDefinition =
      new ReportDataDefinitionDto(firstIdentifier, firstInstance.getProcessDefinitionKey(), firstDisplayName);
    ReportDataDefinitionDto secondDefinition =
      new ReportDataDefinitionDto(secondIdentifier, secondInstance.getProcessDefinitionKey(), secondDisplayName);

    // when
    final ProcessReportDataDto reportData = createReport(List.of(firstDefinition, secondDefinition));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getMeasures()).hasSize(1)
      .extracting(MeasureResponseDto::getData)
      .containsExactly(List.of(
        createHyperMapResult(
          localDateTimeToString(now.minusDays(1)),
          new MapResultEntryDto(firstIdentifier, 2.0, firstDisplayName),
          new MapResultEntryDto(secondIdentifier, 1.0, secondDisplayName)
        ),
        createHyperMapResult(
          localDateTimeToString(now),
          new MapResultEntryDto(firstIdentifier, 1.0, firstDisplayName),
          new MapResultEntryDto(secondIdentifier, 3.0, secondDisplayName)
        )
      ));
  }

  @Test
  public void reportEvaluationWithMultipleProcessDefinitionSourcesAndOverlappingInstances() {
    // given
    final OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto v1Instance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram("definition"));
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeInstanceDate(v1Instance, START_EVENT, now.minusDays(1));
    changeFlowNodeInstanceDate(v1Instance, USER_TASK_1, now.minusDays(1));
    final ProcessInstanceEngineDto v2Instance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram("definition"));
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeInstanceDate(v2Instance, START_EVENT, now.minusDays(1));
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(v2Instance.getId(), 5000);
    importAllEngineEntitiesFromScratch();
    final String v1displayName = "v1";
    final String allVersionsDisplayName = "all";
    final String v1Identifier = "v1Identifier";
    final String allVersionsIdentifier = "allIdentifier";
    ReportDataDefinitionDto v1definition =
      new ReportDataDefinitionDto(v1Identifier, v1Instance.getProcessDefinitionKey(), v1displayName);
    v1definition.setVersion("1");
    ReportDataDefinitionDto allVersionsDefinition = new ReportDataDefinitionDto(
      allVersionsIdentifier, v2Instance.getProcessDefinitionKey(), allVersionsDisplayName);
    allVersionsDefinition.setVersion(ALL_VERSIONS);

    // when
    final ProcessReportDataDto reportData = createReport(List.of(v1definition, allVersionsDefinition));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getMeasures()).hasSize(1)
      .extracting(MeasureResponseDto::getData)
      .containsExactly(List.of(
        createHyperMapResult(
          localDateTimeToString(now.minusDays(1)),
          new MapResultEntryDto(allVersionsIdentifier, 3.0, allVersionsDisplayName),
          new MapResultEntryDto(v1Identifier, 2.0, v1displayName)
        ),
        createHyperMapResult(
          localDateTimeToString(now),
          new MapResultEntryDto(allVersionsIdentifier, 3.0, allVersionsDisplayName),
          new MapResultEntryDto(v1Identifier, 1.0, v1displayName)
        )
      ));
  }

  private String localDateTimeToString(OffsetDateTime time) {
    return embeddedOptimizeExtension.getDateTimeFormatter().format(truncateToStartOfUnit(time, ChronoUnit.DAYS));
  }

  private HyperMapResultEntryDto createHyperMapResult(final String dateAsString,
                                                      final MapResultEntryDto... results) {
    return new HyperMapResultEntryDto(dateAsString, List.of(results), dateAsString);
  }

  private ProcessReportDataDto createReport(final List<ReportDataDefinitionDto> definitionDtos) {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getReportDataType())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();
    reportData.setDefinitions(definitionDtos);
    return reportData;
  }

}

