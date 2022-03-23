/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.decision.mapping;

import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.VariableEntry;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class RawDataDecisionReportResultDtoMapperTest {

  @Test
  public void testMapFromSearchResponse_hitCountNotEqualTotalCount() {
    // given
    final Integer rawDataLimit = 2;
    final RawDecisionDataResultDtoMapper mapper = new RawDecisionDataResultDtoMapper();
    final List<DecisionInstanceDto> decisionInstanceDtos = generateInstanceList(rawDataLimit);

    // when
    final List<RawDataDecisionInstanceDto> result = mapper.mapFrom(
      decisionInstanceDtos,
      Collections.emptySet(),
      Collections.emptySet()
    );

    // then
    assertThat(result).hasSize(rawDataLimit);
  }

  @Test
  public void testMapFromSearchResponse_additionalVariablesAddedToResults() {
    // given
    final RawDecisionDataResultDtoMapper mapper = new RawDecisionDataResultDtoMapper();
    final List<DecisionInstanceDto> decisionInstanceDtos = generateInstanceList(5)
      .stream()
      .peek(instance -> {
        instance.setInputs(Arrays.asList(new InputInstanceDto(
          IdGenerator.getNextId(), "inputVarClauseId", "inputVarClauseName", VariableType.STRING, "in1")));
        instance.setOutputs(Arrays.asList(new OutputInstanceDto(
          IdGenerator.getNextId(), "outputVarClauseId", "outputVarClauseName",
          IdGenerator.getNextId(), 1, "outVarName", VariableType.STRING, "out1"
        )));
      })
      .collect(Collectors.toList());

    // when
    final List<RawDataDecisionInstanceDto> result = mapper.mapFrom(
      decisionInstanceDtos,
      new HashSet<>(Arrays.asList(new InputVariableEntry(
        IdGenerator.getNextId(),
        "newInputVarName",
        VariableType.STRING,
        "newInVal"
      ))),
      new HashSet<>(Arrays.asList(new OutputVariableEntry(
        IdGenerator.getNextId(),
        "newOutputVarName",
        VariableType.STRING,
        "newOutVal"
      )))
    );

    // then
    assertThat(result)
      .extracting(RawDataDecisionInstanceDto::getInputVariables)
      .allSatisfy(instanceInputVars ->
                    assertThat(instanceInputVars.values()
                                 .stream()
                                 .map(VariableEntry::getName)
                                 .collect(Collectors.toList()))
                      .containsExactlyInAnyOrder("inputVarClauseName", "newInputVarName"));
    assertThat(result)
      .extracting(RawDataDecisionInstanceDto::getOutputVariables)
      .allSatisfy(instanceOutputVars ->
                    assertThat(instanceOutputVars.values()
                                 .stream()
                                 .map(VariableEntry::getName)
                                 .collect(Collectors.toList()))
                      .containsExactlyInAnyOrder("outputVarClauseName", "newOutputVarName"));
  }

  private List<DecisionInstanceDto> generateInstanceList(final Integer rawDataLimit) {
    return IntStream.range(0, rawDataLimit).mapToObj(i -> new DecisionInstanceDto()).collect(Collectors.toList());
  }

}
