/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

public class DatabaseSecurity {

  private String username;
  private String password;
  private DatabaseSSLConfiguration ssl;

  public DatabaseSecurity() {}

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public DatabaseSSLConfiguration getSsl() {
    return ssl;
  }

  public void setSsl(final DatabaseSSLConfiguration ssl) {
    this.ssl = ssl;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DatabaseSecurity;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "DatabaseSecurity(username="
        + getUsername()
        + ", password="
        + getPassword()
        + ", ssl="
        + getSsl()
        + ")";
  }
}
