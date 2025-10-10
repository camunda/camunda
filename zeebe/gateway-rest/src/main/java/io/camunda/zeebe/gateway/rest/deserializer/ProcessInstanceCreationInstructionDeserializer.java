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
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstructionById;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstructionByKey;
import io.camunda.zeebe.gateway.rest.exception.DeserializationException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ProcessInstanceCreationInstructionDeserializer
    extends JsonDeserializer<ProcessInstanceCreationInstruction> {

  private static final String PROCESS_DEFINITION_KEY_FIELD = "processDefinitionKey";
  private static final String PROCESS_DEFINITION_ID_FIELD = "processDefinitionId";
  private static final Set<String> SUPPORTED_FIELDS =
      Set.of(PROCESS_DEFINITION_KEY_FIELD, PROCESS_DEFINITION_ID_FIELD);

  @Override
  public ProcessInstanceCreationInstruction deserialize(
      final JsonParser parser, final DeserializationContext context) throws IOException {
    final var codec = parser.getCodec();
    final var treeNode = codec.readTree(parser);

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
    if (treeNode.get(PROCESS_DEFINITION_KEY_FIELD) instanceof NullNode) {
      ((ObjectNode) treeNode).remove(PROCESS_DEFINITION_KEY_FIELD);
    }
    if (treeNode.get(PROCESS_DEFINITION_ID_FIELD) instanceof NullNode) {
      ((ObjectNode) treeNode).remove(PROCESS_DEFINITION_ID_FIELD);
    }

    if (presentFields.contains(PROCESS_DEFINITION_KEY_FIELD)) {
      return codec.treeToValue(treeNode, ProcessInstanceCreationInstructionByKey.class);
    } else {
      return codec.treeToValue(treeNode, ProcessInstanceCreationInstructionById.class);
    }
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
    return "[%s, %s]".formatted(PROCESS_DEFINITION_ID_FIELD, PROCESS_DEFINITION_KEY_FIELD);
  }
}
