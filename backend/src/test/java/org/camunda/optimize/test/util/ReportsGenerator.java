/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.util.client.SimpleEngineClient;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.YEARS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;

public class ReportsGenerator {

  private static final Random randomGen = new Random();
  public static final String DOUBLE_VAR = "doubleVar";
  private static SimpleEngineClient client =
    new SimpleEngineClient(IntegrationTestConfigurationUtil.getEngineRestEndpoint() + "default");

  public static List<SingleReportDataDto> createAllPossibleReports() {
    List<ProcessDefinitionEngineDto> latestDefinitionVersions =
      client.getLatestProcessDefinitions();

    List<DecisionDefinitionEngineDto> latestDecisionDefs =
      client.getLatestDecisionDefinitions();


    List<ProcessReportDataDto> processReportDataDtos = latestDefinitionVersions
      .stream()
      .flatMap(v -> createProcessReportsFromDefinition(v).stream())
      .collect(Collectors.toList());

    List<DecisionReportDataDto> decisionReportDataDtos = latestDecisionDefs
      .stream()
      .flatMap(v -> createDecisionReportsFromDefinition(v).stream())
      .collect(Collectors.toList());

    List<SingleReportDataDto> reports = new ArrayList<>();
    reports.addAll(decisionReportDataDtos);
    reports.addAll(processReportDataDtos);
    return reports;
  }

  private static List<ProcessReportDataDto> createProcessReportsFromDefinition(ProcessDefinitionEngineDto definition) {
    List<ProcessReportDataDto> reports = new ArrayList<>();
    ProcessPartDto processPart = createProcessPart(definition);
    for (ProcessReportDataType reportDataType : ProcessReportDataType.values()) {
      TemplatedProcessReportDataBuilder reportDataBuilder = TemplatedProcessReportDataBuilder.createReportData()
        .setReportDataType(reportDataType)
        .setProcessDefinitionKey(definition.getKey())
        .setProcessDefinitionVersion(definition.getVersionAsString())
        .setVariableName(DOUBLE_VAR)
        .setVariableType(VariableType.DOUBLE)
        .setDateInterval(GroupByDateUnit.WEEK)
        .setStartFlowNodeId(processPart.getStart())
        .setEndFlowNodeId(processPart.getEnd())
        .setFilter(createProcessFilter());

      ProcessReportDataDto reportDataLatestDefinitionVersion =
        reportDataBuilder.build();
      reports.add(reportDataLatestDefinitionVersion);
      reportDataBuilder.setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS);
      ProcessReportDataDto reportDataAllDefinitionVersions = reportDataBuilder.build();
      reports.add(reportDataAllDefinitionVersions);
    }
    return reports;
  }

  private static List<DecisionReportDataDto> createDecisionReportsFromDefinition(DecisionDefinitionEngineDto definition) {
    List<DecisionReportDataDto> reports = new ArrayList<>();

    for (DecisionReportDataType type : DecisionReportDataType.values()) {
      DecisionReportDataDto reportDataDto = DecisionReportDataBuilder.create().setReportDataType(type)
        .setDecisionDefinitionKey(definition.getKey())
        .setDecisionDefinitionVersion(definition.getVersionAsString())
        .setDateInterval(GroupByDateUnit.DAY)
        .setVariableName(DOUBLE_VAR)
        .setVariableType(VariableType.DOUBLE)
        .setVariableId(INPUT_AMOUNT_ID)
        .build();
      reports.add(reportDataDto);
    }
    return reports;
  }

  private static ProcessPartDto createProcessPart(ProcessDefinitionEngineDto definition) {
    String xml = client
      .getProcessDefinitionXml(definition.getId())
      .getBpmn20Xml();
    ModelInstance model = Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));
    String startFlowNodeId = model.getModelElementsByType(StartEvent.class).stream().findFirst().get().getId();
    String endFlowNodeId = model.getModelElementsByType(EndEvent.class).stream().findFirst().get().getId();
    ProcessPartDto processPart = new ProcessPartDto();
    processPart.setStart(startFlowNodeId);
    processPart.setEnd(endFlowNodeId);
    return processPart;
  }

  private static List<ProcessFilterDto<?>> createProcessFilter() {
    // @formatter:off
    return ProcessFilterBuilder
      .filter()
        .variable()
        .booleanType()
        .values(Collections.singletonList(String.valueOf(randomGen.nextBoolean())))
        .name("boolVar")
      .add()
        .fixedStartDate()
        .start(OffsetDateTime.now().minusYears(200L))
        .end(OffsetDateTime.now().plusYears(100L))
      .add()
        .fixedEndDate()
        .start(OffsetDateTime.now().minusYears(100L))
        .end(OffsetDateTime.now().plusYears(100L))
      .add()
        .completedInstancesOnly()
      .add()
        .duration()
        .unit(YEARS.toString().toLowerCase())
        .value((long) 100)
        .operator(LESS_THAN)
      .add()
      .buildList();
  }
  // @formatter:on
}
