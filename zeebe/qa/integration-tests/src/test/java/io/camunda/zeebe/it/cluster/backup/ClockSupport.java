/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.PinTimeRequest;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface ClockSupport {

  default Instant currentTime(final TestStandaloneBroker broker) {
    final var clockActuator = ActorClockActuator.of(broker);
    return clockActuator.getCurrentClock().instant();
  }

  default void progressClock(final TestStandaloneBroker broker, final long millis) {
    final var clockActuator = ActorClockActuator.of(broker);
    clockActuator.addTime(new AddTimeRequest(millis));
  }

  /**
   * Advances every broker, the one running the checkpoint scheduler last. The scheduler fires as
   * soon as its own clock passes the deadline; if the partition leaders have not advanced yet, they
   * stamp the new checkpoint with the previous time, and retention then computes its window from
   * that stale timestamp.
   */
  default void progressClock(final TestCluster cluster, final long millis) {
    schedulerBrokerLast(cluster).forEach(broker -> progressClock(broker, millis));
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

  /**
   * Pins every broker to the same instant, the one running the checkpoint scheduler last. Pinning
   * each broker to its own current time instead leaves later brokers ahead by the actuator
   * round-trips, and the scheduler, which compares checkpoint timestamps from all leaders against
   * its own clock, can then wait for a deadline that a pinned clock never reaches.
   */
  default void pinClock(final TestCluster cluster) {
    final var brokers = schedulerBrokerLast(cluster);
    final long pinnedTime = ActorClockActuator.of(brokers.getLast()).getCurrentClock().epochMilli();
    brokers.forEach(
        broker -> ActorClockActuator.of(broker).pinClock(new PinTimeRequest(pinnedTime)));
  }

  /**
   * The checkpoint scheduler runs on the member with the lowest id (see {@code
   * CheckpointSchedulingService}), so it is ordered last.
   */
  private static List<TestStandaloneBroker> schedulerBrokerLast(final TestCluster cluster) {
    return cluster.brokers().entrySet().stream()
        .sorted(Map.Entry.<MemberId, TestStandaloneBroker>comparingByKey().reversed())
        .map(Map.Entry::getValue)
        .toList();
  }
}
