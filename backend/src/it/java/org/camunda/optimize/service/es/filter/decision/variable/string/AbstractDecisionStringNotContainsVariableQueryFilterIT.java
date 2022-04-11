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

public abstract class AbstractDecisionStringNotContainsVariableQueryFilterIT extends AbstractDecisionStringVariableQueryFilterIT {

  @Test
  public void notContainsFilter_possibleMatchingScenarios() {
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
    final String noMatch = "noMatch";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(noMatch)
    );
    // no match 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(null)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> notContainsFilter = createNotContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, notContainsFilter);

    // then
    assertThatResultDoesNotContainVariables(result, noMatch, "");
  }

  @Test
  public void notContainsFilter_matchesNoValue() {
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
    final List<DecisionFilterDto<?>> filter = createNotContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultDoesNotContainVariables(result, noMatch1, noMatch2);
  }

  @Test
  public void notContainsFilter_otherVariableWithMatchingValue() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs("ketchup")
    );
    final String shouldNotMatch = "mustard";
    final Map<String, InputVariableEntry> inputs = createInputs(shouldNotMatch);
    inputs.put(INPUT_SEASON_ID, new InputVariableEntry(INPUT_SEASON_ID, "Something", VariableType.STRING, "ketchup"));
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      inputs
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createNotContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultDoesNotContainVariables(result, shouldNotMatch);
  }

  @Test
  public void notContainsFilter_nullValue() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(null)
    );
    final String shouldNotMatch = "ketchup";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(shouldNotMatch)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createNotContainsFilterForValues((String) null);
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultDoesNotContainVariables(result, shouldNotMatch);
  }

  @Test
  public void notContainsFilter_combineSeveralValues() {
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
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(null)
    );
    final String shouldNotMatch = "mayonnaise";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(shouldNotMatch)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createNotContainsFilterForValues("ketchup", null, "must");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultDoesNotContainVariables(result, shouldNotMatch);
  }

  @Test
  public void notContainsFilter_worksWithVeryLongValues() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    final String longMatchWhichUsesWildcardQuery = "12345678910111213";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(longMatchWhichUsesWildcardQuery)
    );
    final String shouldNotMatch = "12345678910";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(shouldNotMatch)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createNotContainsFilterForValues("1234567891011");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultDoesNotContainVariables(result, shouldNotMatch);
  }

  @Test
  public void notContainsFilter_missingValueIsNotAllowed() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployInputEqualsOutputDecisionDefinition();
    final String noMatch = "ketchup";
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(noMatch)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createNotContainsFilterForValues();
    final DecisionReportDataDto reportWithAllVersionSet = createReportWithAllVersionSet(decisionDefinitionDto);
    reportWithAllVersionSet.setFilter(filter);
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportWithAllVersionSet)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void notContainsFilter_variableWithDifferentTypeIsIgnored() {
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
    final String shouldNotMatch = "123";
    final HashMap<String, InputVariableEntry> inputsWithIntegerCouldMatchButIsDifferentType =
      new HashMap<String, InputVariableEntry>() {{
        put(STRING_INPUT_ID, new InputVariableEntry(STRING_INPUT_ID, "input", VariableType.STRING, shouldNotMatch));
        put(INTEGER_INPUT_ID, new InputVariableEntry(INTEGER_INPUT_ID, "input", VariableType.INTEGER, 100));
      }};
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      inputsWithIntegerCouldMatchButIsDifferentType
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<DecisionFilterDto<?>> filter = createNotContainsFilterForValues("100");
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluateReportWithFilter(decisionDefinitionDto, filter);

    // then
    assertThatResultDoesNotContainVariables(result, shouldNotMatch);
  }

  protected abstract void assertThatResultDoesNotContainVariables(final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result,
                                                                  final String... shouldMatch);

  protected abstract List<DecisionFilterDto<?>> createNotContainsFilterForValues(final String... variableValues);

}
