/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.config.operate;

public class ZeebeOpensearchProperties extends OpensearchProperties {

  public static final String ZEEBE_INDEX_PREFIX_DEFAULT = "zeebe-record";
  private String prefix = ZEEBE_INDEX_PREFIX_DEFAULT;

  public ZeebeOpensearchProperties() {
    setDateFormat("yyyy-MM-dd"); // hard-coded, as not configurable on Zeebe side
    setOsDateFormat("date"); // hard-coded, as not configurable on Zeebe side
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(final String prefix) {
    this.prefix = prefix;
  }
}
