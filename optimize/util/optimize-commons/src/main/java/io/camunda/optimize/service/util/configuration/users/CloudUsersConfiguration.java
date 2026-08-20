/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudUsersConfiguration {

  private String accountsUrl;

  public CloudUsersConfiguration() {}

  // Only here for backwards compatibility as the param got renamed to accountsUrl
  @Deprecated
  public void setUsersUrl(final String usersUrl) {
    accountsUrl = usersUrl;
  }

  public String getAccountsUrl() {
    return accountsUrl;
  }

  public void setAccountsUrl(final String accountsUrl) {
    this.accountsUrl = accountsUrl;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CloudUsersConfiguration;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CloudUsersConfiguration that = (CloudUsersConfiguration) o;
    return Objects.equals(accountsUrl, that.accountsUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(accountsUrl);
  }

  @Override
  public String toString() {
    return "CloudUsersConfiguration(accountsUrl=" + getAccountsUrl() + ")";
  }
}
