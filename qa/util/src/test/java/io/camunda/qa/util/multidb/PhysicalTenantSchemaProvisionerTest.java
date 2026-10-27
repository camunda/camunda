/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhysicalTenantSchemaProvisionerTest {

  @Test
  void shouldBuildNamespaceFromBasePrefixAndTenantId() {
    // given
    final String basePrefix = "ABCDEFGHIJ";
    final String tenantId = "tenanta";

    // when
    final String namespace = PhysicalTenantSchemaProvisioner.buildNamespace(basePrefix, tenantId);

    // then
    assertThat(namespace).isEqualTo("ABCDEFGHIJ_tenanta");
  }

  // --- PostgreSQL URL rewriting ---

  @Test
  void shouldDerivePostgresUrlWhenNoQueryStringPresent() {
    // given
    final String baseUrl = "jdbc:postgresql://localhost:5432/camunda";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when
    final String result = PhysicalTenantSchemaProvisioner.derivePostgresUrl(baseUrl, namespace);

    // then
    assertThat(result)
        .isEqualTo("jdbc:postgresql://localhost:5432/camunda?currentSchema=ABCDEFGHIJ_tenanta");
  }

  @Test
  void shouldDerivePostgresUrlWhenQueryStringAlreadyPresent() {
    // given
    final String baseUrl = "jdbc:postgresql://localhost:5432/camunda?sslmode=disable";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when
    final String result = PhysicalTenantSchemaProvisioner.derivePostgresUrl(baseUrl, namespace);

    // then
    assertThat(result)
        .isEqualTo(
            "jdbc:postgresql://localhost:5432/camunda?sslmode=disable&currentSchema=ABCDEFGHIJ_tenanta");
  }

  @Test
  void shouldDerivePostgresUrlForShortFormWithoutHost() {
    // given
    final String baseUrl = "jdbc:postgresql:camunda";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when
    final String result = PhysicalTenantSchemaProvisioner.derivePostgresUrl(baseUrl, namespace);

    // then
    assertThat(result).isEqualTo("jdbc:postgresql:camunda?currentSchema=ABCDEFGHIJ_tenanta");
  }

  // --- MySQL / MariaDB URL rewriting ---

  @Test
  void shouldDeriveMysqlUrlReplacingDatabaseSegment() {
    // given
    final String baseUrl = "jdbc:mysql://localhost:3306/camunda";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when
    final String result = PhysicalTenantSchemaProvisioner.deriveMysqlUrl(baseUrl, namespace);

    // then
    assertThat(result).isEqualTo("jdbc:mysql://localhost:3306/ABCDEFGHIJ_tenanta");
  }

  @Test
  void shouldDeriveMysqlUrlPreservingQueryParams() {
    // given
    final String baseUrl = "jdbc:mysql://localhost:3306/camunda?charset=utf8&useSSL=false";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when
    final String result = PhysicalTenantSchemaProvisioner.deriveMysqlUrl(baseUrl, namespace);

    // then
    assertThat(result)
        .isEqualTo("jdbc:mysql://localhost:3306/ABCDEFGHIJ_tenanta?charset=utf8&useSSL=false");
  }

  @Test
  void shouldDeriveMariadbUrlReplacingDatabaseSegment() {
    // given
    final String baseUrl = "jdbc:mariadb://localhost:3306/camunda";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when
    final String result = PhysicalTenantSchemaProvisioner.deriveMysqlUrl(baseUrl, namespace);

    // then
    assertThat(result).isEqualTo("jdbc:mariadb://localhost:3306/ABCDEFGHIJ_tenanta");
  }

  @Test
  void shouldRejectMysqlUrlWithoutDatabaseSegment() {
    // given a URL with no database segment after the host
    final String baseUrl = "jdbc:mysql://localhost:3306";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when / then — must fail clearly rather than corrupt the host portion
    assertThatThrownBy(() -> PhysicalTenantSchemaProvisioner.deriveMysqlUrl(baseUrl, namespace))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expected a database segment");
  }

  // --- SQL Server URL rewriting ---

  @Test
  void shouldDeriveMssqlUrlAppendingDatabaseNameWhenAbsent() {
    // given
    final String baseUrl = "jdbc:sqlserver://localhost:1433;Encrypt=false";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when
    final String result = PhysicalTenantSchemaProvisioner.deriveMssqlUrl(baseUrl, namespace);

    // then
    assertThat(result)
        .isEqualTo("jdbc:sqlserver://localhost:1433;Encrypt=false;databaseName=ABCDEFGHIJ_tenanta");
  }

  @Test
  void shouldDeriveMssqlUrlReplacingExistingDatabaseName() {
    // given
    final String baseUrl = "jdbc:sqlserver://localhost:1433;databaseName=camunda;Encrypt=false";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when
    final String result = PhysicalTenantSchemaProvisioner.deriveMssqlUrl(baseUrl, namespace);

    // then
    assertThat(result)
        .isEqualTo("jdbc:sqlserver://localhost:1433;databaseName=ABCDEFGHIJ_tenanta;Encrypt=false");
  }

  @Test
  void shouldDeriveMssqlUrlReplacingDatabaseNameCaseInsensitively() {
    // given
    final String baseUrl = "jdbc:sqlserver://localhost:1433;DatabaseName=camunda";
    final String namespace = "ABCDEFGHIJ_tenanta";

    // when
    final String result = PhysicalTenantSchemaProvisioner.deriveMssqlUrl(baseUrl, namespace);

    // then
    assertThat(result).isEqualTo("jdbc:sqlserver://localhost:1433;databaseName=ABCDEFGHIJ_tenanta");
  }

  // --- Best-effort namespace cleanup ---

  @Test
  void shouldNotThrowWhenDroppingNamespaceFailsForSchemaDialect() {
    // given an unreachable host so the bootstrap connection cannot be established; connectTimeout
    // and socketTimeout keep the failure fast and deterministic even where the port silently drops
    // packets instead of refusing the connection
    final String unreachableUrl =
        "jdbc:postgresql://localhost:1/camunda?connectTimeout=1&socketTimeout=1";

    // when / then — cleanup is best-effort and must swallow the failure
    assertThatNoException()
        .isThrownBy(
            () ->
                PhysicalTenantSchemaProvisioner.dropNamespace(
                    CamundaMultiDBExtension.DatabaseType.RDBMS_POSTGRES,
                    unreachableUrl,
                    "user",
                    "pass",
                    "ABCDEFGHIJ_tenanta"));
  }

  @Test
  void shouldNotThrowWhenDroppingOracleUserFails() {
    // given a URL with no matching driver so the DROP USER bootstrap connection fails fast
    final String unreachableUrl = "jdbc:invalid://does-not-exist";

    // when / then — Oracle now isolates by user/schema, so cleanup issues DROP USER; the failure is
    // best-effort and must be swallowed
    assertThatNoException()
        .isThrownBy(
            () ->
                PhysicalTenantSchemaProvisioner.dropNamespace(
                    CamundaMultiDBExtension.DatabaseType.RDBMS_ORACLE,
                    unreachableUrl,
                    "user",
                    "pass",
                    "ABCDEFGHIJ_tenanta"));
  }

  @Test
  void shouldNotAttemptCleanupForH2() {
    // given a URL that would fail if a connection were attempted
    final String unreachableUrl = "jdbc:invalid://does-not-exist";

    // when / then — H2 uses a per-PT in-memory DB with no namespace object to drop, so
    // dropNamespace
    // is a no-op and never touches the connection
    assertThatNoException()
        .isThrownBy(
            () ->
                PhysicalTenantSchemaProvisioner.dropNamespace(
                    CamundaMultiDBExtension.DatabaseType.RDBMS_H2,
                    unreachableUrl,
                    "user",
                    "pass",
                    "ABCDEFGHIJ_tenanta"));
  }
}
