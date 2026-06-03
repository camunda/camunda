/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.coverage.data;

import io.camunda.client.api.search.response.DecisionDefinition;
import org.immutables.value.Value;

/**
 * Coverage input data for a deployed decision definition.
 *
 * <p>Contains the decision definition metadata and DMN XML required to calculate and render
 * decision-level coverage.
 */
@Value.Immutable
public interface CoverageDecisionDefinitionData {

  /** Returns the deployed decision definition metadata. */
  DecisionDefinition getDecisionDefinition();

  /** Returns the DMN XML of the decision definition. */
  String getXml();
}
