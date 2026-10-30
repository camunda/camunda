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
import java.util.Map;
import java.util.stream.Collectors;
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

    final DirectBuffer jobVariables =
        switch (jobRecord.getJobKind()) {
          case BPMN_ELEMENT, EXECUTION_LISTENER, AD_HOC_SUB_PROCESS -> processVariables;
          case TASK_LISTENER -> {
            final var taskVariablesMap = getTaskVariables(requestedVariables, elementInstanceKey);

            // merge the two variables maps favoring the task variables over process variables
            final Map<String, Object> taskListenersVariables =
                MsgPackConverter.convertToMap(processVariables);
            taskListenersVariables.putAll(taskVariablesMap);
            yield BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(taskListenersVariables));
          }
        };

    jobRecord.setVariables(jobVariables);
  }

  private Map<String, Object> getTaskVariables(
      final Collection<DirectBuffer> requestedVariables, final long elementInstanceKey) {
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (elementInstance == null) {
      return Map.of();
    }
    final var userTaskIntermediateState =
        userTaskState.getIntermediateState(elementInstance.getUserTaskKey());
    if (userTaskIntermediateState == null) {
      return Map.of();
    }
    final var taskVariables = userTaskIntermediateState.getRecord().getVariablesBuffer();
    if (taskVariables.capacity() <= 0) {
      return Map.of();
    }
    final Map<String, Object> taskVariablesMap = MsgPackConverter.convertToMap(taskVariables);
    if (requestedVariables.isEmpty()) {
      return taskVariablesMap;
    }
    return taskVariablesMap.entrySet().stream()
        .filter(e -> requestedVariables.contains(BufferUtil.wrapString(e.getKey())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
