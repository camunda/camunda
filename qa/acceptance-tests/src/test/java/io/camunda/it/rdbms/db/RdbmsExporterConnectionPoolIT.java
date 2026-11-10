/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import java.sql.SQLTransientConnectionException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Tag("rdbms")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
    properties = {
      "spring.liquibase.enabled=false",
      "camunda.data.secondary-storage.type=rdbms",
      "camunda.data.secondary-storage.rdbms.connection-pool.maximum-pool-size=1",
      "camunda.data.secondary-storage.rdbms.connection-pool.minimum-idle=1",
      "camunda.data.secondary-storage.rdbms.connection-pool.connection-timeout=500"
    })
class RdbmsExporterConnectionPoolIT {

  @Autowired DataSource dataSource;
  @Autowired RdbmsService rdbmsService;
  @Autowired ProcessInstanceDbReader searchProcessInstanceReader;

  @Test
  void shouldExportWhileSearchesHoldAllConnectionsOfTheMainPool() throws Exception {
    // given
    final var rdbmsWriters =
        rdbmsService.createWriter(new RdbmsWriterConfig.Builder().partitionId(0).build());

    try (final var heldBySearch = dataSource.getConnection()) {
      assertThatThrownBy(() -> searchProcessInstanceReader.findOne(1L))
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
