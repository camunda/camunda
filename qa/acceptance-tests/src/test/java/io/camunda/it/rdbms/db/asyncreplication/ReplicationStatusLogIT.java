/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.asyncreplication;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestTemplate;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("rdbms")
public class ReplicationStatusLogIT {

  @RegisterExtension
  static final CamundaRdbmsInvocationContextProviderExtension TEST_APPLICATION =
      new CamundaRdbmsInvocationContextProviderExtension(
          "camundaWithPostgresReplicationCluster", "camundaWithMssqlReplicationCluster");

  @RdbmsTestTemplate
  public void shouldQueryReplicationStatus(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final var replicationStatusProvider = rdbmsService.getReplicationLogStatusProvider();

    Awaitility.await()
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(
            () -> {
              final var currentLsn = replicationStatusProvider.getCurrent();
              final var replicationStatuses = replicationStatusProvider.getReplicationStatuses();

              assertThat(currentLsn).isGreaterThan(0);
              assertThat(replicationStatuses).isNotEmpty();
            });
  }
}
