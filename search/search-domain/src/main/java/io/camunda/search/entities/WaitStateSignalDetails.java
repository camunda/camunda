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
public record WaitStateSignalDetails(@Nullable String signalName) implements WaitStateDetails {

  @Override
  public WaitStateDetails.WaitStateType waitStateType() {
    return WaitStateDetails.WaitStateType.SIGNAL;
  }

  public static class Builder implements ObjectBuilder<WaitStateSignalDetails> {
    private @Nullable String signalName;

    public Builder signalName(final @Nullable String signalName) {
      this.signalName = signalName;
      return this;
    }

    @Override
    public WaitStateSignalDetails build() {
      return new WaitStateSignalDetails(signalName);
    }
  }
}
