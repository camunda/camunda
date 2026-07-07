/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import java.util.Objects;

/**
 * The stable identity of one meter instance: a meter {@code name} within a cube ({@code cubeId}).
 * Keyed by name rather than declaration index so reordering a cube's meters never shifts an
 * already-allocated {@code aggId} (see {@link MeterIdRegistry}).
 */
public record MeterKey(long cubeId, String meterName) {

  public MeterKey {
    Objects.requireNonNull(meterName, "meterName");
  }
}
