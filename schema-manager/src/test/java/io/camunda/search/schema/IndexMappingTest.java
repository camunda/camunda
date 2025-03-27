/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.test.utils.TestObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class IndexMappingTest {

  @Test
  void shouldReadIndexMappingsFileCorrectly() {
    // given
    final var index =
        SchemaTestUtil.mockIndex("index_name", "alias", "index_name", "/mappings.json");

    // when
    final var indexMapping = IndexMapping.from(index, TestObjectMapper.objectMapper());

    // then
    assertThat(indexMapping.dynamic()).isEqualTo("strict");

    assertThat(indexMapping.properties())
        .containsExactlyInAnyOrder(
            new IndexMappingProperty.Builder()
                .name("hello")
                .typeDefinition(Map.of("type", "text"))
                .build(),
            new IndexMappingProperty.Builder()
                .name("world")
                .typeDefinition(Map.of("type", "keyword"))
                .build());
  }
}
