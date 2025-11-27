/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstructionById;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstructionByKey;
import java.util.Set;

public class ProcessInstanceCreationInstructionDeserializer
    extends AbstractRequestDeserializer<ProcessInstanceCreationInstruction> {

  private static final String PROCESS_DEFINITION_KEY_FIELD = "processDefinitionKey";
  private static final String PROCESS_DEFINITION_ID_FIELD = "processDefinitionId";
  private static final Set<String> SUPPORTED_FIELDS =
      Set.of(PROCESS_DEFINITION_KEY_FIELD, PROCESS_DEFINITION_ID_FIELD);

  @Override
  protected Set<String> getSupportedFields() {
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
