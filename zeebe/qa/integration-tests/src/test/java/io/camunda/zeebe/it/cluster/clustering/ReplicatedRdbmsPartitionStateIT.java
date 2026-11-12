/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import java.util.UUID;

final class ReplicatedRdbmsPartitionStateIT extends ReplicatedRdbmsPartitionStateTestBase {

  @Override
  StateDatabase createDatabase() {
    // the database lives as long as the JVM, so that it outlives the brokers' connection pools
    return new StateDatabase(
        "jdbc:h2:mem:zeebe-state-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
  }
}
