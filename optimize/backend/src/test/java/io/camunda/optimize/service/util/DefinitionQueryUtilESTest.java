/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static io.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import jakarta.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefinitionQueryUtilESTest {

  private static final String KEY = "invoice";

  // Pins the query every process report scopes its definitions with, now built from field names.
  @ParameterizedTest(name = "versions {0}")
  @MethodSource("versionClauses")
  void shouldScopeTheVersionsOfTheProcessInstanceIndex(
      final List<String> versions, final String expectedVersionClause) {
    // given
    final ProcessInstanceIndexES index = new ProcessInstanceIndexES(KEY);

    // when
    final String query =
        toJson(
            DefinitionQueryUtilES.createDefinitionQuery(
                    KEY, versions, List.of("tenant-a"), index, key -> "7")
                .build());

    // then
    assertThat(query)
        .contains("\"" + index.getDefinitionKeyFieldName() + "\"")
        .contains("\"" + index.getTenantIdFieldName() + "\"");
    if (expectedVersionClause == null) {
      assertThat(query).doesNotContain(index.getDefinitionVersionFieldName());
    } else {
      assertThat(query).contains(expectedVersionClause);
    }
  }

  @Test
  void shouldMatchNothingWhenNoVersionIsSelected() {
    // when
    final String query =
        toJson(
            DefinitionQueryUtilES.createDefinitionQuery(
                    KEY,
                    List.of(),
                    List.of("tenant-a"),
                    "keyField",
                    "versionField",
                    "tenantField",
                    key -> "7")
                .build());

    // then an empty version list matches no version
    assertThat(query).contains("{\"terms\":{\"versionField\":[]}}");
  }

  @Test
  void shouldScopeToTheGivenFields() {
    // when
    final String query =
        toJson(
            DefinitionQueryUtilES.createDefinitionQuery(
                    KEY,
                    List.of(LATEST_VERSION),
                    Arrays.asList("tenant-a", null),
                    "keyField",
                    "versionField",
                    "tenantField",
                    key -> "7")
                .build());

    // then the latest version is resolved, and the null tenant matches documents without one
    assertThat(query)
        .contains("{\"term\":{\"keyField\":{\"value\":\"invoice\"}}}")
        .contains("{\"terms\":{\"versionField\":[\"7\"]}}")
        .contains("{\"terms\":{\"tenantField\":[\"tenant-a\"]}}")
        .contains("{\"exists\":{\"field\":\"tenantField\"}}");
  }

  private static Stream<Arguments> versionClauses() {
    return Stream.of(
        Arguments.of(List.of(LATEST_VERSION), "\"7\""),
        Arguments.of(List.of("1", "3"), "[\"1\",\"3\"]"),
        Arguments.of(List.of(ALL_VERSIONS), null));
  }

  private static String toJson(final BoolQuery query) {
    final JsonpMapper mapper = new JacksonJsonpMapper();
    final StringWriter writer = new StringWriter();
    try (final JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
      query.serialize(generator, mapper);
    }
    return writer.toString();
  }
}
