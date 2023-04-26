/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.impl.ImmutableStreamRegistry.AggregatedRemoteStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

final class RandomStreamPicker<M> implements RemoteStreamPicker<M> {

  @Override
  public AggregatedRemoteStream<M> pickStream(final Set<AggregatedRemoteStream<M>> consumers) {
    final var targets = new ArrayList<>(consumers);
    final var index = ThreadLocalRandom.current().nextInt(consumers.size());

    return targets.get(index);
  }
}
