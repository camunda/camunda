/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties()
public class UnifiedConfiguration {

  private Camunda camunda = new Camunda();

  public Camunda getCamunda() {
    return camunda;
  }

  public void setCamunda(final Camunda camunda) {
    this.camunda = camunda;
  }
}
