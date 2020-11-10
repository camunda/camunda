/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RawDataProcessReportResultDtoMapperTest {

  @Mock
  private ExecutionContext<ProcessReportDataDto> executionContext;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testMapFromSearchResponse_hitCountNotEqualTotalCount() {
    // given
    final Integer rawDataLimit = 2;
    final Long actualInstanceCount = 3L;
    final Long unfilteredInstanceCount = 4L;
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(rawDataLimit);
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    final PaginationDto expectedPagination = PaginationDto.fromPaginationRequest(paginationDto);
    when(executionContext.getUnfilteredInstanceCount())
      .thenReturn(unfilteredInstanceCount);
    when(executionContext.getPagination()).thenReturn(expectedPagination);

    // when
    final RawDataProcessReportResultDto result = mapper.mapFrom(
      processInstanceDtos,
      actualInstanceCount,
      executionContext,
      objectMapper
    );

    // then
    assertThat(result.getData()).hasSize(rawDataLimit);
    assertThat(result.getInstanceCount()).isEqualTo(actualInstanceCount);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(unfilteredInstanceCount);
    assertThat(result.getPagination()).isEqualTo(expectedPagination);
  }

  @Test
  public void testMapFromSearchResponse_hitCountEqualsTotalCount() {
    // given
    final Integer rawDataLimit = 3;
    final Long actualInstanceCount = 3L;
    final Long unfilteredInstanceCount = 4L;
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(rawDataLimit);
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    final PaginationDto expectedPagination = PaginationDto.fromPaginationRequest(paginationDto);
    when(executionContext.getUnfilteredInstanceCount())
      .thenReturn(unfilteredInstanceCount);
    when(executionContext.getPagination()).thenReturn(expectedPagination);

    // when
    final RawDataProcessReportResultDto result = mapper.mapFrom(
      processInstanceDtos,
      actualInstanceCount,
      executionContext,
      objectMapper
    );

    // then
    assertThat(result.getData()).hasSize(rawDataLimit);
    assertThat(result.getInstanceCount()).isEqualTo(actualInstanceCount);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(unfilteredInstanceCount);
    assertThat(result.getPagination()).isEqualTo(expectedPagination);
  }

  @Test
  public void testMapFromSearchResponse_paginationParamIsReturned() {
    // given
    final Integer rawDataLimit = 2;
    final Long actualInstanceCount = 3L;
    final Long unfilteredInstanceCount = 4L;
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(rawDataLimit);
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(10);
    paginationDto.setLimit(10);
    final PaginationDto expectedPagination = PaginationDto.fromPaginationRequest(paginationDto);
    when(executionContext.getUnfilteredInstanceCount())
      .thenReturn(unfilteredInstanceCount);
    when(executionContext.getPagination()).thenReturn(expectedPagination);

    // when
    final RawDataProcessReportResultDto result = mapper.mapFrom(
      processInstanceDtos,
      actualInstanceCount,
      executionContext,
      objectMapper
    );

    // then
    assertThat(result.getData()).hasSize(rawDataLimit);
    assertThat(result.getInstanceCount()).isEqualTo(actualInstanceCount);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(unfilteredInstanceCount);
    assertThat(result.getPagination()).isEqualTo(expectedPagination);
  }

  private List<ProcessInstanceDto> generateInstanceList(final Integer rawDataLimit) {
    return IntStream.range(0, rawDataLimit)
      .mapToObj(i -> ProcessInstanceDto.builder().build())
      .collect(Collectors.toList());
  }

}
