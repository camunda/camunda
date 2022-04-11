/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.decision.variable.string;

import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.DmnModels.INPUT_SEASON_ID;
import static org.camunda.optimize.util.DmnModels.INTEGER_INPUT_ID;
import static org.camunda.optimize.util.DmnModels.STRING_INPUT_ID;

public abstract class AbstractDecisionStringContainsVariableQueryFilterIT extends AbstractDecisionStringVariableQueryFilterIT {

  @Test
  public void containsFilter_possibleMatchingScenarios() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    final String exactMatch = "ketchup";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(exactMatch)
    );
    final String matchesTheBeginning = "ketchupMustard";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(matchesTheBeginning)
    );
    final String matchesTheEnd = "mustardKetchup";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(matchesTheEnd)
    );
    final String matchesInBetween = "mustardKetchupMayonnaise";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(matchesInBetween)
    );
    final String caseInsensitiveMatch = "(asokndf249814kETchUpa;rinioanbrair01-34";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(caseInsensitiveMatch)
    );
    // no match 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs("noMatch")
    );
    // no match 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(null)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> containsFilter = createContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, containsFilter);

    // then
    assertThatResultContainsVariables(
      result,
      exactMatch,
      matchesTheBeginning,
      matchesTheEnd,
      matchesInBetween,
      caseInsensitiveMatch
    );
  }

  @Test
  public void containsFilter_matchesNoValue() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    final String noMatch1 = "mayonnaise";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(noMatch1)
    );
    final String noMatch2 = "mustard";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(noMatch2)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThat(result.getData()).isEmpty();
    assertThat(result.getInstanceCount()).isZero();
  }

  @Test
  public void containsFilter_otherVariableWithMatchingValue() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    final String exactMatch = "ketchup";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(exactMatch)
    );
    final Map<String, InputVariableEntry> inputs = createInputs("mustard");
    inputs.put(INPUT_SEASON_ID, new InputVariableEntry(INPUT_SEASON_ID, "Something", VariableType.STRING, "ketchup"));
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      inputs
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultContainsVariables(result, "ketchup");
  }

  @Test
  public void containsFilter_nullValue() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployInputEqualsOutputDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(),
      createInputs(null)
    );
    final DecisionDefinitionEngineDto decisionDefinitionDto2 = deployInputEqualsOutputDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto2.getId(),
      createInputs("ketchup")
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createContainsFilterForValues((String) null);
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto1);
    reportData.setFilter(filter);
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getData())
      .hasSize(1)
      .flatExtracting(RawDataDecisionInstanceDto::getDecisionDefinitionId)
      .containsExactly(decisionDefinitionDto1.getId());
  }

  @Test
  public void containsFilter_combineSeveralValues() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    final String ketchupMatch = "ketchup";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(ketchupMatch)
    );
    final String mustMatch = "mustard";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(mustMatch)
    );
    final String nullMatch = null;
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(nullMatch)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs("mayonnaise")
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createContainsFilterForValues("ketchup", null, "must");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultContainsVariables(result, "", ketchupMatch, mustMatch);
  }

  @Test
  public void containsFilter_worksWithVeryLongValues() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    final String longMatchWhichUsesWildcardQuery = "12345678910111213";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(longMatchWhichUsesWildcardQuery)
    );
    final String noMatch = "12345678910";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(noMatch)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createContainsFilterForValues("1234567891011");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultContainsVariables(result, longMatchWhichUsesWildcardQuery);
  }

  @Test
  public void containsFilter_missingValueIsNotAllowed() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    final String noMatch = "ketchup";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(noMatch)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createContainsFilterForValues();
    final DecisionReportDataDto reportWithAllVersionSet = createReportWithAllVersionSet(decisionDefinitionDto);
    reportWithAllVersionSet.setFilter(filter);
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportWithAllVersionSet)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void containsFilter_variableWithDifferentTypeIsIgnored() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    final String shouldMatch = "100";
    final HashMap<String, InputVariableEntry> inputWithStringMatch = new HashMap<String, InputVariableEntry>() {{
      put(STRING_INPUT_ID, new InputVariableEntry(STRING_INPUT_ID, "input", VariableType.STRING, shouldMatch));
      put(INTEGER_INPUT_ID, new InputVariableEntry(INTEGER_INPUT_ID, "input", VariableType.INTEGER, 0));
    }};
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      inputWithStringMatch
    );
    final HashMap<String, InputVariableEntry> inputsWithIntegerCouldMatchButIsDifferentType =
      new HashMap<String, InputVariableEntry>() {{
      put(STRING_INPUT_ID, new InputVariableEntry(STRING_INPUT_ID, "input", VariableType.STRING, "123"));
      put(INTEGER_INPUT_ID, new InputVariableEntry(INTEGER_INPUT_ID, "input", VariableType.INTEGER, 100));
    }};
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      inputsWithIntegerCouldMatchButIsDifferentType
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createContainsFilterForValues("100");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultContainsVariables(result, shouldMatch);
  }

  protected abstract void assertThatResultContainsVariables(final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result,
                                                            final String... shouldMatch);
  protected abstract List<DecisionFilterDto<?>> createContainsFilterForValues(final String... variableValues);

}
