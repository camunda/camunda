/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class RawDataProcessReportResultDtoMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testMapFromSearchResponse_hitCountNotEqualTotalCount() {
    // given
    final Integer rawDataLimit = 2;
    final Long actualInstanceCount = 3L;
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(rawDataLimit);

    // when
    final RawDataProcessReportResultDto result = mapper.mapFrom(processInstanceDtos, actualInstanceCount, objectMapper);

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
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(rawDataLimit);

    // when
    final RawDataProcessReportResultDto result = mapper.mapFrom(processInstanceDtos, actualInstanceCount, objectMapper);

    // then
    assertThat(result.getData().size(), is(rawDataLimit));
    assertThat(result.getIsComplete(), is(true));
    assertThat(result.getInstanceCount(), is(actualInstanceCount));
  }

  private List<ProcessInstanceDto> generateInstanceList(final Integer rawDataLimit) {
    return IntStream.range(0, rawDataLimit).mapToObj(i -> new ProcessInstanceDto()).collect(Collectors.toList());
  }

}
