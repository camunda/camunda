/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import java.util.List;
import java.util.Set;

public class ProcessInstanceCreationInstructionDeserializer
    extends AbstractRequestDeserializer<ProcessInstanceCreationInstruction> {

  private static final String PROCESS_DEFINITION_KEY_FIELD = "processDefinitionKey";
  private static final String PROCESS_DEFINITION_ID_FIELD = "processDefinitionId";
  private static final List<String> SUPPORTED_FIELDS =
      List.of(PROCESS_DEFINITION_ID_FIELD, PROCESS_DEFINITION_KEY_FIELD);

  @Override
  protected List<String> getSupportedFields() {
    return SUPPORTED_FIELDS;
  }

  @Override
  protected Class<? extends ProcessInstanceCreationInstruction> getResultType(
      final Set<String> presentFields) {
    if (presentFields.contains(PROCESS_DEFINITION_KEY_FIELD)) {
      return ProcessInstanceCreationInstructionByKey.class;
    }
    return ProcessInstanceCreationInstructionById.class;
  }
}
