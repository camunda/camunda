/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg;

public class FlowControlCfg {
  private LimitCfg append;

  public LimitCfg getAppend() {
    return append;
  }

  public void setAppend(final LimitCfg append) {
    this.append = append;
  }
}
