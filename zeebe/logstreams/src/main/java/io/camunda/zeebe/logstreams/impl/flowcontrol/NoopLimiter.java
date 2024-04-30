/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter;
import java.util.Optional;

final class NoopLimiter implements Limiter<Void> {

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private final Optional<Listener> noop_listener =
      Optional.of(
          new Listener() {

            @Override
            public void onSuccess() {}

            @Override
            public void onIgnore() {}

            @Override
            public void onDropped() {}
          });

  @Override
  public Optional<Listener> acquire(final Void context) {
    return noop_listener;
  }
}
