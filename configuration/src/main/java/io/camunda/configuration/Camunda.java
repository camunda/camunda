/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Section of the new Unified Configuration system that wraps all the config keys that belong to the
 * proprietary, configurable Camunda components.
 */
@ConfigurationProperties(prefix = Camunda.PREFIX)
public class Camunda {

  public static final String PREFIX = "camunda";

  private Data data = new Data();

  public Data getData() {
    return data;
  }

  public void setData(Data data) {
    this.data = data;
  }
}
