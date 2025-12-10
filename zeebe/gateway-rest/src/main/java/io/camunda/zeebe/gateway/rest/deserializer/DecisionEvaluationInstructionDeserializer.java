/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationById;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationByKey;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationInstruction;
import java.util.List;
import java.util.Set;

public class DecisionEvaluationInstructionDeserializer
    extends AbstractRequestDeserializer<DecisionEvaluationInstruction> {

  private static final String DECISION_DEFINITION_ID_FIELD = "decisionDefinitionId";
  private static final String DECISION_DEFINITION_KEY_FIELD = "decisionDefinitionKey";
  private static final List<String> SUPPORTED_FIELDS =
      List.of(DECISION_DEFINITION_ID_FIELD, DECISION_DEFINITION_KEY_FIELD);

  @Override
  protected List<String> getSupportedFields() {
    return SUPPORTED_FIELDS;
  }

  @Override
  protected Class<? extends DecisionEvaluationInstruction> getResultType(
      final Set<String> presentFields) {
    if (presentFields.contains(DECISION_DEFINITION_KEY_FIELD)) {
      return DecisionEvaluationByKey.class;
    }
    return DecisionEvaluationById.class;
  }
}
