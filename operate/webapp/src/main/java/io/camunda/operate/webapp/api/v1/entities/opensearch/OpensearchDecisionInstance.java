/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities.opensearch;

import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstanceInput;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstanceOutput;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstanceState;
import java.util.List;

public record OpensearchDecisionInstance(
    String id,
    Long key,
    DecisionInstanceState state,
    String evaluationDate,
    String evaluationFailure,
    String evaluationFailureMessage,
    Long processDefinitionKey,
    Long processInstanceKey,
    String decisionId,
    String decisionDefinitionId,
    String decisionName,
    Integer decisionVersion,
    DecisionType decisionType,
    String result,
    List<DecisionInstanceInput> evaluatedInputs,
    List<DecisionInstanceOutput> evaluatedOutputs,
    String tenantId) {}
