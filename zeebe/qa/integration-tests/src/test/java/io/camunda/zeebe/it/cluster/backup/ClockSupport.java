/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.PinTimeRequest;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Instant;

public interface ClockSupport {

  default Instant currentTime(final TestStandaloneBroker broker) {
    final var clockActuator = ActorClockActuator.of(broker);
    return clockActuator.getCurrentClock().instant();
  }

  default void progressClock(final TestStandaloneBroker broker, final long millis) {
    final var clockActuator = ActorClockActuator.of(broker);
    clockActuator.addTime(new AddTimeRequest(millis));
  }

  default void progressClock(final TestCluster cluster, final long millis) {
    cluster.brokers().values().forEach(broker -> progressClock(broker, millis));
  }

  default void resetTime(final TestStandaloneBroker broker) {
    final var clockActuator = ActorClockActuator.of(broker);
    clockActuator.resetTime();
  }

  default void resetTime(final TestCluster cluster) {
    cluster.brokers().values().forEach(this::resetTime);
  }

  default void pinClock(final TestStandaloneBroker broker) {
    final var clock = ActorClockActuator.of(broker);
    clock.pinClock(new PinTimeRequest(clock.getCurrentClock().epochMilli()));
  }

  default void pinClock(final TestCluster cluster) {
    cluster.brokers().values().forEach(this::pinClock);
  }
}
