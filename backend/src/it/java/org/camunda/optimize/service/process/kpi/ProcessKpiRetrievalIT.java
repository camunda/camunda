/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.kpi;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiType;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessKpiRetrievalIT extends AbstractIT {

  private static final String PROCESS_DEFINITION_KEY = "aProcessDefKey";

  @Test
  public void getKpisForDefinition() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReport(true, "2", true, PROCESS_DEFINITION_KEY);
    KpiResponseDto kpiResponseDto1 = new KpiResponseDto();
    kpiResponseDto1.setReportId(reportId1);
    kpiResponseDto1.setReportName("My test report");
    kpiResponseDto1.setValue("1.0");
    kpiResponseDto1.setTarget("1");
    kpiResponseDto1.setIsBelow(true);
    kpiResponseDto1.setType(KpiType.QUALITY);
    kpiResponseDto1.setMeasure(ViewProperty.FREQUENCY);

    KpiResponseDto kpiResponseDto2 = new KpiResponseDto();
    kpiResponseDto2.setReportId(reportId2);
    kpiResponseDto2.setReportName("My test report");
    kpiResponseDto2.setTarget("2");
    kpiResponseDto2.setValue("1.0");
    kpiResponseDto2.setIsBelow(true);
    kpiResponseDto2.setType(KpiType.QUALITY);
    kpiResponseDto2.setMeasure(ViewProperty.FREQUENCY);

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(1)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getKpis)
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          PROCESS_DEFINITION_KEY,
          List.of(kpiResponseDto1, kpiResponseDto2)
        )
      );
  }

  @Test
  public void reportIsNotReturnedIfNotKpi() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReport(true, "2", false, PROCESS_DEFINITION_KEY);
    KpiResponseDto kpiResponseDto1 = new KpiResponseDto();
    kpiResponseDto1.setReportId(reportId1);
    kpiResponseDto1.setReportName("My test report");
    kpiResponseDto1.setValue("1.0");
    kpiResponseDto1.setTarget("1");
    kpiResponseDto1.setIsBelow(true);
    kpiResponseDto1.setType(KpiType.QUALITY);
    kpiResponseDto1.setMeasure(ViewProperty.FREQUENCY);

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(1)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getKpis)
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          PROCESS_DEFINITION_KEY,
          List.of(kpiResponseDto1)
        )
      );
  }

  @Test
  public void otherProcessDefinitionKpiReportIsNotReturned() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram("somedefinition"));
    importAllEngineEntitiesFromScratch();
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReport(true, "2", true, PROCESS_DEFINITION_KEY);
    String reportId3 = createKpiReport(true, "1", true, "somedefinition");
    String reportId4 = createKpiReport(true, "2", true, "somedefinition");

    KpiResponseDto kpiResponseDto1 = new KpiResponseDto();
    kpiResponseDto1.setReportId(reportId1);
    kpiResponseDto1.setReportName("My test report");
    kpiResponseDto1.setValue("1.0");
    kpiResponseDto1.setTarget("1");
    kpiResponseDto1.setIsBelow(true);
    kpiResponseDto1.setType(KpiType.QUALITY);
    kpiResponseDto1.setMeasure(ViewProperty.FREQUENCY);

    KpiResponseDto kpiResponseDto2 = new KpiResponseDto();
    kpiResponseDto2.setReportId(reportId2);
    kpiResponseDto2.setReportName("My test report");
    kpiResponseDto2.setTarget("2");
    kpiResponseDto2.setValue("1.0");
    kpiResponseDto2.setIsBelow(true);
    kpiResponseDto2.setType(KpiType.QUALITY);
    kpiResponseDto2.setMeasure(ViewProperty.FREQUENCY);

    KpiResponseDto kpiResponseDto3 = new KpiResponseDto();
    kpiResponseDto3.setReportId(reportId3);
    kpiResponseDto3.setReportName("My test report");
    kpiResponseDto3.setValue("1.0");
    kpiResponseDto3.setTarget("1");
    kpiResponseDto3.setIsBelow(true);
    kpiResponseDto3.setType(KpiType.QUALITY);
    kpiResponseDto3.setMeasure(ViewProperty.FREQUENCY);

    KpiResponseDto kpiResponseDto4 = new KpiResponseDto();
    kpiResponseDto4.setReportId(reportId4);
    kpiResponseDto4.setReportName("My test report");
    kpiResponseDto4.setTarget("2");
    kpiResponseDto4.setValue("1.0");
    kpiResponseDto4.setIsBelow(true);
    kpiResponseDto4.setType(KpiType.QUALITY);
    kpiResponseDto4.setMeasure(ViewProperty.FREQUENCY);

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(2)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getKpis)
      .map(tuple2 -> Tuple.tuple(tuple2.toList().get(0), Set.copyOf((List)tuple2.toList().get(1))))
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          PROCESS_DEFINITION_KEY,
          Set.of(kpiResponseDto1, kpiResponseDto2)
        ),
        Tuple.tuple(
          "somedefinition",
          Set.of(kpiResponseDto3, kpiResponseDto4)
        )
      );
  }

  @Test
  public void kpiTypeGetsAssignedCorrectly() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = new ExecutedFlowNodeFilterDto();
    executedFlowNodeFilterDto.setFilterLevel(FilterApplicationLevel.INSTANCE);
    String reportId1 = createKpiReport(true, "1", true, PROCESS_DEFINITION_KEY);
    String reportId2 = createKpiReportWithMeasures(true, "2", true, PROCESS_DEFINITION_KEY, ViewProperty.DURATION);
    String reportId3 = createKpiReportWithMeasures(true, "3", true, PROCESS_DEFINITION_KEY, ViewProperty.FREQUENCY);
    String reportId4 = createKpiReportWithMeasures(true, "4", true, PROCESS_DEFINITION_KEY, ViewProperty.PERCENTAGE);

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(1);
    assertThat(processes.get(0).getKpis()).extracting(KpiResponseDto::getReportId, KpiResponseDto::getType)
      .containsExactlyInAnyOrder(
        Tuple.tuple(reportId1, KpiType.QUALITY),
        Tuple.tuple(reportId2, KpiType.TIME),
        Tuple.tuple(reportId3, KpiType.QUALITY),
        Tuple.tuple(reportId4, KpiType.QUALITY)
      );
  }

  private String createKpiReport(final Boolean isBelow, final String target, final Boolean isKpi,
                                 final String definitionKey) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(definitionKey)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(isKpi);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setIsBelow(isBelow);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setTarget(target);
    return reportClient.createSingleProcessReport(reportDataDto);
  }

  private String createKpiReportWithMeasures(final Boolean isBelow, final String target, final Boolean isKpi,
                                             final String definitionKey, final ViewProperty viewProperty) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(definitionKey)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(isKpi);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setIsBelow(isBelow);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setTarget(target);
    reportDataDto.getView().setProperties(List.of(viewProperty));
    return reportClient.createSingleProcessReport(reportDataDto);
  }

}
