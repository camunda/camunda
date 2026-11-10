/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api.mailbox;

import java.util.Optional;

/**
 * Supplies the mailbox to the gateway. An implementation is registered with the {@link
 * java.util.ServiceLoader}, in {@code
 * META-INF/services/io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetupProvider}. The
 * module that provides the database storage contains it, next to the provider for the broker.
 *
 * <p>The provider decides if the mailbox is on. It returns an empty result if the deployment does
 * not use it. There can be at most one provider that returns a setup.
 */
public interface GatewayMailboxSetupProvider {

  /**
   * Called once for each broker client.
   *
   * @param memberId the id of the cluster member that hosts the gateway. For an embedded gateway it
   *     is the id of its broker, which contains the zone.
   * @return the setup, or empty if the mailbox is off
   */
  Optional<GatewayMailboxSetup> create(String memberId);
}
