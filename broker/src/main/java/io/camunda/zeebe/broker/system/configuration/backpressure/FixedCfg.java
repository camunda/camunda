/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.configuration.backpressure;

import static io.zeebe.broker.system.configuration.ConfigurationUtil.checkPositive;

public class FixedCfg {

  private int limit = 20;

  public int getLimit() {
    return limit;
  }

  public void setLimit(final int limit) {
    checkPositive(limit, "limit");
    this.limit = limit;
  }

  @Override
  public String toString() {
    return "FixedCfg{" + "limit=" + limit + '}';
  }
}
