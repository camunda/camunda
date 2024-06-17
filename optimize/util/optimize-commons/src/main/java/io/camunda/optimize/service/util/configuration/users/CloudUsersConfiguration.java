/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudUsersConfiguration {

  private String accountsUrl;

  // Only here for backwards compatibility as the param got renamed to accountsUrl
  @Deprecated
  public void setUsersUrl(final String usersUrl) {
    this.accountsUrl = usersUrl;
  }
}
