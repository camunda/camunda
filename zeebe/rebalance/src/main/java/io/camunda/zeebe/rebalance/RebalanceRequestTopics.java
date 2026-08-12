/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

public enum RebalanceRequestTopics {
  TRIGGER_REBALANCE("cluster-rebalance-trigger"),
  REBALANCE_STATUS("cluster-rebalance-status"),
  CANCEL_REBALANCE("cluster-rebalance-cancel");

  private final String topic;

  RebalanceRequestTopics(final String topic) {
    this.topic = topic;
  }

  public String topic() {
    return topic;
  }
}
