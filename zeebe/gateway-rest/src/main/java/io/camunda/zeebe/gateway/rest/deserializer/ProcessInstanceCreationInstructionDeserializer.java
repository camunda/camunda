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
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstructionById;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstructionByKey;
import io.camunda.zeebe.gateway.rest.exception.DeserializationException;
import java.io.IOException;
import java.util.ArrayList;

public class ProcessInstanceCreationInstructionDeserializer extends
    JsonDeserializer<ProcessInstanceCreationInstruction> {

  private static final String PROCESS_DEFINITION_KEY_FIELD = "processDefinitionKey";
  private static final String PROCESS_DEFINITION_ID_FIELD = "processDefinitionId";

  @Override
  public ProcessInstanceCreationInstruction deserialize(final JsonParser jsonParser,
      final DeserializationContext deserializationContext) throws IOException {
    final var codec = jsonParser.getCodec();
    final var treeNode = codec.readTree(jsonParser);

    // Collect all field names into a list
    final var fieldNames = new ArrayList<>();
    final var it = treeNode.fieldNames();
    while (it.hasNext()) {
      fieldNames.add(it.next());
    }

    if (fieldNames.contains(PROCESS_DEFINITION_ID_FIELD) && fieldNames.contains(
        PROCESS_DEFINITION_KEY_FIELD)) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(getErrorMessageParam()));
    } else if (!fieldNames.contains(PROCESS_DEFINITION_ID_FIELD) && !fieldNames.contains(
        PROCESS_DEFINITION_KEY_FIELD)) {
      throw new DeserializationException(
          ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(getErrorMessageParam()));
    }

    if (fieldNames.contains(PROCESS_DEFINITION_KEY_FIELD)) {
      return codec.treeToValue(treeNode, ProcessInstanceCreationInstructionByKey.class);
    }
    return codec.treeToValue(treeNode, ProcessInstanceCreationInstructionById.class);
  }

  private static String getErrorMessageParam() {
    return "[%s, %s]".formatted(PROCESS_DEFINITION_ID_FIELD, PROCESS_DEFINITION_KEY_FIELD);
  }
}
