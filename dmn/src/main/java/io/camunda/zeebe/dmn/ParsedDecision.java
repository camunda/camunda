/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

/**
 * A parsed DMN decision. A decision denotes the act of choosing among multiple possible options. It
 * is contained in a {@link ParsedDecisionRequirementsGraph decision requirements graph (DRG)} that
 * shows how a decision can be made.
 *
 * @see DecisionEngine
 */
public interface ParsedDecision {

  /**
   * @return the name of the decision
   */
  String getName();

  /**
   * @return the id of the decision
   */
  String getId();
}
