/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationTerminateByIdInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationTerminateByKeyInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationTerminateInstruction;
import java.util.List;
import java.util.Set;

public class ProcessInstanceModificationTerminateInstructionDeserializer
    extends AbstractRequestDeserializer<ProcessInstanceModificationTerminateInstruction> {

  private static final String ELEMENT_ID_FIELD = "elementId";
  private static final String ELEMENT_INSTANCE_KEY_FIELD = "elementInstanceKey";
  private static final List<String> SUPPORTED_FIELDS =
      List.of(ELEMENT_ID_FIELD, ELEMENT_INSTANCE_KEY_FIELD);

  @Override
  protected List<String> getSupportedFields() {
    return SUPPORTED_FIELDS;
  }

  @Override
  protected Class<? extends ProcessInstanceModificationTerminateInstruction> getResultType(
      final Set<String> presentFields) {
    if (presentFields.contains(ELEMENT_INSTANCE_KEY_FIELD)) {
      return ProcessInstanceModificationTerminateByKeyInstruction.class;
    }
    return ProcessInstanceModificationTerminateByIdInstruction.class;
  }
}
