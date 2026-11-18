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

import io.camunda.zeebe.gateway.rest.exception.DeserializationException;
import java.util.List;
import java.util.Set;

/**
 * Selects between the by-key and by-ID variant of a process definition suspension or resumption
 * instruction. A version and a tenant only qualify a process definition ID, so they cannot be
 * combined with a key.
 */
abstract class AbstractProcessDefinitionSuspensionInstructionDeserializer<T>
    extends AbstractRequestDeserializer<T> {

  private static final String PROCESS_DEFINITION_KEY_FIELD = "processDefinitionKey";
  private static final String PROCESS_DEFINITION_ID_FIELD = "processDefinitionId";
  private static final String PROCESS_DEFINITION_VERSION_FIELD = "processDefinitionVersion";
  private static final String TENANT_ID_FIELD = "tenantId";
  private static final List<String> SUPPORTED_FIELDS =
      List.of(
          PROCESS_DEFINITION_ID_FIELD,
          PROCESS_DEFINITION_KEY_FIELD,
          PROCESS_DEFINITION_VERSION_FIELD,
          TENANT_ID_FIELD);

  protected abstract Class<? extends T> byKeyType();

  protected abstract Class<? extends T> byIdType();

  @Override
  protected List<String> getSupportedFields() {
    return SUPPORTED_FIELDS;
  }

  @Override
  protected void validateFields(final Set<String> presentFields) {
    final var hasId = presentFields.contains(PROCESS_DEFINITION_ID_FIELD);
    final var hasKey = presentFields.contains(PROCESS_DEFINITION_KEY_FIELD);
    final var hasVersion = presentFields.contains(PROCESS_DEFINITION_VERSION_FIELD);

    if (hasId && hasKey) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
              List.of(PROCESS_DEFINITION_ID_FIELD, PROCESS_DEFINITION_KEY_FIELD)));
    }
    if (hasKey && hasVersion) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
              List.of(PROCESS_DEFINITION_KEY_FIELD, PROCESS_DEFINITION_VERSION_FIELD)));
    }
    if (hasKey && presentFields.contains(TENANT_ID_FIELD)) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
              List.of(PROCESS_DEFINITION_KEY_FIELD, TENANT_ID_FIELD)));
    }
    if (!hasId && !hasKey) {
      throw new DeserializationException(
          ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
              List.of(PROCESS_DEFINITION_ID_FIELD, PROCESS_DEFINITION_KEY_FIELD)));
    }
  }

  @Override
  protected Class<? extends T> getResultType(final Set<String> presentFields) {
    return presentFields.contains(PROCESS_DEFINITION_KEY_FIELD) ? byKeyType() : byIdType();
  }
}
