/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.mapping;

import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.junit.jupiter.api.Test;

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
    final List<RawDataDecisionInstanceDto> result = mapper.mapFrom(decisionInstanceDtos);

    // then
    assertThat(result).hasSize(rawDataLimit);
  }

  private List<DecisionInstanceDto> generateInstanceList(final Integer rawDataLimit) {
    return IntStream.range(0, rawDataLimit).mapToObj(i -> new DecisionInstanceDto()).collect(Collectors.toList());
  }
}
