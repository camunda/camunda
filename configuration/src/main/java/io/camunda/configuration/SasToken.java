/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.backup.azure.SasTokenConfig;

public class SasToken {

  /** The SAS token must be of the following types: "delegation", "service" or "account". */
  private SasTokenType type;

  /** Specifies the key value of the SAS token. */
  private String value;

  public static SasToken fromSasTokenConfig(final SasTokenConfig sasTokenConfig) {
    final var sasToken = new SasToken();
    sasToken.setType(SasToken.SasTokenType.valueOf(sasTokenConfig.type().name()));
    sasToken.setValue(sasTokenConfig.value());
    return sasToken;
  }

  public SasTokenType getType() {
    return type;
  }

  public void setType(final SasTokenType type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public SasTokenConfig toSasTokenConfig() {
    return new SasTokenConfig.Builder()
        .withValue(getValue())
        .withTokenType(io.camunda.zeebe.backup.azure.SasTokenType.valueOf(getType().name()))
        .build();
  }

  public enum SasTokenType {
    DELEGATION,
    SERVICE,
    ACCOUNT;
  }
}
