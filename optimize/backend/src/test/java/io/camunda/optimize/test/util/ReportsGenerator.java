/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static io.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;

import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.ProcessReportDataType;
import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper;
import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import io.camunda.optimize.test.util.decision.DecisionReportDataType;
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

@Slf4j
public class ReportsGenerator {

  @SuppressWarnings("checkstyle:constantname")
  private static final Random randomGen = new Random();
  private static final String DOUBLE_VAR = "doubleVar";
  @SuppressWarnings("checkstyle:constantname")
  private static final SimpleEngineClient client =
      new SimpleEngineClient(IntegrationTestConfigurationUtil.getEngineRestEndpoint() + "default");

  public static List<SingleReportDataDto> createAllReportTypesForAllDefinitions() {
    final List<ProcessDefinitionEngineDto> processDefinitions =
        client.getLatestProcessDefinitions();
    final List<DecisionDefinitionEngineDto> decisionDefinitions =
        client.getLatestDecisionDefinitions();
    return createAllReportTypesForDefinitions(processDefinitions, decisionDefinitions);
  }

  public static List<SingleReportDataDto> createAllReportTypesForDefinitions(
      final List<ProcessDefinitionEngineDto> processDefinitions,
      final List<DecisionDefinitionEngineDto> decisionDefinitions) {
    final List<DecisionReportDataDto> decisionReportDataDtos =
        decisionDefinitions.stream()
            .map(ReportsGenerator::createDecisionReportsFromDefinition)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    final List<ProcessReportDataDto> processReportDataDtos =
        processDefinitions.stream()
            .map(ReportsGenerator::createProcessReportsFromDefinition)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    final List<SingleReportDataDto> reports = new ArrayList<>();
    reports.addAll(decisionReportDataDtos);
    reports.addAll(processReportDataDtos);
    return reports;
  }

