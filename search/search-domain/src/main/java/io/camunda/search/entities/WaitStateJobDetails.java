/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.util.ObjectBuilder;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WaitStateJobDetails(
    @Nullable Long jobKey, @Nullable String jobType, @Nullable JobKind jobKind)
    implements WaitStateDetails {

  @Override
  public WaitStateType waitStateType() {
    return WaitStateType.JOB;
  }

  public static class Builder implements ObjectBuilder<WaitStateJobDetails> {
    private @Nullable Long jobKey;
    private @Nullable String jobType;
    private @Nullable JobKind jobKind;

    public Builder jobKey(final @Nullable Long jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder jobType(final @Nullable String jobType) {
      this.jobType = jobType;
      return this;
    }

    public Builder jobKind(final @Nullable JobKind jobKind) {
      this.jobKind = jobKind;
      return this;
    }

    @Override
    public WaitStateJobDetails build() {
      return new WaitStateJobDetails(jobKey, jobType, jobKind);
    }
  }
}
