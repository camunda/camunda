/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import java.util.ArrayList;
import java.util.List;

/** Lists the entries of a {@link TransientPendingSubscriptionState} from outside its package. */
public final class PendingSubscriptions {

  private PendingSubscriptions() {}

  public static List<PendingSubscription> of(final TransientPendingSubscriptionState state) {
    final var entries = new ArrayList<PendingSubscription>();
    state.entriesBefore(Long.MAX_VALUE).forEach(entries::add);
    return entries;
  }
}
