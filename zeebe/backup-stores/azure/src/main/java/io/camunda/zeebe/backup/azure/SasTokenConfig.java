/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

public record SasTokenConfig(String value, SasTokenType type) {

  public static class Builder {

    private String value;
    private SasTokenType type;

    public Builder withValue(final String value) {
      this.value = value;
      return this;
    }

    public Builder withTokenType(final SasTokenType type) {
      this.type = type;
      return this;
    }

    public SasTokenConfig build() {

      return new SasTokenConfig(value, type);
    }
  }
}
