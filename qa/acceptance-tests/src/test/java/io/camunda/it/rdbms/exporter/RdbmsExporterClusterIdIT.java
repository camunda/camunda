/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PartitionId;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots the real production {@link RdbmsExporterWrapper} against a live H2 database (no mocks) and
 * confirms {@code configure()} actually persists the cluster ID into {@code
 * RDBMS_SCHEMA_VERSION.CLUSTER_ID}. The H2 instance is in-memory, so the assertion runs while the
 * Spring context is still alive.
 */
@Tag("rdbms")
@SpringBootTest(classes = {RdbmsTestConfiguration.class})
@TestPropertySource(
    properties = {
      "spring.liquibase.enabled=false",
      "camunda.data.secondary-storage.type=rdbms",
      "camunda.data.secondary-storage.rdbms.queue-size=0",
      // distinguishes this context from RdbmsExporterIT's otherwise-identical configuration so
      // Spring's test-context cache does not share (and thus does not leak state via) the same
      // in-memory H2 database between the two test classes.
      "logging.level.io.camunda.db.rdbms=DEBUG",
    })
class RdbmsExporterClusterIdIT {

  private static final String CLUSTER_ID = "cluster-under-test-" + UUID.randomUUID();

  @Autowired private RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry;
  @Autowired private RdbmsServiceFactory rdbmsServiceFactory;
  @Autowired private DataSource dataSource;

  private RdbmsExporterWrapper exporter;

  @AfterEach
  void tearDown() {
    if (exporter != null) {
      exporter.close();
    }
  }

  @Test
  void shouldPersistClusterIdIntoRdbmsSchemaVersionTableOnRealStartup() throws Exception {
    // given - the real RdbmsExporterWrapper, wired with the real production
    // RdbmsSchemaManagerRegistry/RdbmsServiceFactory beans (no mocks)
    final var exporterConfiguration = new ExporterConfiguration("rdbms", Map.of("queueSize", 0));
    exporter =
        new RdbmsExporterWrapper(
            rdbmsServiceFactory,
            rdbmsSchemaManagerRegistry,
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                exporterConfiguration.instantiate(
                    io.camunda.exporter.rdbms.ExporterConfiguration.class)));

    // when - configure() is the exact production code path that calls
    // rdbmsSchemaManagerRegistry.validateClusterId(physicalTenantId, clusterId, restrictionEnabled)
    exporter.configure(
        new ExporterContext(
            null,
            exporterConfiguration,
            new PartitionId(DEFAULT_PHYSICAL_TENANT_ID, 1),
            CLUSTER_ID,
            null,
            Mockito.mock(MeterRegistry.class, Mockito.RETURNS_DEEP_STUBS),
            null));

    // then - query the live H2 database directly via JDBC: the cluster ID was really persisted,
    // proving the real, production wiring end to end (not a unit-tested mock)
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement();
        var resultSet = statement.executeQuery("SELECT CLUSTER_ID FROM RDBMS_SCHEMA_VERSION")) {
      assertThat(resultSet.next()).as("RDBMS_SCHEMA_VERSION must have exactly one row").isTrue();
      assertThat(resultSet.getString("CLUSTER_ID")).isEqualTo(CLUSTER_ID);
    }
  }
}
