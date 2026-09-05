/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.read.domain.ProcessDefinitionStatisticsDbQuery;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProcessDefinitionMapperTest {

  private static final String MAPPER_DIRECTORY = "src/main/resources/mapper";
  private static final List<String> REQUIRED_MAPPER_FILES =
      List.of("Commons.xml", "ProcessDefinitionMapper.xml");
  private static final String FLOW_NODE_STATISTICS_STATEMENT =
      "io.camunda.db.rdbms.sql.ProcessDefinitionMapper.flowNodeStatistics";

  @Test
  void shouldRenderNotExistsForMissingErrorMessage() throws Exception {
    // given
    final var configuration = mapperConfiguration();
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .errorMessageOperations(Operation.exists(false))
            .build();
    final var query = ProcessDefinitionStatisticsDbQuery.of(builder -> builder.filter(filter));

    // when
    final var sql =
        configuration
            .getMappedStatement(FLOW_NODE_STATISTICS_STATEMENT)
            .getBoundSql(query)
            .getSql();

    // then
    assertThat(sql)
        .contains("NOT EXISTS")
        .contains("i.ERROR_MESSAGE IS NOT NULL")
        .doesNotContain("i.ERROR_MESSAGE IS NULL");
  }

  @Test
  void shouldRenderOracleEmptyEqualsAsIsNull() throws Exception {
    // given
    final var configuration = mapperConfiguration("oracle");
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .errorMessageOperations(Operation.eq(""))
            .build();
    final var query = ProcessDefinitionStatisticsDbQuery.of(builder -> builder.filter(filter));

    // when
    final var sql =
        configuration
            .getMappedStatement(FLOW_NODE_STATISTICS_STATEMENT)
            .getBoundSql(query)
            .getSql();

    // then
    assertThat(sql).contains("i.ERROR_MESSAGE IS NULL").doesNotContain("LIKE");
  }

  @Test
  void shouldRenderOracleEmptyNotEqualsAsIsNotNull() throws Exception {
    // given
    final var configuration = mapperConfiguration("oracle");
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .errorMessageOperations(Operation.neq(""))
            .build();
    final var query = ProcessDefinitionStatisticsDbQuery.of(builder -> builder.filter(filter));

    // when
    final var sql =
        configuration
            .getMappedStatement(FLOW_NODE_STATISTICS_STATEMENT)
            .getBoundSql(query)
            .getSql();

    // then
    assertThat(sql).contains("i.ERROR_MESSAGE IS NOT NULL").doesNotContain("LIKE");
  }

  @Test
  void shouldRenderOracleNonEmptyEqualsAsLike() throws Exception {
    // given
    final var configuration = mapperConfiguration("oracle");
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .errorMessageOperations(Operation.eq("expected error"))
            .build();
    final var query = ProcessDefinitionStatisticsDbQuery.of(builder -> builder.filter(filter));

    // when
    final var sql =
        configuration
            .getMappedStatement(FLOW_NODE_STATISTICS_STATEMENT)
            .getBoundSql(query)
            .getSql();

    // then
    assertThat(sql).contains("LOWER(i.ERROR_MESSAGE) LIKE LOWER(").contains("ESCAPE");
  }

  @Test
  void shouldRenderEqualsAsCaseInsensitiveLike() throws Exception {
    // given
    final var configuration = mapperConfiguration();
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .errorMessageOperations(Operation.eq("Expected Error"))
            .build();
    final var query = ProcessDefinitionStatisticsDbQuery.of(builder -> builder.filter(filter));

    // when
    final var sql =
        configuration
            .getMappedStatement(FLOW_NODE_STATISTICS_STATEMENT)
            .getBoundSql(query)
            .getSql();

    // then
    assertThat(sql).contains("LOWER(i.ERROR_MESSAGE) LIKE LOWER(").contains("ESCAPE");
  }

  private Configuration mapperConfiguration() throws Exception {
    return mapperConfiguration(null);
  }

  private Configuration mapperConfiguration(final String databaseId) throws Exception {
    final var configuration = new Configuration();
    configuration.setDatabaseId(databaseId);
    final var properties = new Properties();
    properties.setProperty("prefix", "");
    properties.setProperty("escapeChar", "'\\\\'");
    configuration.setVariables(properties);

    for (final var mapperFileName : REQUIRED_MAPPER_FILES) {
      final var mapperFile = new File(MAPPER_DIRECTORY, mapperFileName);
      try (var inputStream = new FileInputStream(mapperFile)) {
        new XMLMapperBuilder(
                inputStream, configuration, mapperFile.getPath(), configuration.getSqlFragments())
            .parse();
      }
    }
    return configuration;
  }
}
