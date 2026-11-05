/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ElasticsearchRepositoryTest extends AbstractRepositoryTest {
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
          .enable(SerializationFeature.INDENT_OUTPUT);
  private final JacksonJsonpMapper jacksonJsonpMapper = new JacksonJsonpMapper();

  @ParameterizedTest
  @MethodSource("filtersAndQueries")
  void shouldConvertFilterToQuery(final FilterByKeysQuery<?> filter, final String expectedJsonQuery)
      throws JsonProcessingException {
    // given/when
    final var query = ElasticsearchRepository.toQuery(filter);

    // then
    final String json = toNormalisedJson(JsonpUtils.toJsonString(query, jacksonJsonpMapper));
    assertThat(json).isEqualTo(expectedJsonQuery);
  }

  private String toNormalisedJson(final String json) throws JsonProcessingException {
    return objectMapper.writeValueAsString(objectMapper.readValue(json, Map.class));
  }
}
