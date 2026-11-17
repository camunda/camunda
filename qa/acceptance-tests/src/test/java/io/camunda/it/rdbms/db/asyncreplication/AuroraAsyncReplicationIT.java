/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.asyncreplication;

import io.camunda.it.rdbms.db.util.AuroraPostgresReplicationCluster;
import java.time.Duration;
import org.junit.jupiter.api.Tag;

/**
 * Runs the {@link AsyncReplicationIT} suite against a pre-provisioned AWS Aurora <b>PostgreSQL</b>
 * Global Database instead of local containers (see {@link AuroraPostgresReplicationCluster}).
 */
@Tag("rdbms-aurora")
public class AuroraAsyncReplicationIT extends AsyncReplicationIT<AuroraPostgresReplicationCluster> {

  @Override
  protected AuroraPostgresReplicationCluster createCluster() {
    return new AuroraPostgresReplicationCluster();
  }

  @Override
  protected Duration getMaxLag() {
    return Duration.ofMinutes(5);
  }
}