  private static List<ProcessReportDataDto> createProcessReportsFromDefinition(
      final ProcessDefinitionEngineDto definition) {
    final List<ProcessReportDataDto> reports = new ArrayList<>();
    final ProcessPartDto processPart = createProcessPart(definition);
    for (final ProcessReportDataType reportDataType : ProcessReportDataType.values()) {
      final TemplatedProcessReportDataBuilder reportDataBuilder =
          TemplatedProcessReportDataBuilder.createReportData()
              .setReportDataType(reportDataType)
              .setProcessDefinitionKey(definition.getKey())
              .setProcessDefinitionVersion(definition.getVersionAsString())
              .setVariableName(DOUBLE_VAR)
              .setVariableType(VariableType.DOUBLE)
              .setGroupByDateInterval(AggregateByDateUnit.WEEK)
              .setStartFlowNodeId(processPart.getStart())
              .setEndFlowNodeId(processPart.getEnd())
              .setFilter(createProcessFilter());
      final ProcessReportDataDto reportDataLatestDefinitionVersion = reportDataBuilder.build();
      reports.add(reportDataLatestDefinitionVersion);
      reportDataBuilder.setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS);
      final ProcessReportDataDto reportDataAllDefinitionVersions = reportDataBuilder.build();
      reports.add(reportDataAllDefinitionVersions);
    }
    return reports;
  }

  private static List<DecisionReportDataDto> createDecisionReportsFromDefinition(
      final DecisionDefinitionEngineDto definition) {
    final DmnFilterData dmnFilterData = retrieveVariablesForDecision(definition);
    final List<DecisionFilterDto<?>> decisionFilters = createDecisionFilters(dmnFilterData);
    return Arrays.stream(DecisionReportDataType.values())
        .map(
            type ->
                DecisionReportDataBuilder.create()
                    .setReportDataType(type)
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

  private static ProcessPartDto createProcessPart(final ProcessDefinitionEngineDto definition) {
    final String xml = client.getProcessDefinitionXml(definition.getId()).getBpmn20Xml();
    final ModelInstance model = Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));
    final String startFlowNodeId =
        model.getModelElementsByType(StartEvent.class).stream().findFirst().get().getId();
    final String endFlowNodeId =
        model.getModelElementsByType(EndEvent.class).stream().findFirst().get().getId();
    final ProcessPartDto processPart = new ProcessPartDto();
    processPart.setStart(startFlowNodeId);
    processPart.setEnd(endFlowNodeId);
    return processPart;
  }

  private static List<DecisionFilterDto<?>> createDecisionFilters(final DmnFilterData data) {
    final List<DecisionFilterDto<?>> result = new ArrayList<>();

    result.add(createInputVariableFilter(data));
    result.add(createOutputVariableFilter(data));
    result.add(
        DecisionFilterUtilHelper.createFixedEvaluationDateFilter(
            OffsetDateTime.now().minusYears(200L), OffsetDateTime.now().plusYears(100L)));

    return result;
  }

  private static DecisionFilterDto createInputVariableFilter(final DmnFilterData filterData) {
    switch (filterData.getInputType()) {
      case STRING:
        return DecisionFilterUtilHelper.createStringInputVariableFilter(
            filterData.getInputName(), FilterOperator.IN, filterData.getPossibleInputValue());
      case BOOLEAN:
        return DecisionFilterUtilHelper.createBooleanInputVariableFilter(
            filterData.getInputName(), true);
      case DOUBLE:
      case INTEGER:
      case LONG:
      case SHORT:
        return DecisionFilterUtilHelper.createNumericInputVariableFilter(
            filterData.getInputName(),
            filterData.getInputType(),
            FilterOperator.LESS_THAN,
            Collections.singletonList("100"));
      case DATE:
        return DecisionFilterUtilHelper.createFixedDateInputVariableFilter(
            filterData.getInputName(),
            OffsetDateTime.now().minusYears(200L),
            OffsetDateTime.now().plusYears(100L));
      default:
        throw new OptimizeRuntimeException(
            "Unknown input variable type to create decision filter for.");
    }
  }

  private static DecisionFilterDto createOutputVariableFilter(final DmnFilterData filterData) {
    switch (filterData.getOutputType()) {
      case STRING:
        return DecisionFilterUtilHelper.createStringOutputVariableFilter(
            filterData.getInputName(), FilterOperator.IN, filterData.getPossibleOutputValue());
      case BOOLEAN:
        return DecisionFilterUtilHelper.createBooleanOutputVariableFilter(
            filterData.getOutputName(), Collections.singletonList(true));
      case DOUBLE:
      case INTEGER:
      case LONG:
      case SHORT:
        return DecisionFilterUtilHelper.createNumericOutputVariableFilter(
            filterData.getOutputName(),
            filterData.getOutputType(),
            FilterOperator.LESS_THAN,
            Collections.singletonList("100"));
      case DATE:
        return DecisionFilterUtilHelper.createFixedDateOutputVariableFilter(
            filterData.getOutputName(),
            OffsetDateTime.now().minusYears(200L),
            OffsetDateTime.now().plusYears(100L));
      default:
        throw new OptimizeRuntimeException(
            "Unknown output variable type to create decision filter for.");
    }
  }

  private static DmnFilterData retrieveVariablesForDecision(
      final DecisionDefinitionEngineDto definition) {
    final DmnFilterData resultData = new DmnFilterData();

    final DecisionTable decisionTable = getDecisionTableForDefinition(definition);

    final Input input = decisionTable.getInputs().stream().findFirst().get();
    final Output output = decisionTable.getOutputs().stream().findFirst().get();

    assignPossibleVariableValues(resultData, decisionTable);

    assignInputTypeAndName(resultData, input);
    assignOutputTypeAndName(resultData, output);

    return resultData;
  }

  private static void assignOutputTypeAndName(final DmnFilterData resultData, final Output output) {
    final String outputTypeString = output.getTypeRef();
    final VariableType outputType = VariableType.getTypeForId(outputTypeString);
    resultData.setOutputType(outputType);

    final String outputName = output.getName();
    resultData.setOutputName(outputName);
  }

  private static void assignInputTypeAndName(final DmnFilterData resultData, final Input input) {
    final InputExpression inputExpression =
        input.getChildElementsByType(InputExpression.class).stream().findFirst().get();

    final String inputTypeString = inputExpression.getAttributeValue("typeRef");
    final VariableType inputType = VariableType.getTypeForId(inputTypeString);
    resultData.setInputType(inputType);

    final String inputName = inputExpression.getUniqueChildElementByType(Text.class)
        .getTextContent();
    resultData.setInputName(inputName);
  }

  private static void assignPossibleVariableValues(
      final DmnFilterData resultData, final DecisionTable decisionTable) {
    final Rule rule = decisionTable.getRules().stream().findFirst().get();
    final InputEntry inputEntry = rule.getInputEntries().stream().findFirst().get();
    final OutputEntry outputEntry = rule.getOutputEntries().stream().findFirst().get();

    final String possibleInputValue = inputEntry.getTextContent();
    final String possibleOutputValue = outputEntry.getTextContent();

    resultData.setPossibleInputValue(possibleInputValue);
    resultData.setPossibleOutputValue(possibleOutputValue);
  }

  private static DecisionTable getDecisionTableForDefinition(
      final DecisionDefinitionEngineDto definition) {
    final String xml = client.getDecisionDefinitionXml(definition.getId()).getDmnXml();
    final DmnModelInstance model = Dmn.readModelFromStream(
        new ByteArrayInputStream(xml.getBytes()));

    final Collection<Decision> decisions = model.getDefinitions()
        .getChildElementsByType(Decision.class);
    final Optional<Decision> optionalDecision = decisions.stream().findFirst();
    final Decision decision = optionalDecision.get();
    return (DecisionTable) decision.getExpression();
  }

  private static List<ProcessFilterDto<?>> createProcessFilter() {
    // @formatter:off
    return ProcessFilterBuilder.filter()
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
  private static final class DmnFilterData {

    private String inputName;
    private VariableType inputType;
    private String possibleInputValue;

    private String outputName;
    private VariableType outputType;
    private String possibleOutputValue;
  }
}
