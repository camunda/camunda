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
public record WaitStateUserTaskDetails(@Nullable Long taskKey, @Nullable String dueDate)
    implements WaitStateDetails {

  @Override
  public WaitStateDetails.WaitStateType waitStateType() {
    return WaitStateDetails.WaitStateType.USER_TASK;
  }

  public static class Builder implements ObjectBuilder<WaitStateUserTaskDetails> {
    private @Nullable Long taskKey;
    private @Nullable String dueDate;

    public Builder taskKey(final @Nullable Long taskKey) {
      this.taskKey = taskKey;
      return this;
    }

    public Builder dueDate(final @Nullable String dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    @Override
    public WaitStateUserTaskDetails build() {
      return new WaitStateUserTaskDetails(taskKey, dueDate);
    }
  }
}
