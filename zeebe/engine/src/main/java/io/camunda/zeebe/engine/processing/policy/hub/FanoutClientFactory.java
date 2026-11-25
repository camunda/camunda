/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.fanout.client.FanoutClient;
import io.camunda.fanout.client.port.EventHandler;
import org.jspecify.annotations.NullMarked;

/**
 * Creates the fan-out client through which the engine of partition 1 receives the policy change
 * sets of its physical tenant from Hub.
 */
@NullMarked
@FunctionalInterface
public interface FanoutClientFactory {

  /** The factory of a physical tenant that is not connected to Hub; the engine never calls it. */
  FanoutClientFactory DISABLED =
      handler -> {
        throw new UnsupportedOperationException("The physical tenant is not connected to Hub");
      };

  /**
   * Creates a client that gives the policy change sets to the handler. The engine starts it once it
   * leads partition 1, and closes it when it stops leading.
   *
   * @param policyChangeSetHandler the handler of the policy change set events
   * @return a client that is not started yet
   */
  FanoutClient create(EventHandler policyChangeSetHandler);
}
