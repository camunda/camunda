/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.qa.util.multidb.MultiPhysicalTenantClients;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Verifies that the per-physical-tenant RDBMS table row-count metric ({@code
 * zeebe.rdbms.table.row.count}, registered by {@code PhysicalTenantsRdbmsTableRowCountMetrics}) is
 * isolated per physical tenant: each tenant's gauge reads its own secondary storage. A single
 * process deployed only into {@code tenanta} moves that tenant's {@code PROCESS_DEFINITION} gauge
 * to 1 while {@code tenantb}'s gauge — backed by a separate database — stays 0.
 *
 * <p>Pinned to {@code RDBMS_H2}: only the H2 branch of the row-count query issues an exact live
 * {@code COUNT(*)}. PostgreSQL, Oracle, MySQL/MariaDB and SQL Server read lagging optimizer
 * statistics, so an exact {@code == 1}/{@code == 0} assertion would be non-deterministic there. The
 * physical-tenant isolation under test is vendor-independent, so H2 coverage is sufficient.
 */
@MultiDbTest
@MultiDbPhysicalTenants({"tenanta", "tenantb"})
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "(?i)rdbms_h2",
    disabledReason =
        "Exact row-count assertions are only deterministic on H2 (live COUNT(*)); other vendors"
            + " read lagging optimizer statistics")
final class TableRowCountMetricsMultiPhysicalTenantIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String METRIC_NAME = "zeebe_rdbms_table_row_count";
  private static final String PROCESS_DEFINITION_TABLE = "PROCESS_DEFINITION";
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          // Shrink the row-count cache so the gauge re-reads the DB within the test window; the
          // gauge otherwise caches the pre-deploy count for the (5m default) cache duration.
          .withProperty(
              "camunda.data.secondary-storage.rdbms.metrics.table-row-count-cache-duration",
              "PT1S");

  // Injected by the extension: admin clients for tenanta and tenantb
  private static MultiPhysicalTenantClients ptClients;

  @Test
  void shouldReportRowCountGaugePerPhysicalTenantIsolatedByStorage() {
    // given - deploy exactly one process, only into tenanta
    deploySingleProcess(ptClients.admin(TENANT_A));

    final PrometheusActuator prometheus = PrometheusActuator.of(BROKER);

    // then - both tenants expose the PROCESS_DEFINITION gauge, but only tenanta's reaches 1;
    // tenantb, backed by its own isolated database, stays at 0
    Awaitility.await("row-count gauge reflects the deploy only in tenanta")
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final String scrape = prometheus.metrics();
              assertThat(gaugeValue(scrape, TENANT_A, PROCESS_DEFINITION_TABLE))
                  .as("tenanta received the deploy → its PROCESS_DEFINITION gauge is 1")
                  .isEqualTo(1.0);
              assertThat(gaugeValue(scrape, TENANT_B, PROCESS_DEFINITION_TABLE))
                  .as("tenantb got no deploy → its gauge stays 0, proving per-PT storage isolation")
                  .isEqualTo(0.0);
            });
  }

  private static void deploySingleProcess(final CamundaClient client) {
    final String processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    client.newDeployResourceCommand().addProcessModel(model, processId + ".bpmn").send().join();
  }

  /**
   * Extracts the value of the {@code zeebe_rdbms_table_row_count} gauge for the given physical
   * tenant and table from a Prometheus text scrape. Throws an {@link AssertionError} if no matching
   * gauge line is present, which doubles as the "gauge exists" assertion.
   */
  private static double gaugeValue(
      final String scrape, final String physicalTenant, final String table) {
    return scrape
        .lines()
        .filter(line -> line.startsWith(METRIC_NAME + "{"))
        .filter(line -> line.contains("physicalTenant=\"" + physicalTenant + "\""))
        .filter(line -> line.contains("table=\"" + table + "\""))
        .map(TableRowCountMetricsMultiPhysicalTenantIT::parseGaugeValue)
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "No %s gauge for physicalTenant=%s table=%s in scrape:%n%s"
                        .formatted(METRIC_NAME, physicalTenant, table, scrape)));
  }

  private static double parseGaugeValue(final String line) {
    final String afterTags = line.substring(line.lastIndexOf('}') + 1).trim();
    return Double.parseDouble(afterTags.split("\\s+")[0]);
  }
}
