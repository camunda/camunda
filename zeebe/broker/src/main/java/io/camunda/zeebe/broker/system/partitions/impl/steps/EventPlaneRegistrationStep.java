/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.eventplane.EventPlaneRegistration;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import org.jspecify.annotations.Nullable;

/**
 * Tells the tenant's {@link EventPlaneRegistration} while this broker leads the partition, so that
 * the tenant registers in its Event Plane while the broker leads any of its partitions. Without
 * {@code camunda.event-plane.endpoint}, there is no registration, and the step does nothing.
 *
 * <p>Each partition has its own instance of this step; the registration is the tenant's, shared by
 * its partitions on this broker. Registering is best effort: it never holds up a transition.
 */
public final class EventPlaneRegistrationStep implements PartitionTransitionStep {

  private final @Nullable EventPlaneRegistration registration;
  private boolean leading;

  public EventPlaneRegistrationStep(final @Nullable EventPlaneRegistration registration) {
    this.registration = registration;
  }

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (registration != null && leading) {
      leading = false;
      registration.stopLeading(context.partitionId().number());
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (registration != null && targetRole == Role.LEADER) {
      leading = true;
      registration.lead(context.partitionId().number());
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "EventPlaneRegistration";
  }
}
