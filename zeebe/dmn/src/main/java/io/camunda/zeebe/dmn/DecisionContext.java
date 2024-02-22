/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import java.util.Map;

/**
 * {@link ParsedDecision Decisions} can only be made within in a specific context. The context must
 * contain all input data required by the decision in the {@link ParsedDecisionRequirementsGraph
 * decision requirements graph} in order to successfully make a decision.
 *
 * @see DecisionEngine
 */
public interface DecisionContext {

  /**
   * @return the Context as map
   */
  Map<String, Object> toMap();
}
