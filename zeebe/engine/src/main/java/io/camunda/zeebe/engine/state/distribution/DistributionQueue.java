/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.distribution;

public enum DistributionQueue {
  IDENTITY("IDENTITY"),
  DEPLOYMENT("DEPLOYMENT"),
  REDISTRIBUTION("REDISTRIBUTION");

  private final String queueId;

  DistributionQueue(final String queueId) {
    this.queueId = queueId;
  }

  public String getQueueId() {
    return queueId;
  }
}
