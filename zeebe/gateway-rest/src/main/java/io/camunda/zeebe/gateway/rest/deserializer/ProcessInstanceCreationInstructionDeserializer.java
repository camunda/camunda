/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_VERSION_TAG_REQUIRES_ID;

import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByVersionTag;
import io.camunda.zeebe.gateway.rest.exception.DeserializationException;
import java.util.List;
import java.util.Set;

public class ProcessInstanceCreationInstructionDeserializer
    extends AbstractRequestDeserializer<ProcessInstanceCreationInstruction> {

  private static final String PROCESS_DEFINITION_KEY_FIELD = "processDefinitionKey";
  private static final String PROCESS_DEFINITION_ID_FIELD = "processDefinitionId";
  private static final String PROCESS_DEFINITION_VERSION_FIELD = "processDefinitionVersion";
  private static final String PROCESS_DEFINITION_VERSION_TAG_FIELD = "processDefinitionVersionTag";
  private static final List<String> SUPPORTED_FIELDS =
      List.of(
          PROCESS_DEFINITION_ID_FIELD,
          PROCESS_DEFINITION_KEY_FIELD,
          PROCESS_DEFINITION_VERSION_FIELD,
          PROCESS_DEFINITION_VERSION_TAG_FIELD);

  @Override
  protected List<String> getSupportedFields() {
    return SUPPORTED_FIELDS;
  }

  @Override
  protected void validateFields(final Set<String> presentFields) {
    final var hasId = presentFields.contains(PROCESS_DEFINITION_ID_FIELD);
    final var hasKey = presentFields.contains(PROCESS_DEFINITION_KEY_FIELD);
    final var hasVersion = presentFields.contains(PROCESS_DEFINITION_VERSION_FIELD);
    final var hasVersionTag = presentFields.contains(PROCESS_DEFINITION_VERSION_TAG_FIELD);

    if (hasId && hasKey) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
              List.of(PROCESS_DEFINITION_ID_FIELD, PROCESS_DEFINITION_KEY_FIELD)));
    }
    if (hasKey && hasVersionTag) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
              List.of(PROCESS_DEFINITION_KEY_FIELD, PROCESS_DEFINITION_VERSION_TAG_FIELD)));
    }
    if (hasVersion && hasVersionTag) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
              List.of(PROCESS_DEFINITION_VERSION_FIELD, PROCESS_DEFINITION_VERSION_TAG_FIELD)));
    }
    if (hasVersionTag && !hasId) {
      throw new DeserializationException(ERROR_MESSAGE_VERSION_TAG_REQUIRES_ID);
    }
    if (!hasId && !hasKey) {
      throw new DeserializationException(
          ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
              List.of(PROCESS_DEFINITION_ID_FIELD, PROCESS_DEFINITION_KEY_FIELD)));
    }
  }

  @Override
  protected Class<? extends ProcessInstanceCreationInstruction> getResultType(
      final Set<String> presentFields) {
    if (presentFields.contains(PROCESS_DEFINITION_KEY_FIELD)) {
      return ProcessInstanceCreationInstructionByKey.class;
    }
    if (presentFields.contains(PROCESS_DEFINITION_VERSION_TAG_FIELD)) {
      return ProcessInstanceCreationInstructionByVersionTag.class;
    }
    return ProcessInstanceCreationInstructionById.class;
  }
}
