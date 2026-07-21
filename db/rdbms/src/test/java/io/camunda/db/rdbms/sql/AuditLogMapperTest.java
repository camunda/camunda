/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.read.domain.AuditLogAuthorizationFilter;
import io.camunda.db.rdbms.read.domain.LatestAuditLogDbQuery;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AuditLogMapperTest {

  @Test
  void shouldBuildLatestSuccessfulByEntityKeysAsSingleRankedQuery() throws IOException {
    final var configuration = configuration();
    final var query =
        new LatestAuditLogDbQuery(
            AuditLogEntityType.USER_TASK,
            List.of("entity-1", "entity-2"),
            AuditLogAuthorizationFilter.allowAll(),
            true,
            List.of("tenant-1"));

    final var sql = latestAuditLogSql(configuration, query);

    assertThat(sql)
        .containsOnlyOnce("FROM AUDIT_LOG")
        .contains("ROW_NUMBER() OVER ( PARTITION BY ENTITY_KEY ORDER BY TIMESTAMP DESC )")
        .doesNotContain("AUDIT_LOG_KEY DESC")
        .contains("ENTITY_TYPE = ?")
        .contains("RESULT = 'SUCCESS'")
        .contains("ENTITY_KEY IN ( ? , ? )")
        .contains("TENANT_SCOPE = 'GLOBAL' OR TENANT_ID IN ( ? )")
        .contains("WHERE ROW_NUM = 1");
  }

  @Test
  void shouldNotFilterTenantsWhenTenantCheckIsDisabled() throws IOException {
    final var configuration = configuration();
    final var query =
        new LatestAuditLogDbQuery(
            AuditLogEntityType.USER_TASK,
            List.of("entity-1"),
            AuditLogAuthorizationFilter.allowAll(),
            false,
            List.of("ignored-tenant"));

    assertThat(latestAuditLogSql(configuration, query))
        .doesNotContain("AND (TENANT_SCOPE")
        .doesNotContain("OR TENANT_ID IN");
  }

  @Test
  void shouldFilterGlobalTenantScopeWhenTenantCheckIsEnabledWithoutTenants() throws IOException {
    final var configuration = configuration();
    final var query =
        new LatestAuditLogDbQuery(
            AuditLogEntityType.USER_TASK,
            List.of("entity-1"),
            AuditLogAuthorizationFilter.allowAll(),
            true,
            List.of());

    assertThat(latestAuditLogSql(configuration, query))
        .contains("AND (TENANT_SCOPE = 'GLOBAL' )")
        .doesNotContain("OR TENANT_ID IN");
  }

  private static Configuration configuration() throws IOException {
    final var configuration = new Configuration();
    final var variables = new Properties();
    variables.setProperty("prefix", "");
    configuration.setVariables(variables);
    parseMapper(configuration, "mapper/Commons.xml");
    parseMapper(configuration, "mapper/AuditLogMapper.xml");
    return configuration;
  }

  private static String latestAuditLogSql(
      final Configuration configuration, final LatestAuditLogDbQuery query) {
    return configuration
        .getMappedStatement(AuditLogMapper.class.getName() + ".searchLatestSuccessfulByEntityKeys")
        .getBoundSql(query)
        .getSql()
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static void parseMapper(final Configuration configuration, final String resource)
      throws IOException {
    try (final var inputStream = Resources.getResourceAsStream(resource)) {
      new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments())
          .parse();
    }
  }
}
