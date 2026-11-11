/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import java.util.Optional;

/**
 * Supplies the lease store to the broker. An implementation is registered with the {@link
 * java.util.ServiceLoader}, in {@code
 * META-INF/services/io.camunda.zeebe.broker.system.partitions.leadership.LeaseSetupProvider}. The
 * module that provides the database storage contains it.
 *
 * <p>The provider decides the mode: with a setup, leaders come from the leases; without one, from
 * Raft. At most one provider may return a setup.
 */
public interface LeaseSetupProvider {

  /**
   * Called once for each partition manager of the broker. The store must be shared, so an
   * implementation should return the same store every time.
   *
   * @return the setup, or empty to take the leaders from Raft
   */
  Optional<LeaseSetup> create(BrokerCfg brokerCfg);
}
