/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test that boots the whole Camunda application with two configured physical tenants,
 * each pointing at its own H2 database with its own table prefix, and verifies that Liquibase ran
 * once per physical tenant — i.e. the prefixed {@code DATABASECHANGELOG} table exists in each
 * tenant's dedicated database.
 */
@Tag("rdbms")
class LiquibaseMultiTenantIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  private static final String URL_A = "jdbc:h2:mem:lb-mt-a;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
  private static final String URL_B = "jdbc:h2:mem:lb-mt-b;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
  private static final String PREFIX_A = "TA_";
  private static final String PREFIX_B = "TB_";

  private CamundaRdbmsTestApplication app;

  @AfterEach
  void tearDown() {
    if (app != null) {
      app.close();
    }
  }

  @Test
  void shouldRunLiquibaseOncePerPhysicalTenant() throws Exception {
    // given - configure two physical tenants with distinct H2 URLs and table prefixes
    app =
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withH2()
            .withProperty(
                "camunda.physical-tenants." + TENANT_A + ".data.secondary-storage.rdbms.url", URL_A)
            .withProperty(
                "camunda.physical-tenants." + TENANT_A + ".data.secondary-storage.rdbms.username",
                "sa")
            .withProperty(
                "camunda.physical-tenants." + TENANT_A + ".data.secondary-storage.rdbms.password",
                "")
            .withProperty(
                "camunda.physical-tenants." + TENANT_A + ".data.secondary-storage.rdbms.prefix",
                PREFIX_A)
            .withProperty(
                "camunda.physical-tenants." + TENANT_B + ".data.secondary-storage.rdbms.url", URL_B)
            .withProperty(
                "camunda.physical-tenants." + TENANT_B + ".data.secondary-storage.rdbms.username",
                "sa")
            .withProperty(
                "camunda.physical-tenants." + TENANT_B + ".data.secondary-storage.rdbms.password",
                "")
            .withProperty(
                "camunda.physical-tenants." + TENANT_B + ".data.secondary-storage.rdbms.prefix",
                PREFIX_B);

    // when
    app.start();

    // then - the application started successfully and the prefixed Liquibase tracking tables exist
    // in each tenant's dedicated database.
    assertThat(app.isStarted()).isTrue();
    assertThat(tableExists(URL_A, PREFIX_A + "DATABASECHANGELOG")).isTrue();
    assertThat(tableExists(URL_B, PREFIX_B + "DATABASECHANGELOG")).isTrue();
    // and the other tenant's tracking table does NOT exist in the wrong database
    assertThat(tableExists(URL_A, PREFIX_B + "DATABASECHANGELOG")).isFalse();
    assertThat(tableExists(URL_B, PREFIX_A + "DATABASECHANGELOG")).isFalse();
  }

  private static boolean tableExists(final String jdbcUrl, final String tableName)
      throws Exception {
    final var ds = new JdbcDataSource();
    ds.setURL(jdbcUrl);
    ds.setUser("sa");
    ds.setPassword("");
    try (final var conn = ds.getConnection()) {
      final var meta = conn.getMetaData();
      try (final var rs =
          meta.getTables(null, null, tableName.toUpperCase(), new String[] {"TABLE"})) {
        return rs.next();
      }
    }
  }
}
