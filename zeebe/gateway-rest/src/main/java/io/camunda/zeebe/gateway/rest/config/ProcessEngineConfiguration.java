/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zeebe.broker.experimental.engine.validators")
public class ProcessEngineConfiguration {

  private int maxNameFieldLength = 32 * 1024;

  public int getMaxNameFieldLength() {
    return maxNameFieldLength;
  }

  public void setMaxNameFieldLength(final int maxNameFieldLength) {
    this.maxNameFieldLength = maxNameFieldLength;
  }
}
