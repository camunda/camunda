/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.InputEntry;
import org.camunda.bpm.model.dmn.instance.InputExpression;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.bpm.model.dmn.instance.OutputEntry;
import org.camunda.bpm.model.dmn.instance.Rule;
import org.camunda.bpm.model.dmn.instance.Text;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.util.client.SimpleEngineClient;
import org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;

@Slf4j
public class ReportsGenerator {

  private static final Random randomGen = new Random();
  private static final String DOUBLE_VAR = "doubleVar";
  private static final SimpleEngineClient client = new SimpleEngineClient(
    IntegrationTestConfigurationUtil.getEngineRestEndpoint() + "default"
  );

  public static List<SingleReportDataDto> createAllReportTypesForAllDefinitions() {
    final List<ProcessDefinitionEngineDto> processDefinitions = client.getLatestProcessDefinitions();
    final List<DecisionDefinitionEngineDto> decisionDefinitions = client.getLatestDecisionDefinitions();
    return createAllReportTypesForDefinitions(processDefinitions, decisionDefinitions);
  }

  public static List<SingleReportDataDto> createAllReportTypesForDefinitions(final List<ProcessDefinitionEngineDto> processDefinitions,
                                                                             final List<DecisionDefinitionEngineDto> decisionDefinitions) {
    final List<DecisionReportDataDto> decisionReportDataDtos = decisionDefinitions
      .stream()
      .map(ReportsGenerator::createDecisionReportsFromDefinition)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    final List<ProcessReportDataDto> processReportDataDtos = processDefinitions
      .stream()
      .map(ReportsGenerator::createProcessReportsFromDefinition)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    final List<SingleReportDataDto> reports = new ArrayList<>();
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
        .setGroupByDateInterval(AggregateByDateUnit.WEEK)
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
    DmnFilterData dmnFilterData = retrieveVariablesForDecision(definition);
    List<DecisionFilterDto<?>> decisionFilters = createDecisionFilters(dmnFilterData);
    return Arrays.stream(DecisionReportDataType.values())
      .map(type -> DecisionReportDataBuilder.create().setReportDataType(type)
        .setDecisionDefinitionKey(definition.getKey())
        .setDecisionDefinitionVersion(definition.getVersionAsString())
        .setDateInterval(AggregateByDateUnit.DAY)
        .setVariableName(DOUBLE_VAR)
        .setVariableType(VariableType.DOUBLE)
        .setVariableId(INPUT_AMOUNT_ID)
        .setFilter(decisionFilters)
        .build())
      .collect(Collectors.toList());
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

  private static List<DecisionFilterDto<?>> createDecisionFilters(DmnFilterData data) {
    List<DecisionFilterDto<?>> result = new ArrayList<>();

    result.add(createInputVariableFilter(data));
    result.add(createOutputVariableFilter(data));
    result.add(
      DecisionFilterUtilHelper.createFixedEvaluationDateFilter(
        OffsetDateTime.now().minusYears(200L),
        OffsetDateTime.now().plusYears(100L)
      )
    );

    return result;
  }

  private static DecisionFilterDto createInputVariableFilter(DmnFilterData filterData) {
    switch (filterData.getInputType()) {
      case STRING:
        return DecisionFilterUtilHelper.createStringInputVariableFilter(
          filterData.getInputName(),
          FilterOperator.IN,
          filterData.getPossibleInputValue()
        );
      case BOOLEAN:
        return DecisionFilterUtilHelper.createBooleanInputVariableFilter(
          filterData.getInputName(), true
        );
      case DOUBLE:
      case INTEGER:
      case LONG:
      case SHORT:
        return DecisionFilterUtilHelper.createNumericInputVariableFilter(
          filterData.getInputName(),
          filterData.getInputType(),
          FilterOperator.LESS_THAN,
          Collections.singletonList("100")
        );
      case DATE:
        return DecisionFilterUtilHelper.createFixedDateInputVariableFilter(
          filterData.getInputName(),
          OffsetDateTime.now().minusYears(200L),
          OffsetDateTime.now().plusYears(100L)
        );
      default:
        throw new OptimizeRuntimeException("Unknown input variable type to create decision filter for.");
    }
  }

  private static DecisionFilterDto createOutputVariableFilter(DmnFilterData filterData) {
    switch (filterData.getOutputType()) {
      case STRING:
        return DecisionFilterUtilHelper.createStringOutputVariableFilter(
          filterData.getInputName(),
          FilterOperator.IN,
          filterData.getPossibleOutputValue()
        );
      case BOOLEAN:
        return DecisionFilterUtilHelper.createBooleanOutputVariableFilter(
          filterData.getOutputName(), Collections.singletonList(true)
        );
      case DOUBLE:
      case INTEGER:
      case LONG:
      case SHORT:
        return DecisionFilterUtilHelper.createNumericOutputVariableFilter(
          filterData.getOutputName(),
          filterData.getOutputType(),
          FilterOperator.LESS_THAN,
          Collections.singletonList("100")
        );
      case DATE:
        return DecisionFilterUtilHelper.createFixedDateOutputVariableFilter(
          filterData.getOutputName(),
          OffsetDateTime.now().minusYears(200L),
          OffsetDateTime.now().plusYears(100L)
        );
      default:
        throw new OptimizeRuntimeException("Unknown output variable type to create decision filter for.");
    }
  }

  private static DmnFilterData retrieveVariablesForDecision(DecisionDefinitionEngineDto definition) {
    DmnFilterData resultData = new DmnFilterData();

    DecisionTable decisionTable = getDecisionTableForDefinition(definition);

    Input input = decisionTable.getInputs().stream().findFirst().get();
    Output output = decisionTable.getOutputs().stream().findFirst().get();

    assignPossibleVariableValues(resultData, decisionTable);

    assignInputTypeAndName(resultData, input);
    assignOutputTypeAndName(resultData, output);

    return resultData;
  }

  private static void assignOutputTypeAndName(DmnFilterData resultData, Output output) {
    String outputTypeString = output.getTypeRef();
    VariableType outputType = VariableType.getTypeForId(outputTypeString);
    resultData.setOutputType(outputType);

    String outputName = output.getName();
    resultData.setOutputName(outputName);
  }

  private static void assignInputTypeAndName(DmnFilterData resultData, Input input) {
    InputExpression inputExpression = input.getChildElementsByType(InputExpression.class)
      .stream()
      .findFirst()
      .get();

    String inputTypeString = inputExpression
      .getAttributeValue("typeRef");
    VariableType inputType = VariableType.getTypeForId(inputTypeString);
    resultData.setInputType(inputType);

    String inputName = inputExpression
      .getUniqueChildElementByType(Text.class)
      .getTextContent();
    resultData.setInputName(inputName);
  }

  private static void assignPossibleVariableValues(DmnFilterData resultData, DecisionTable decisionTable) {
    Rule rule = decisionTable.getRules()
      .stream()
      .findFirst()
      .get();
    InputEntry inputEntry = rule
      .getInputEntries()
      .stream()
      .findFirst()
      .get();
    OutputEntry outputEntry = rule
      .getOutputEntries()
      .stream()
      .findFirst()
      .get();

    String possibleInputValue = inputEntry.getTextContent();
    String possibleOutputValue = outputEntry.getTextContent();

    resultData.setPossibleInputValue(possibleInputValue);
    resultData.setPossibleOutputValue(possibleOutputValue);
  }

  private static DecisionTable getDecisionTableForDefinition(DecisionDefinitionEngineDto definition) {
    String xml = client.getDecisionDefinitionXml(definition.getId()).getDmnXml();
    DmnModelInstance model = Dmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));

    Collection<Decision> decisions = model.getDefinitions().getChildElementsByType(Decision.class);
    Optional<Decision> optionalDecision = decisions.stream().findFirst();
    Decision decision = optionalDecision.get();
    return (DecisionTable) decision.getExpression();
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
        .completedInstancesOnly()
      .add()
        .duration()
        .unit(DurationUnit.MONTHS)
        .value((long) 100)
        .operator(LESS_THAN)
      .add()
      .buildList();
  }
  // @formatter:on

  @NoArgsConstructor
  @Setter
  @Getter
  private static class DmnFilterData {
    private String inputName;
    private VariableType inputType;
    private String possibleInputValue;

    private String outputName;
    private VariableType outputType;
    private String possibleOutputValue;
  }
}
