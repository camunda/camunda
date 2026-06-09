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
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WaitStateConditionDetails(@Nullable String expression, @Nullable List<String> events)
    implements WaitStateDetails {

  public WaitStateConditionDetails {
    events = events != null ? events : new ArrayList<>();
  }

  @Override
  public WaitStateDetails.WaitStateType waitStateType() {
    return WaitStateDetails.WaitStateType.CONDITION;
  }

  public static class Builder implements ObjectBuilder<WaitStateConditionDetails> {
    private @Nullable String expression;
    private @Nullable List<String> events;

    public Builder expression(final @Nullable String expression) {
      this.expression = expression;
      return this;
    }

    public Builder events(final @Nullable List<String> events) {
      this.events = events;
      return this;
    }

    @Override
    public WaitStateConditionDetails build() {
      return new WaitStateConditionDetails(expression, events);
    }
  }
}
