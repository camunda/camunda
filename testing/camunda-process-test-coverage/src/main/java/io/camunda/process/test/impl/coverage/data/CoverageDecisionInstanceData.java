/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.coverage.data;

import io.camunda.client.api.search.response.DecisionInstance;
import org.immutables.value.Value;

/**
 * Coverage input data for a single decision evaluation.
 *
 * <p>Contains the evaluated decision instance used for decision coverage aggregation.
 */
@Value.Immutable
public interface CoverageDecisionInstanceData {

  /** Returns the evaluated decision instance metadata. */
  DecisionInstance getDecisionInstance();
}
