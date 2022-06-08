/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.process.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class RawProcessDataResultDtoMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testMapFromSearchResponse_hitCountNotEqualTotalCount() {
    // given
    final Integer rawDataLimit = 2;
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(rawDataLimit);

    // when
    final List<RawDataProcessInstanceDto> result =
      mapper.mapFrom(processInstanceDtos, objectMapper, Collections.emptySet());

    // then
    assertThat(result).hasSize(rawDataLimit);
  }

  @Test
  public void testMapFromSearchResponse_additionalVariablesAddedToResults() {
    // given
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(5)
      .stream()
      .peek(instance -> {
        final SimpleProcessVariableDto variableForInstances = new SimpleProcessVariableDto(
          IdGenerator.getNextId(), "var1", "String", Collections.singletonList("val1"), 1L);
        instance.setVariables(Collections.singletonList(variableForInstances));
      })
      .collect(Collectors.toList());

    // when
    final Set<String> varsFromUndisplayedInstances = new HashSet<>();
    varsFromUndisplayedInstances.add("var2");
    final List<RawDataProcessInstanceDto> result =
      mapper.mapFrom(processInstanceDtos, objectMapper, varsFromUndisplayedInstances);

    // then
    assertThat(result)
      .extracting(RawDataProcessInstanceDto::getVariables).isNotEmpty()
      .allSatisfy(variables -> assertThat(variables.keySet()).containsAll(Arrays.asList("var1", "var2")));
  }

  @Test
  public void nullVariableValuesReturnedAsEmptyString() {
    // given
    final RawProcessDataResultDtoMapper mapper = new RawProcessDataResultDtoMapper();
    final List<ProcessInstanceDto> processInstanceDtos = generateInstanceList(5)
      .stream()
      .peek(instance -> {
        final SimpleProcessVariableDto variableForInstances = new SimpleProcessVariableDto(
          IdGenerator.getNextId(), "var1", "String", null, 1L);
        instance.setVariables(Collections.singletonList(variableForInstances));
      })
      .collect(Collectors.toList());

    // when
    final List<RawDataProcessInstanceDto> result =
      mapper.mapFrom(processInstanceDtos, objectMapper, new HashSet<>());

    // then
    assertThat(result)
      .extracting(RawDataProcessInstanceDto::getVariables).isNotEmpty()
      .allSatisfy(variables -> assertThat(variables).containsEntry("var1", ""));
  }

  private List<ProcessInstanceDto> generateInstanceList(final Integer rawDataLimit) {
    return IntStream.range(0, rawDataLimit)
      .mapToObj(i -> ProcessInstanceDto.builder().build())
      .collect(Collectors.toList());
  }

}
