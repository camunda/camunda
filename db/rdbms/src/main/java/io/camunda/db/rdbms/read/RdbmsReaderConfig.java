/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read;

import io.camunda.util.ObjectBuilder;

public record RdbmsReaderConfig(
    /*
     * The maximum number of total hits to return in search results.
     * This is used to limit the total hits count to prevent performance issues.
     */
    int maxTotalHits) {

  public static final int DEFAULT_MAX_TOTAL_HITS = 10000;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder implements ObjectBuilder<RdbmsReaderConfig> {

    private int maxTotalHits = DEFAULT_MAX_TOTAL_HITS;

    public Builder maxTotalHits(final int maxTotalHits) {
      this.maxTotalHits = maxTotalHits;
      return this;
    }

    @Override
    public RdbmsReaderConfig build() {
      return new RdbmsReaderConfig(maxTotalHits);
    }
  }
}
