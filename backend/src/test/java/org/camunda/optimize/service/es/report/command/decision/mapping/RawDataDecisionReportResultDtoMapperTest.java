/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.mapping;

import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RawDataDecisionReportResultDtoMapperTest {

  @Test
  public void testMapFromSearchResponse_hitCountNotEqualTotalCount() {
    // given
    final Integer rawDataLimit = 2;
    final Long actualInstanceCount = 3L;
    final RawDecisionDataResultDtoMapper mapper = new RawDecisionDataResultDtoMapper();
    final List<DecisionInstanceDto> decisionInstanceDtos = generateInstanceList(rawDataLimit);

    // when
    final RawDataDecisionReportResultDto result = mapper.mapFrom(decisionInstanceDtos, actualInstanceCount);

    // then
    assertThat(result.getData().size(), is(rawDataLimit));
    assertThat(result.getIsComplete(), is(false));
    assertThat(result.getInstanceCount(), is(actualInstanceCount));
  }

  @Test
  public void testMapFromSearchResponse_hitCountEqualsTotalCount() {
    // given
    final Integer rawDataLimit = 3;
    final Long actualInstanceCount = 3L;
    final RawDecisionDataResultDtoMapper mapper = new RawDecisionDataResultDtoMapper();
    final List<DecisionInstanceDto> decisionInstanceDtos = generateInstanceList(rawDataLimit);

    // when
    final RawDataDecisionReportResultDto result = mapper.mapFrom(decisionInstanceDtos, actualInstanceCount);

    // then
    assertThat(result.getData().size(), is(rawDataLimit));
    assertThat(result.getIsComplete(), is(true));
    assertThat(result.getInstanceCount(), is(actualInstanceCount));
  }

  @Test
  public void testAllInputAndOutVariablesAreAvailableAtEachInstance() {
    // given
    final Integer rawDataLimit = 2;
    final Long actualInstanceCount = 3L;
    final RawDecisionDataResultDtoMapper mapper = new RawDecisionDataResultDtoMapper();

    final List<DecisionInstanceDto> decisionInstances = IntStream.rangeClosed(1, rawDataLimit)
      .mapToObj(i -> {
        final OutputInstanceDto outputInstanceDto = new OutputInstanceDto();
        outputInstanceDto.setType(VariableType.SHORT);
        outputInstanceDto.setId("id" + i);
        outputInstanceDto.setClauseId("output_id_" + i);
        outputInstanceDto.setClauseName("output_name_" + i);
        outputInstanceDto.setValue("" + i);

        final DecisionInstanceDto instanceDto = new DecisionInstanceDto()
          .setInputs(Collections.singletonList(new InputInstanceDto(
            "id" + i, "input_id_" + i, "input_name_" + i, VariableType.STRING, "a" + i
          )))
          .setOutputs(Collections.singletonList(outputInstanceDto));

        return instanceDto;
      })
      .collect(Collectors.toList());

    // when
    final RawDataDecisionReportResultDto result = mapper.mapFrom(decisionInstances, actualInstanceCount);

    // then
    assertThat(result.getData().size(), is(rawDataLimit));
    assertThat(result.getInstanceCount(), is(actualInstanceCount));
    IntStream.range(0, rawDataLimit)
      .forEach(i -> {
        assertThat(result.getData().get(i).getInputVariables().size(), is(rawDataLimit));
        assertThat(result.getData().get(i).getOutputVariables().size(), is(rawDataLimit));
      });
  }

  private List<DecisionInstanceDto> generateInstanceList(final Integer rawDataLimit) {
    return IntStream.range(0, rawDataLimit).mapToObj(i -> new DecisionInstanceDto()).collect(Collectors.toList());
  }
}
