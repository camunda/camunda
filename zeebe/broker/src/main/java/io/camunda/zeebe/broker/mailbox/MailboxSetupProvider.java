/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.mailbox;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import java.util.Optional;

/**
 * Supplies the mailbox to the broker. An implementation is registered with the {@link
 * java.util.ServiceLoader}, in {@code
 * META-INF/services/io.camunda.zeebe.broker.mailbox.MailboxSetupProvider}. The module that provides
 * the database storage contains it.
 *
 * <p>The provider decides if the mailbox is on. It returns an empty result if the deployment does
 * not use it, for example because its own settings are missing. There can be at most one provider
 * that returns a setup.
 */
public interface MailboxSetupProvider {

  /**
   * Called once for each partition manager of the broker. The store must be shared, so an
   * implementation should return the same store every time and not open a new connection pool.
   *
   * @param brokerCfg the configuration of the broker
   * @return the setup, or empty if the mailbox is off
   */
  Optional<MailboxSetup> create(BrokerCfg brokerCfg);
}
