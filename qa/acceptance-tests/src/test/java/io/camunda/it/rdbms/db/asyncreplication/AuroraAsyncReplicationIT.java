/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.asyncreplication;

import io.camunda.it.rdbms.db.util.AuroraReplicationCluster;
import io.camunda.it.rdbms.db.util.ReplicationClusterContainer;
import org.junit.jupiter.api.Tag;

/**
 * Runs the {@link AsyncReplicationIT} suite against a pre-provisioned AWS Aurora Global Database
 * instead of local containers (see {@link AuroraReplicationCluster}).
 */
@Tag("rdbms-aurora")
public class AuroraAsyncReplicationIT extends AsyncReplicationIT {

  @Override
  protected ReplicationClusterContainer createCluster() {
    return new AuroraReplicationCluster();
  }
}
