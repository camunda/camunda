/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.config;

import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "camunda.identity.init")
public class IdentityConfiguration {
  private List<CamundaUserWithPassword> users = new ArrayList<>();

  public IdentityConfiguration() {}

  public IdentityConfiguration(final List<CamundaUserWithPassword> users) {
    this.users = users;
  }

  public List<CamundaUserWithPassword> getUsers() {
    return users;
  }

  public void setUsers(final List<CamundaUserWithPassword> users) {
    this.users = users;
  }
}
