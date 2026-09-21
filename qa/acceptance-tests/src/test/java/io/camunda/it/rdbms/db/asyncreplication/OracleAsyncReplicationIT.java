/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.asyncreplication;

import io.camunda.it.rdbms.db.util.OracleDataGuardReplicationCluster;
import java.time.Duration;
import org.junit.jupiter.api.Tag;

/**
 * Runs the {@link AsyncReplicationIT} suite against an RDS for Oracle primary and Data Guard
 * replica provisioned by the Oracle async-replication CI workflow.
 */
@Tag("rdbms-oracle")
public class OracleAsyncReplicationIT
    extends AsyncReplicationIT<OracleDataGuardReplicationCluster> {

  @Override
  protected OracleDataGuardReplicationCluster createCluster() {
    return new OracleDataGuardReplicationCluster();
  }

  @Override
  protected Duration getMaxLag() {
    // RDS read replicas cannot be stopped, so the local/CI outage command reboots the replica.
    // Use a window shorter than the managed reboot so the test observes the outage before recovery.
    return Duration.ofSeconds(30);
  }

  @Override
  protected Duration getExporterAcknowledgementTimeout() {
    return Duration.ofMinutes(2);
  }
}
