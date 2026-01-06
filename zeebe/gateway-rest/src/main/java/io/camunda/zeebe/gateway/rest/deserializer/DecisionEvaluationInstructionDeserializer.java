/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationById;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationByKey;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationInstruction;
import io.camunda.zeebe.gateway.rest.exception.DeserializationException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class DecisionEvaluationInstructionDeserializer
    extends JsonDeserializer<DecisionEvaluationInstruction> {

  private static final String DECISION_DEFINITION_ID_FIELD = "decisionDefinitionId";
  private static final String DECISION_DEFINITION_KEY_FIELD = "decisionDefinitionKey";
  private static final Set<String> SUPPORTED_FIELDS =
      Set.of(DECISION_DEFINITION_ID_FIELD, DECISION_DEFINITION_KEY_FIELD);

  @Override
  public DecisionEvaluationInstruction deserialize(
      final JsonParser jsonParser, final DeserializationContext deserializationContext)
      throws IOException {
    final var codec = jsonParser.getCodec();
    final var treeNode = codec.readTree(jsonParser);

    final Set<String> presentFields = new HashSet<>();
    final var fields = treeNode.fieldNames();

    while (fields.hasNext()) {
      final String field = fields.next();
      if (SUPPORTED_FIELDS.contains(field) && !(treeNode.get(field) instanceof NullNode)) {
        presentFields.add(field);
      }
    }

    validateFields(presentFields);

    // Remove null fields from the tree to prevent parsing errors
    if (treeNode.get(DECISION_DEFINITION_ID_FIELD) instanceof NullNode) {
      ((ObjectNode) treeNode).remove(DECISION_DEFINITION_ID_FIELD);
    }
    if (treeNode.get(DECISION_DEFINITION_KEY_FIELD) instanceof NullNode) {
      ((ObjectNode) treeNode).remove(DECISION_DEFINITION_KEY_FIELD);
    }

    if (presentFields.contains(DECISION_DEFINITION_KEY_FIELD)) {
      return codec.treeToValue(treeNode, DecisionEvaluationByKey.class);
    }
    return codec.treeToValue(treeNode, DecisionEvaluationById.class);
  }

  private static void validateFields(final Set<String> presentFields) {
    if (presentFields.size() > 1) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(getErrorMessageParam()));
    }
    if (presentFields.isEmpty()) {
      throw new DeserializationException(
          ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(getErrorMessageParam()));
    }
  }

  private static String getErrorMessageParam() {
    return "[%s, %s]".formatted(DECISION_DEFINITION_ID_FIELD, DECISION_DEFINITION_KEY_FIELD);
  }
}
