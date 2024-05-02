/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

/** Common interface for health actuators on the broker and the gateway. */
public interface HealthActuator {

  /**
   * Succeeds if the node is ready.
   *
   * @throws feign.FeignException if not ready
   */
  void ready();

  /**
   * Succeeds if the node is started.
   *
   * @throws feign.FeignException if not started
   */
  void startup();

  /**
   * Succeeds if the node is live.
   *
   * @throws feign.FeignException if not live
   */
  void live();

  final class NoopHealthActuator implements HealthActuator {

    @Override
    public void ready() {}

    @Override
    public void startup() {}

    @Override
    public void live() {}
  }
}
