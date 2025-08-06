/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public record MigrationStep(
    @JsonProperty("@type") String type,
    String content,
    String description,
    String version,
    String indexName,
    OffsetDateTime appliedDate,
    boolean applied) {

  public static final String STEP_TYPE = "usageMetricStep";

  public static class Builder implements ObjectBuilder<MigrationStep> {
    private String content;
    private String description;
    private String version;
    private String indexName;
    private OffsetDateTime appliedDate;
    private boolean applied;

    public Builder content(final String content) {
      this.content = content;
      return this;
    }

    public Builder description(final String description) {
      this.description = description;
      return this;
    }

    public Builder version(final String version) {
      this.version = version;
      return this;
    }

    public Builder indexName(final String indexName) {
      this.indexName = indexName;
      return this;
    }

    public Builder appliedDate(final OffsetDateTime appliedDate) {
      this.appliedDate = appliedDate;
      return this;
    }

    public Builder applied(final boolean applied) {
      this.applied = applied;
      return this;
    }

    @Override
    public MigrationStep build() {
      return new MigrationStep(
          STEP_TYPE, content, description, version, indexName, appliedDate, applied);
    }
  }
}
