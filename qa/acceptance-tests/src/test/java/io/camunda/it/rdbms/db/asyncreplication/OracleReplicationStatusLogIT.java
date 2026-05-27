/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.asyncreplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Oracle Free (gvenzl/oracle-free:23) does not include Data Guard redo transport (V$OPTION: Oracle
 * Data Guard = FALSE), so V$ARCHIVE_DEST has no active STANDBY rows and getReplicationStatuses()
 * always returns an empty list. We only verify the SQL executes without error. For Postgres, we
 * verify at least one active replica row is returned.
 */
@Tag("rdbms")
public class OracleReplicationStatusLogIT {

  @RegisterExtension
  static final CamundaRdbmsInvocationContextProviderExtension TEST_APPLICATION =
      new CamundaRdbmsInvocationContextProviderExtension("camundaWithOracleDB");

  @TestTemplate
  public void shouldQueryReplicationStatus(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var replicationStatusProvider = rdbmsService.getReplicationLogStatusProvider();

    Awaitility.await()
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(() -> assertThat(replicationStatusProvider.getCurrent()).isGreaterThan(0));

    assertThatCode(replicationStatusProvider::getReplicationStatuses).doesNotThrowAnyException();
  }
}
