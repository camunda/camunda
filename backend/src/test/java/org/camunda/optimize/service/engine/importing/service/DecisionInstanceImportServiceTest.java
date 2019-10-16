/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricDecisionInputInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionOutputInstanceDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.plugin.DecisionInputImportAdapterProvider;
import org.camunda.optimize.plugin.DecisionOutputImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeDecisionDefinitionFetchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DecisionInstanceImportServiceTest {

  private static final String VERSION_RESULT = "VERSION";

  @Mock
  private DecisionInstanceWriter decisionInstanceWriter;

  @Mock
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  @Mock
  private EngineContext engineContext;

  @Mock
  private DecisionDefinitionResolverService decisionDefinitionResolverService;

  @Mock
  private DecisionInputImportAdapterProvider decisionInputImportAdapterProvider;

  @Mock
  private DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider;

  private DecisionInstanceImportService underTest;

  @BeforeEach
  public void init() {
    when(engineContext.getEngineAlias()).thenReturn("1");
    when(decisionDefinitionResolverService.getVersionForDecisionDefinitionId(any()))
      .thenReturn(Optional.of(VERSION_RESULT));

    this.underTest = new DecisionInstanceImportService(
      elasticsearchImportJobExecutor, engineContext, decisionInstanceWriter,
      decisionDefinitionResolverService, decisionInputImportAdapterProvider, decisionOutputImportAdapterProvider
    );
  }

  @Test
  public void testMappingOfAllFieldToOptimizeDto() throws OptimizeDecisionDefinitionFetchException {
    // given
    HistoricDecisionInstanceDto historicDecisionInstanceDto = new HistoricDecisionInstanceDto();
    historicDecisionInstanceDto.setId(UUID.randomUUID().toString());
    historicDecisionInstanceDto.setDecisionDefinitionId(UUID.randomUUID().toString());
    historicDecisionInstanceDto.setDecisionDefinitionKey("decisionDefinitionKey");
    historicDecisionInstanceDto.setDecisionDefinitionName("decisionDefinitionName");
    historicDecisionInstanceDto.setEvaluationTime(OffsetDateTime.now());
    historicDecisionInstanceDto.setProcessDefinitionId(UUID.randomUUID().toString());
    historicDecisionInstanceDto.setProcessDefinitionKey("processDefinitionKey");
    historicDecisionInstanceDto.setProcessInstanceId(UUID.randomUUID().toString());
    historicDecisionInstanceDto.setRootProcessInstanceId(UUID.randomUUID().toString());
    historicDecisionInstanceDto.setActivityId(UUID.randomUUID().toString());
    historicDecisionInstanceDto.setCollectResultValue(2.0);
    historicDecisionInstanceDto.setRootDecisionInstanceId(UUID.randomUUID().toString());

    addAllSupportedInputVariables(historicDecisionInstanceDto);

    addAllSupportedOutputVairables(historicDecisionInstanceDto);

    // when
    final DecisionInstanceDto decisionInstanceDto = underTest.mapEngineEntityToOptimizeEntity(
      historicDecisionInstanceDto
    );

    // then
    assertThat(
      decisionInstanceDto.getDecisionDefinitionId(),
      is(historicDecisionInstanceDto.getDecisionDefinitionId())
    );
    assertThat(decisionInstanceDto.getDecisionInstanceId(), is(historicDecisionInstanceDto.getId()));
    assertThat(
      decisionInstanceDto.getDecisionDefinitionKey(),
      is(historicDecisionInstanceDto.getDecisionDefinitionKey())
    );
    assertThat(decisionInstanceDto.getDecisionDefinitionVersion(), is(VERSION_RESULT));
    assertThat(decisionInstanceDto.getProcessDefinitionId(), is(historicDecisionInstanceDto.getProcessDefinitionId()));
    assertThat(
      decisionInstanceDto.getProcessDefinitionKey(),
      is(historicDecisionInstanceDto.getProcessDefinitionKey())
    );
    assertThat(decisionInstanceDto.getEvaluationDateTime(), is(historicDecisionInstanceDto.getEvaluationTime()));
    assertThat(decisionInstanceDto.getProcessInstanceId(), is(historicDecisionInstanceDto.getProcessInstanceId()));
    assertThat(
      decisionInstanceDto.getRootProcessInstanceId(),
      is(historicDecisionInstanceDto.getRootProcessInstanceId())
    );
    assertThat(decisionInstanceDto.getActivityId(), is(historicDecisionInstanceDto.getActivityId()));
    assertThat(decisionInstanceDto.getCollectResultValue(), is(historicDecisionInstanceDto.getCollectResultValue()));
    assertThat(
      decisionInstanceDto.getRootDecisionInstanceId(),
      is(historicDecisionInstanceDto.getRootDecisionInstanceId())
    );

    assertThat(decisionInstanceDto.getInputs().size(), is(historicDecisionInstanceDto.getInputs().size()));
    assertAllInputsWithAllFieldsAvailable(historicDecisionInstanceDto, decisionInstanceDto);

    assertThat(decisionInstanceDto.getOutputs().size(), is(historicDecisionInstanceDto.getOutputs().size()));
    assertAllOutputsWithAllFieldsAvailable(historicDecisionInstanceDto, decisionInstanceDto);

    assertThat(decisionInstanceDto.getEngine(), is("1"));
  }

  @Test
  public void testSkipUnsupportedInputTypesWhenMappingToOptimizeDto() throws OptimizeDecisionDefinitionFetchException {
    // given
    HistoricDecisionInstanceDto historicDecisionInstanceDto = new HistoricDecisionInstanceDto();

    addAllSupportedInputVariables(historicDecisionInstanceDto);

    final HistoricDecisionInputInstanceDto nullTypeInput = new HistoricDecisionInputInstanceDto();
    nullTypeInput.setId(UUID.randomUUID().toString());
    nullTypeInput.setClauseId(UUID.randomUUID().toString());
    nullTypeInput.setClauseName("clauseName_null");
    nullTypeInput.setType(null);
    nullTypeInput.setValue(null);
    historicDecisionInstanceDto.getInputs().add(nullTypeInput);

    final HistoricDecisionInputInstanceDto complexTypeInput = new HistoricDecisionInputInstanceDto();
    complexTypeInput.setId(UUID.randomUUID().toString());
    complexTypeInput.setClauseId(UUID.randomUUID().toString());
    complexTypeInput.setClauseName("clauseName_complex");
    complexTypeInput.setType("customType");
    complexTypeInput.setValue("{}");
    historicDecisionInstanceDto.getInputs().add(complexTypeInput);


    // when
    final DecisionInstanceDto decisionInstanceDto = underTest.mapEngineEntityToOptimizeEntity(
      historicDecisionInstanceDto
    );

    // then
    assertThat(decisionInstanceDto.getInputs().size(), is(historicDecisionInstanceDto.getInputs().size() - 2));
    assertAllInputsWithAllFieldsAvailable(historicDecisionInstanceDto, decisionInstanceDto);
  }

  @Test
  public void testSkipUnsupportedOutputTypesWhenMappingToOptimizeDto() throws OptimizeDecisionDefinitionFetchException {
    // given
    HistoricDecisionInstanceDto historicDecisionInstanceDto = new HistoricDecisionInstanceDto();

    addAllSupportedOutputVairables(historicDecisionInstanceDto);

    final HistoricDecisionOutputInstanceDto nullTypeOutput = new HistoricDecisionOutputInstanceDto();
    nullTypeOutput.setId(UUID.randomUUID().toString());
    nullTypeOutput.setClauseId(UUID.randomUUID().toString());
    nullTypeOutput.setClauseName("clauseName_null");
    nullTypeOutput.setType(null);
    nullTypeOutput.setValue(null);
    nullTypeOutput.setRuleId(UUID.randomUUID().toString());
    nullTypeOutput.setRuleOrder(1);
    nullTypeOutput.setVariableName("varName_null");
    historicDecisionInstanceDto.getOutputs().add(nullTypeOutput);

    final HistoricDecisionOutputInstanceDto complexTypeOutput = new HistoricDecisionOutputInstanceDto();
    complexTypeOutput.setId(UUID.randomUUID().toString());
    complexTypeOutput.setClauseId(UUID.randomUUID().toString());
    complexTypeOutput.setClauseName("clauseName_complex");
    complexTypeOutput.setType("customType");
    complexTypeOutput.setValue("{}");
    complexTypeOutput.setRuleId(UUID.randomUUID().toString());
    complexTypeOutput.setRuleOrder(1);
    complexTypeOutput.setVariableName("varName_complex");
    historicDecisionInstanceDto.getOutputs().add(complexTypeOutput);


    // when
    final DecisionInstanceDto decisionInstanceDto = underTest.mapEngineEntityToOptimizeEntity(
      historicDecisionInstanceDto
    );

    // then
    assertThat(decisionInstanceDto.getOutputs().size(), is(historicDecisionInstanceDto.getOutputs().size() - 2));
    assertAllOutputsWithAllFieldsAvailable(historicDecisionInstanceDto, decisionInstanceDto);
  }

  private void assertAllInputsWithAllFieldsAvailable(final HistoricDecisionInstanceDto historicDecisionInstanceDto,
                                                     final DecisionInstanceDto decisionInstanceDto) {
    decisionInstanceDto.getInputs().forEach(inputInstanceDto -> {
      final HistoricDecisionInputInstanceDto historicInput = historicDecisionInstanceDto.getInputs()
        .stream()
        .filter(inputInstanceEngine -> inputInstanceEngine.getId().equals(inputInstanceDto.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Couldn't find input with id: " + inputInstanceDto.getId()));

      assertThat(inputInstanceDto.getClauseId(), is(historicInput.getClauseId()));
      assertThat(inputInstanceDto.getClauseName(), is(historicInput.getClauseName()));
      assertThat(inputInstanceDto.getType().getId(), is(historicInput.getType()));
      assertThat(inputInstanceDto.getValue(), is(String.valueOf(historicInput.getValue())));
    });
  }

  private void assertAllOutputsWithAllFieldsAvailable(final HistoricDecisionInstanceDto historicDecisionInstanceDto,
                                                      final DecisionInstanceDto decisionInstanceDto) {
    decisionInstanceDto.getOutputs().forEach(outputInstanceDto -> {
      final HistoricDecisionOutputInstanceDto historicOutput = historicDecisionInstanceDto.getOutputs()
        .stream()
        .filter(outputInstanceEngine -> outputInstanceEngine.getId().equals(outputInstanceDto.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Couldn't find output with id: " + outputInstanceDto.getId()));

      assertThat(outputInstanceDto.getClauseId(), is(historicOutput.getClauseId()));
      assertThat(outputInstanceDto.getClauseName(), is(historicOutput.getClauseName()));
      assertThat(outputInstanceDto.getRuleId(), is(historicOutput.getRuleId()));
      assertThat(outputInstanceDto.getRuleOrder(), is(historicOutput.getRuleOrder()));
      assertThat(outputInstanceDto.getVariableName(), is(historicOutput.getVariableName()));
      assertThat(outputInstanceDto.getType().getId(), is(historicOutput.getType()));
      assertThat(outputInstanceDto.getValue(), is(String.valueOf(historicOutput.getValue())));
    });
  }

  private void addAllSupportedInputVariables(final HistoricDecisionInstanceDto historicDecisionInstanceDto) {
    for (VariableType type : ReportConstants.ALL_SUPPORTED_VARIABLE_TYPES) {
      HistoricDecisionInputInstanceDto input = new HistoricDecisionInputInstanceDto();
      input.setId(UUID.randomUUID().toString());
      input.setClauseId(UUID.randomUUID().toString());
      input.setClauseName("clauseName_" + type);
      input.setType(type.getId());
      input.setValue(getSampleValueForType(type));
      historicDecisionInstanceDto.getInputs().add(input);
    }
  }

  private void addAllSupportedOutputVairables(final HistoricDecisionInstanceDto historicDecisionInstanceDto) {
    for (VariableType type : ReportConstants.ALL_SUPPORTED_VARIABLE_TYPES) {
      HistoricDecisionOutputInstanceDto output = new HistoricDecisionOutputInstanceDto();
      output.setId(UUID.randomUUID().toString());
      output.setClauseId(UUID.randomUUID().toString());
      output.setClauseName("clauseName_" + type);
      output.setType(type.getId());
      output.setValue(getSampleValueForType(type));
      output.setRuleId(UUID.randomUUID().toString());
      output.setRuleOrder(1);
      output.setVariableName("varName_" + type);

      historicDecisionInstanceDto.getOutputs().add(output);
    }
  }

  private Object getSampleValueForType(final VariableType type) {
    switch (type) {
      case STRING:
        return "test";
      case INTEGER:
        return 5;
      case SHORT:
        return Short.valueOf("1");
      case LONG:
        return 6L;
      case DOUBLE:
        return 7.0D;
      case BOOLEAN:
        return true;
      case DATE:
        return OffsetDateTime.now();
      default:
        throw new IllegalStateException("Unhandled type: " + type);
    }
  }
}
