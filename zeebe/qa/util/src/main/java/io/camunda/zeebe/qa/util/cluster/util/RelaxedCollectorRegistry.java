/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster.util;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;

/** A {@link CollectorRegistry} which silently ignores duplicate metric registration. */
public final class RelaxedCollectorRegistry extends CollectorRegistry {

  @Override
  public void register(final Collector m) {
    try {
      super.register(m);
    } catch (final IllegalArgumentException ignored) {
      // ignore
    }
  }
}
