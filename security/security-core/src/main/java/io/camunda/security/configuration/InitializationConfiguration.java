/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.ArrayList;
import java.util.List;

public class InitializationConfiguration {

  public static final String DEFAULT_USER_USERNAME = "demo";
  public static final String DEFAULT_USER_PASSWORD = "demo";
  public static final String DEFAULT_USER_NAME = "Demo";
  public static final String DEFAULT_USER_EMAIL = "demo@demo.com";

  private List<ConfiguredUser> users = new ArrayList<>();
  private List<ConfiguredMapping> mappings = new ArrayList<>();

  public List<ConfiguredUser> getUsers() {
    return users;
  }

  public void setUsers(final List<ConfiguredUser> users) {
    this.users = users;
  }

  public List<ConfiguredMapping> getMappings() {
    return mappings;
  }

  public void setMappings(final List<ConfiguredMapping> mappings) {
    this.mappings = mappings;
  }
}
