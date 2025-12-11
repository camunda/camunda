/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationMoveByIdInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationMoveByKeyInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationMoveInstruction;
import java.util.List;
import java.util.Set;

public class ProcessInstanceModificationMoveInstructionDeserializer
    extends AbstractRequestDeserializer<ProcessInstanceModificationMoveInstruction> {

  private static final String SOURCE_ELEMENT_ID = "sourceElementId";
  private static final String SOURCE_ELEMENT_INSTANCE_KEY = "sourceElementInstanceKey";

  private static final List<String> SUPPORTED_FIELDS =
      List.of(SOURCE_ELEMENT_ID, SOURCE_ELEMENT_INSTANCE_KEY);

  @Override
  protected List<String> getSupportedFields() {
    return SUPPORTED_FIELDS;
  }

  @Override
  protected Class<? extends ProcessInstanceModificationMoveInstruction> getResultType(
      final Set<String> presentFields) {
    if (presentFields.contains(SOURCE_ELEMENT_INSTANCE_KEY)) {
      return ProcessInstanceModificationMoveByKeyInstruction.class;
    }
    return ProcessInstanceModificationMoveByIdInstruction.class;
  }
}
