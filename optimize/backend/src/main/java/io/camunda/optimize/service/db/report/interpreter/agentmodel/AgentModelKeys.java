/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.agentmodel;

/**
 * How agentRun reports key a model. The key is {@code provider:model}, so the same model served by
 * two providers stays two entries; the label is the model as the connector reports it. A run
 * without a provider or model is counted under {@link #UNKNOWN}, so per-model sums still add up to
 * the total.
 */
public final class AgentModelKeys {

  public static final String PROVIDER_AGGREGATION = "agentRunProvider";
  public static final String MODEL_AGGREGATION = "agentRunModel";
  public static final String UNKNOWN = "unknown";

  private AgentModelKeys() {}

  public static String key(final String provider, final String model) {
    return provider + ":" + model;
  }
}
