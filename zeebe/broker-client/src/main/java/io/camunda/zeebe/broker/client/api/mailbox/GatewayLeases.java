/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api.mailbox;

import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import java.time.Duration;

/**
 * The leases of the brokers, for a gateway of a cluster in lease mode. The gateway then takes the
 * leaders from the leases that are alive, instead of from the leader records in the mailbox store.
 *
 * @param store the lease rows that the brokers write
 * @param leaseDuration the lease duration of the brokers
 */
public record GatewayLeases(LeaseStore store, Duration leaseDuration) {}
