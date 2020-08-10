/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class RawDataProcessReportResultDtoMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testMapFromSearchResponse_hitCountNotEqualTotalCount() {
    // given
    final Integer rawDataLimit = 2;
    final Long actualInstanceCount = 3L;
    final Long unfilteredInstanceCount = 4L;
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(rawDataLimit);

    // when
    final RawDataProcessReportResultDto result = mapper.mapFrom(
      processInstanceDtos,
      actualInstanceCount,
      unfilteredInstanceCount,
      objectMapper
    );

    // then
    assertThat(result.getData()).hasSize(rawDataLimit);
    assertThat(result.getIsComplete()).isFalse();
    assertThat(result.getInstanceCount()).isEqualTo(actualInstanceCount);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(unfilteredInstanceCount);
  }

  @Test
  public void testMapFromSearchResponse_hitCountEqualsTotalCount() {
    // given
    final Integer rawDataLimit = 3;
    final Long actualInstanceCount = 3L;
    final Long unfilteredInstanceCount = 4L;
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(rawDataLimit);

    // when
    final RawDataProcessReportResultDto result = mapper.mapFrom(
      processInstanceDtos,
      actualInstanceCount,
      unfilteredInstanceCount,
      objectMapper
    );

    // then
    assertThat(result.getData()).hasSize(rawDataLimit);
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getInstanceCount()).isEqualTo(actualInstanceCount);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(unfilteredInstanceCount);
  }

  private List<ProcessInstanceDto> generateInstanceList(final Integer rawDataLimit) {
    return IntStream.range(0, rawDataLimit).mapToObj(i -> new ProcessInstanceDto()).collect(Collectors.toList());
  }

}
