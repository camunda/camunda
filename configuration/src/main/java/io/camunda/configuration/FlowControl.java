/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class FlowControl {

  /** Configure rate limit */
  @NestedConfigurationProperty private Write write = null;

  public Write getWrite() {
    return write;
  }

  public void setWrite(final Write write) {
    this.write = write;
  }
}
