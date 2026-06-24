/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.membership;

/**
 * A read-only view of the cluster topic registry, used by the {@link ConsumerGroupCoordinator} to
 * resolve a subscribed topic's partition count at join time.
 *
 * <p>The topic registry is owned by a separate Raft group (the metadata group), so it is not part
 * of the coordinator's replicated state and cannot be re-derived in a processor. The leader-side
 * coordinator therefore resolves the count from this live view and stamps it on the {@code
 * JOIN_GROUP} command; the processor only validates the stamped value (a count of {@code 0} means
 * the topic was unknown or not servable at request time).
 *
 * <p>Implementations must be safe to call from the coordinator actor.
 */
@FunctionalInterface
public interface TopicRegistry {

  /**
   * The partition count of the named topic if it is registered and servable, or {@code 0} if the
   * topic is unknown (or not yet/no longer servable).
   */
  int partitionCount(String topic);
}
