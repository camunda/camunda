/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

public enum SasTokenType {
  DELEGATION("delegation"),
  SERVICE("service"),
  ACCOUNT("account");

  private final String type;

  SasTokenType(final String type) {
    this.type = type;
  }

  public static SasTokenType from(final String type) {
    return SasTokenType.valueOf(type.toUpperCase());
  }

  public boolean isDelegation() {
    return equals(DELEGATION);
  }

  public boolean isService() {
    return equals(SERVICE);
  }

  public boolean isAccount() {
    return equals(ACCOUNT);
  }

  @Override
  public String toString() {
    return type;
  }
}
