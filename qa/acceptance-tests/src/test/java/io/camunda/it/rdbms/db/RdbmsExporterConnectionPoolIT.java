/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.db.rdbms.read.RdbmsTenantReaders;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.RdbmsDataJdbcTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLTransientConnectionException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RdbmsDataJdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
    properties = {
      "spring.liquibase.enabled=false",
      "camunda.data.secondary-storage.type=rdbms",
      "camunda.data.secondary-storage.rdbms.connection-pool.maximum-pool-size=1",
      "camunda.data.secondary-storage.rdbms.connection-pool.minimum-idle=1",
      "camunda.data.secondary-storage.rdbms.connection-pool.connection-timeout=500ms"
    })
class RdbmsExporterConnectionPoolIT {

  @Autowired RdbmsDataSources rdbmsDataSources;
  @Autowired RdbmsServiceFactory rdbmsServiceFactory;
  @Autowired Map<String, RdbmsTenantReaders> rdbmsTenantReaders;

  @Test
  void shouldExportWhileSearchesHoldAllConnectionsOfTheMainPool() throws Exception {
    // given
    final var rdbmsService =
        rdbmsServiceFactory.createRdbmsService(
            DEFAULT_PHYSICAL_TENANT_ID, new SimpleMeterRegistry());
    final var rdbmsWriters =
        rdbmsService.createWriter(new RdbmsWriterConfig.Builder().partitionId(0).build());
    final var searchReader = rdbmsTenantReaders.get(DEFAULT_PHYSICAL_TENANT_ID);

    try (final var heldBySearch =
        rdbmsDataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID).getConnection()) {
      assertThatThrownBy(() -> searchReader.processInstanceReader().findOne(1L))
          .hasRootCauseInstanceOf(SQLTransientConnectionException.class);

      // when
      final var processInstance =
          ProcessInstanceFixtures.createAndSaveRandomProcessInstance(rdbmsWriters, b -> b);

      // then
      assertThat(
              rdbmsService.getProcessInstanceReader().findOne(processInstance.processInstanceKey()))
          .isPresent();
    }
  }
}
