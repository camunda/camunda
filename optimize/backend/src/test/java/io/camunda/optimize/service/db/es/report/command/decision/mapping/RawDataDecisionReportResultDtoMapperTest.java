/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.decision.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import io.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.VariableEntry;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.report.interpreter.util.RawDecisionDataResultDtoMapper;
import io.camunda.optimize.service.util.IdGenerator;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class RawDataDecisionReportResultDtoMapperTest {

  @Test
  public void testMapFromSearchResponseHitCountNotEqualTotalCount() {
    // given
    final int rawDataLimit = 2;
    final RawDecisionDataResultDtoMapper mapper = new RawDecisionDataResultDtoMapper();
    final List<DecisionInstanceDto> decisionInstanceDtos = generateInstanceList(rawDataLimit);

    // when
    final List<RawDataDecisionInstanceDto> result =
        mapper.mapFrom(decisionInstanceDtos, Collections.emptySet(), Collections.emptySet());

    // then
    assertThat(result).hasSize(rawDataLimit);
  }

  @Test
  public void testMapFromSearchResponseAdditionalVariablesAddedToResults() {
    // given
    final RawDecisionDataResultDtoMapper mapper = new RawDecisionDataResultDtoMapper();
    final List<DecisionInstanceDto> decisionInstanceDtos =
        generateInstanceList(5).stream()
            .peek(
                instance -> {
                  instance.setInputs(
                      Arrays.asList(
                          new InputInstanceDto(
                              IdGenerator.getNextId(),
                              "inputVarClauseId",
                              "inputVarClauseName",
                              VariableType.STRING,
                              "in1")));
                  instance.setOutputs(
                      Arrays.asList(
                          new OutputInstanceDto(
                              IdGenerator.getNextId(),
                              "outputVarClauseId",
                              "outputVarClauseName",
                              IdGenerator.getNextId(),
                              1,
                              "outVarName",
                              VariableType.STRING,
                              "out1")));
                })
            .collect(Collectors.toList());

    // when
    final List<RawDataDecisionInstanceDto> result =
        mapper.mapFrom(
            decisionInstanceDtos,
            new HashSet<>(
                Arrays.asList(
                    new InputVariableEntry(
                        IdGenerator.getNextId(),
                        "newInputVarName",
                        VariableType.STRING,
                        "newInVal"))),
            new HashSet<>(
                Arrays.asList(
                    new OutputVariableEntry(
                        IdGenerator.getNextId(),
                        "newOutputVarName",
                        VariableType.STRING,
                        "newOutVal"))));

    // then
    assertThat(result)
        .extracting(RawDataDecisionInstanceDto::getInputVariables)
        .allSatisfy(
            instanceInputVars ->
                assertThat(
                        instanceInputVars.values().stream()
                            .map(VariableEntry::getName)
                            .collect(Collectors.toList()))
                    .containsExactlyInAnyOrder("inputVarClauseName", "newInputVarName"));
    assertThat(result)
        .extracting(RawDataDecisionInstanceDto::getOutputVariables)
        .allSatisfy(
            instanceOutputVars ->
                assertThat(
                        instanceOutputVars.values().stream()
                            .map(VariableEntry::getName)
                            .collect(Collectors.toList()))
                    .containsExactlyInAnyOrder("outputVarClauseName", "newOutputVarName"));
  }

  private List<DecisionInstanceDto> generateInstanceList(final Integer rawDataLimit) {
    return IntStream.range(0, rawDataLimit)
        .mapToObj(i -> new DecisionInstanceDto())
        .collect(Collectors.toList());
  }
}
