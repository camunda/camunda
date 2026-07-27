/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class TableMetricsIT {

  @TestTemplate
  public void shouldCountTableRows(final CamundaRdbmsTestApplication testApplication) {
    final Map<String, RdbmsMapperBundle> rdbmsMapperBundles =
        testApplication.bean("rdbmsMapperBundles");
    final var rdbmsMapperBundle = rdbmsMapperBundles.get(DEFAULT_PHYSICAL_TENANT_ID);
    final var tableMetricsMapper = rdbmsMapperBundle.tableMetricsMapper();

    final String tableName = "AUTHORIZATIONS";

    // Oracle and PostgreSQL derive the row count from optimizer statistics (num_rows / reltuples),
    // which stay NULL/-1 until statistics are gathered. Statistics collection has not run yet on a
    // freshly created test table, so the count query would return the -1 sentinel. Trigger a stats
    // refresh first to get a deterministic, non-negative count for those vendors.
    refreshTableStatistics(rdbmsMapperBundle, tableName);

    // Get the count using the optimized vendor-specific query
    final long rowCount = tableMetricsMapper.countTableRows(tableName);

    // having any number here is sufficient for the test because it shows that the SQL is working
    // asserting for a specific number would make the test fragile as the statistics are
    // eventual consistent and only a rough number for each vendor
    assertThat(rowCount).isGreaterThanOrEqualTo(0);
  }

  /**
   * Refreshes optimizer statistics for the given table on vendors whose row-count query reads those
   * statistics. Other vendors expose exact or live counts and need no refresh.
   */
  private static void refreshTableStatistics(
      final RdbmsMapperBundle rdbmsMapperBundle, final String tableName) {
    final String databaseId = rdbmsMapperBundle.vendorDatabaseProperties().databaseId();
    final String statsSql =
        switch (databaseId) {
          case "postgresql" -> "ANALYZE " + tableName;
          case "oracle" -> "ANALYZE TABLE " + tableName + " COMPUTE STATISTICS";
          default -> null;
        };
    if (statsSql == null) {
      return;
    }

    try (final SqlSession session = rdbmsMapperBundle.sqlSessionFactory().openSession();
        final Statement statement = session.getConnection().createStatement()) {
      statement.execute(statsSql);
      session.commit();
    } catch (final SQLException e) {
      throw new IllegalStateException(
          "Failed to refresh table statistics for '%s' on '%s'".formatted(tableName, databaseId),
          e);
    }
  }
}
