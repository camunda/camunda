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
public record WaitStateTimerDetails(@Nullable Long dueDate, @Nullable Integer repetitions)
    implements WaitStateDetails {

  @Override
  public WaitStateDetails.WaitStateType waitStateType() {
    return WaitStateDetails.WaitStateType.TIMER;
  }

  public static class Builder implements ObjectBuilder<WaitStateTimerDetails> {
    private @Nullable Long dueDate;
    private @Nullable Integer repetitions;

    public Builder dueDate(final @Nullable Long dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    public Builder repetitions(final @Nullable Integer repetitions) {
      this.repetitions = repetitions;
      return this;
    }

    @Override
    public WaitStateTimerDetails build() {
      return new WaitStateTimerDetails(dueDate, repetitions);
    }
  }
}
