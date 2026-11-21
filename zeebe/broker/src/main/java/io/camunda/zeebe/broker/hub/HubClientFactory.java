/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Creates the {@link HubClient} of a physical tenant. Spring registers the factory with the {@link
 * io.camunda.zeebe.broker.SpringBrokerBridge}; the broker starts a {@link HubUpdateReceiver} only
 * for the physical tenants that get a client.
 */
@FunctionalInterface
@NullMarked
public interface HubClientFactory {

  /** The client of the physical tenant, or empty if the tenant is not managed by Hub. */
  Optional<HubClient> create(String physicalTenantId);
}
