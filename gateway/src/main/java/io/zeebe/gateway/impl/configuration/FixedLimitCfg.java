/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration;

import io.zeebe.util.Environment;
import java.util.Objects;

public class FixedLimitCfg {

  private int limit = 20;

  public int getLimit() {
    return limit;
  }

  public FixedLimitCfg setLimit(final int limit) {
    this.limit = limit;
    return this;
  }

  public void init(final Environment environment) {
    environment
        .getInt(EnvironmentConstants.ENV_GATEWAY_BACKPRESSURE_FIXED_LIMIT)
        .ifPresent(this::setLimit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(limit);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FixedLimitCfg that = (FixedLimitCfg) o;
    return limit == that.limit;
  }

  @Override
  public String toString() {
    return "FixedLimitCfg{" + "limit=" + limit + '}';
  }
}
