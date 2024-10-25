/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;

public class JobVariablesCollector {

  private final VariableState variableState;
  private final UserTaskState userTaskState;
  private final ElementInstanceState elementInstanceState;

  public JobVariablesCollector(final ProcessingState processingState) {
    variableState = processingState.getVariableState();
    userTaskState = processingState.getUserTaskState();
    elementInstanceState = processingState.getElementInstanceState();
  }

  public void setJobVariables(
      final Collection<DirectBuffer> requestedVariables, final JobRecord jobRecord) {
    final long elementInstanceKey = jobRecord.getElementInstanceKey();
    final DirectBuffer processVariables;
    if (elementInstanceKey < 0) {
      processVariables = DocumentValue.EMPTY_DOCUMENT;
    } else if (requestedVariables.isEmpty()) {
      processVariables = variableState.getVariablesAsDocument(elementInstanceKey);
    } else {
      processVariables =
          variableState.getVariablesAsDocument(elementInstanceKey, requestedVariables);
    }
    jobRecord.setVariables(processVariables);
  }
}
