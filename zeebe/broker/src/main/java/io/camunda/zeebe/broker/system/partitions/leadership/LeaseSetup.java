/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.leadership.LeaseConfig;

/**
 * What the broker needs to elect the leaders of its partitions with leases in the database. If a
 * provider returns one, the broker runs in lease mode instead of taking leaders from Raft.
 *
 * @param store the lease rows, shared by all brokers of the cluster
 * @param config the timing of the leases. All brokers must use the same.
 */
public record LeaseSetup(LeaseStore store, LeaseConfig config) {}
