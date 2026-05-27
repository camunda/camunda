/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.group;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.junit.jupiter.api.Test;

class ProcessGroupByDtoDeserializationTest {

  private final ObjectMapper objectMapper = ObjectMapperFactory.OPTIMIZE_MAPPER;

  @Test
  void shouldRoundTripDeserializeProcessDefinitionKeyGroupBy() throws Exception {
    // given
    final var original = new ProcessDefinitionKeyGroupByDto();

    // when
    final String json = objectMapper.writeValueAsString(original);
    final ProcessGroupByDto<?> result = objectMapper.readValue(json, ProcessGroupByDto.class);

    // then
    assertThat(json).contains("\"type\":\"processDefinitionKey\"");
    assertThat(result).isInstanceOf(ProcessDefinitionKeyGroupByDto.class);
  }

  @Test
  void shouldRoundTripDeserializeProcessDefinitionVersionGroupBy() throws Exception {
    // given
    final var original = new ProcessDefinitionVersionGroupByDto();

    // when
    final String json = objectMapper.writeValueAsString(original);
    final ProcessGroupByDto<?> result = objectMapper.readValue(json, ProcessGroupByDto.class);

    // then
    assertThat(json).contains("\"type\":\"processDefinitionVersion\"");
    assertThat(result).isInstanceOf(ProcessDefinitionVersionGroupByDto.class);
  }
}
