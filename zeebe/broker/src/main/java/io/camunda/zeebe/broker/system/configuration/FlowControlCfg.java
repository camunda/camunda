/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.RateLimitCfg;
import java.util.Objects;

public class FlowControlCfg implements ConfigurationEntry {
  private LimitCfg request = null;
  private RateLimitCfg write = new RateLimitCfg();

  public FlowControlCfg() {}

  public LimitCfg getRequest() {
    return request;
  }

  public void setRequest(final LimitCfg request) {
    this.request = request;
  }

  public RateLimitCfg getWrite() {
    return write;
  }

  public void setWrite(final RateLimitCfg write) {
    this.write = write;
  }

  @Override
  public int hashCode() {
    return Objects.hash(request, write);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final FlowControlCfg that)) {
      return false;
    }
    return Objects.equals(request, that.request) && Objects.equals(write, that.write);
  }
}
