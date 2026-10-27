/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.read.domain.DbQueryPage;
import io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPagination;
import io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPaginationFieldEntry;
import io.camunda.db.rdbms.read.domain.DbQueryPage.Operator;
import io.camunda.db.rdbms.read.domain.DbQuerySorting;
import io.camunda.db.rdbms.read.domain.IncidentDbQuery;
import io.camunda.db.rdbms.sql.columns.IncidentSearchColumn;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.sort.SortOrder;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A {@code before} page seeks backwards from its cursor, so the statement must be ordered against
 * the display direction — only then does the page-size LIMIT keep the rows adjacent to the cursor
 * instead of the first rows of the whole filtered range.
 */
class KeySetPaginationOrderByTest {

  private static final String MAPPER_DIRECTORY = "src/main/resources/mapper";
  private static final List<String> REQUIRED_MAPPER_FILES =
      List.of("Commons.xml", "IncidentMapper.xml");
  private static final String SEARCH_STATEMENT = "io.camunda.db.rdbms.sql.IncidentMapper.search";

  private static final DbQuerySorting<IncidentEntity> SORT =
      DbQuerySorting.of(
          b ->
              b.addEntry(IncidentSearchColumn.CREATION_DATE, SortOrder.DESC)
                  .addEntry(IncidentSearchColumn.INCIDENT_KEY, SortOrder.ASC));

  @Test
  void shouldKeepSortDirectionWhenPagingForward() throws Exception {
    // given
    final var configuration = mapperConfiguration(null);

    // when
    final var sql = renderSearch(configuration, page(false));

    // then
    assertThat(orderByClause(sql)).isEqualTo("ORDER BY CREATION_DATE DESC, INCIDENT_KEY ASC");
  }

  @Test
  void shouldReverseSortDirectionWhenPagingBackward() throws Exception {
    // given
    final var configuration = mapperConfiguration(null);

    // when
    final var sql = renderSearch(configuration, page(true));

    // then
    assertThat(orderByClause(sql)).isEqualTo("ORDER BY CREATION_DATE ASC, INCIDENT_KEY DESC");
  }

  @ParameterizedTest
  @ValueSource(strings = {"oracle", "postgresql"})
  void shouldReverseNullOrderingWhenPagingBackward(final String databaseId) throws Exception {
    // given
    final var configuration = mapperConfiguration(databaseId);

    // when
    final var forward = renderSearch(configuration, page(false));
    final var backward = renderSearch(configuration, page(true));

    // then
    assertThat(orderByClause(forward))
        .isEqualTo("ORDER BY CREATION_DATE DESC NULLS LAST, INCIDENT_KEY ASC NULLS FIRST");
    assertThat(orderByClause(backward))
        .isEqualTo("ORDER BY CREATION_DATE ASC NULLS FIRST, INCIDENT_KEY DESC NULLS LAST");
  }

  private static DbQueryPage page(final boolean searchBefore) {
    final var keySetPagination =
        List.of(
            new KeySetPagination(
                List.of(new KeySetPaginationFieldEntry("INCIDENT_KEY", Operator.GREATER, 42L))));
    return new DbQueryPage(5, 0, 1000, keySetPagination, searchBefore);
  }

  private static String renderSearch(final Configuration configuration, final DbQueryPage page) {
    final var query = IncidentDbQuery.of(b -> b.sort(SORT).page(page));
    return configuration.getMappedStatement(SEARCH_STATEMENT).getBoundSql(query).getSql();
  }

  /** Extracts the single ORDER BY clause of the statement, with whitespace normalized. */
  private static String orderByClause(final String sql) {
    final var normalized = sql.replaceAll("\\s+", " ").replace(" ,", ",");
    final var start = normalized.indexOf("ORDER BY");
    assertThat(start).as("statement contains an ORDER BY clause").isNotNegative();
    assertThat(normalized.indexOf("ORDER BY", start + 1))
        .as("statement contains exactly one ORDER BY clause")
        .isNegative();
    return normalized.substring(start).trim();
  }

  private Configuration mapperConfiguration(final String databaseId) throws Exception {
    final var configuration = new Configuration();
    configuration.setDatabaseId(databaseId);
    final var properties = new Properties();
    properties.setProperty("prefix", "");
    properties.setProperty("escapeChar", "'\\\\'");
    properties.setProperty("keysetPaging.limit", "");
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
