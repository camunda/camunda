/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class License {

  private static final String PREFIX = "camunda.license";

  private String key = "";

  public String getKey() {
    // No need to check anything: LicenseKeyProperties is already wired with the correct prefix.
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }
}
