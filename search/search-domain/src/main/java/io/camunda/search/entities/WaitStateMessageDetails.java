/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.util.ObjectBuilder;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WaitStateMessageDetails(@Nullable String messageName, @Nullable String correlationKey)
    implements WaitStateDetails {

  @Override
  public WaitStateDetails.WaitStateType waitStateType() {
    return WaitStateDetails.WaitStateType.MESSAGE;
  }

  public static class Builder implements ObjectBuilder<WaitStateMessageDetails> {
    private @Nullable String messageName;
    private @Nullable String correlationKey;

    public Builder messageName(final @Nullable String messageName) {
      this.messageName = messageName;
      return this;
    }

    public Builder correlationKey(final @Nullable String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    @Override
    public WaitStateMessageDetails build() {
      return new WaitStateMessageDetails(messageName, correlationKey);
    }
  }
}
