/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;

public class RdbmsQuery {

  /** The maximum number of total hits to return in search results. */
  private int maxTotalHits = RdbmsReaderConfig.DEFAULT_MAX_TOTAL_HITS;

  public int getMaxTotalHits() {
    return maxTotalHits;
  }

  public void setMaxTotalHits(final int maxTotalHits) {
    this.maxTotalHits = maxTotalHits;
  }

  public RdbmsReaderConfig toReaderConfig() {
    return RdbmsReaderConfig.builder().maxTotalHits(maxTotalHits).build();
  }
}
