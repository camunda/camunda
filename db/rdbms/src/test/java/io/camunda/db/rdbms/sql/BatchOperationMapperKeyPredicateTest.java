/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationActivateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemCompletionDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemStatusUpdateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemsCompletionDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateTotalCountDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.UpdateHistoryCleanupDateDto;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The MSSQL driver sends strings as NVARCHAR. Compared with the VARCHAR batch operation key this
 * makes key predicates scans (locking every row passed) on SQL collations, which defeats the
 * deterministic lock order of the execution queue. Every key predicate must therefore cast the
 * parameter on MSSQL, and only there.
 */
class BatchOperationMapperKeyPredicateTest {

  private static final String CAST = "CAST(? AS VARCHAR(255))";
  private static final String PREFIX = "io.camunda.db.rdbms.sql.BatchOperationMapper.";
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  static Stream<Arguments> statements() {
    return Stream.of(
        Arguments.of("lockForUpdate", "key", 1),
        Arguments.of("activate", new BatchOperationActivateDto("key", NOW), 1),
        Arguments.of(
            "updateCompleted",
            new BatchOperationUpdateDto("key", BatchOperationState.SUSPENDED, null),
            1),
        Arguments.of(
            "incrementOperationsTotalCount", new BatchOperationUpdateTotalCountDto("key", 3), 1),
        Arguments.of("incrementFailedOperationsCount", "key", 1),
        Arguments.of("incrementCompletedOperationsCount", "key", 1),
        Arguments.of(
            "bulkIncrementCompletedOperationsCount",
            new BatchOperationUpdateTotalCountDto("key", 3),
            1),
        Arguments.of(
            "updateItemsWithState",
            new BatchOperationItemStatusUpdateDto(
                "key", BatchOperationItemState.ACTIVE, BatchOperationItemState.CANCELED),
            1),
        Arguments.of("updateHistoryCleanupDate", new UpdateHistoryCleanupDateDto("key", NOW), 1),
        // one predicate per item of the OR chain
        Arguments.of(
            "completeBatchOperationItems",
            new BatchOperationItemsCompletionDto(
                List.of(
                    new BatchOperationItemCompletionDto("k1", 1L),
                    new BatchOperationItemCompletionDto("k2", 2L),
                    new BatchOperationItemCompletionDto("k3", 3L)),
                NOW),
            3),
        // MERGE source value compared with the VARCHAR key of the target
        Arguments.of(
            "upsertItem",
            new BatchOperationItemDto("key", 1L, BatchOperationItemState.COMPLETED, NOW, null),
            1));
  }

  @ParameterizedTest(name = "{0} casts the key on mssql")
  @MethodSource("statements")
  void shouldCastTheKeyParameterOnMssql(
      final String statement, final Object parameter, final int expectedCasts) {
    // given
    final var configuration = parseMapper("mssql");

    // when
    final var sql = sqlOf(configuration, statement, parameter);

    // then
    assertThat(countOccurrences(sql, CAST)).as(sql).isEqualTo(expectedCasts);
  }

  @ParameterizedTest(name = "{0} leaves the key untouched on postgresql")
  @MethodSource("statements")
  void shouldNotCastTheKeyParameterOnOtherDatabases(
      final String statement, final Object parameter, final int ignored) {
    // given
    final var configuration = parseMapper("postgresql");

    // when
    final var sql = sqlOf(configuration, statement, parameter);

    // then
    assertThat(sql).as(sql).doesNotContain("CAST(");
  }

  @Test
  void shouldUseVendorSpecificLockStatement() {
    assertThat(sqlOf(parseMapper("mssql"), "lockForUpdate", "key"))
        .contains("WITH (UPDLOCK, ROWLOCK)")
        .doesNotContain("FOR UPDATE");
    assertThat(sqlOf(parseMapper("postgresql"), "lockForUpdate", "key"))
        .contains("FOR UPDATE")
        .doesNotContain("UPDLOCK");
    assertThat(sqlOf(parseMapper("oracle"), "lockForUpdate", "key")).contains("FOR UPDATE");
  }

  /**
   * Every partition exports the creation, so the row usually exists already. The index hint alone
   * fails with an internal error (ORA-00600) when it meets an existing row that another partition
   * is modifying, so existing rows must be filtered out before the hint is reached. The hint still
   * covers two partitions inserting a new row at the same time.
   */
  @Test
  void shouldOnlyInsertMissingBatchOperationsOnOracle() {
    assertThat(sqlOf(parseMapper("oracle"), "createIfNotExists", "key"))
        .contains("IGNORE_ROW_ON_DUPKEY_INDEX")
        .contains("WHERE NOT EXISTS");
  }

  private static String sqlOf(
      final Configuration configuration, final String statement, final Object parameter) {
    return configuration
        .getMappedStatement(PREFIX + statement)
        .getBoundSql(parameter)
        .getSql()
        .replaceAll("\\s+", " ");
  }

  private static Configuration parseMapper(final String databaseId) {
    final var configuration = new Configuration();
    configuration.setDatabaseId(databaseId);
    configuration.getVariables().setProperty("prefix", "");
    for (final var resource : List.of("mapper/Commons.xml", "mapper/BatchOperationMapper.xml")) {
      try (final InputStream in =
          BatchOperationMapperKeyPredicateTest.class
              .getClassLoader()
              .getResourceAsStream(resource)) {
        new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
      } catch (final Exception e) {
        throw new IllegalStateException("Failed to parse " + resource, e);
      }
    }
    return configuration;
  }

  private static int countOccurrences(final String text, final String needle) {
    int count = 0;
    for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
      count++;
    }
    return count;
  }
}
