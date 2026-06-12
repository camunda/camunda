/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
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
  private final ProcessState processState;

  public JobVariablesCollector(final ProcessingState processingState) {
    variableState = processingState.getVariableState();
    userTaskState = processingState.getUserTaskState();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
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
    setJobSecretPaths(jobRecord);
  }

  /**
   * Attaches the statically modeled secret references of the activated element to the job, so the
   * gateway can resolve them in the job's variables before returning them to the client. References
   * come from the process definition (input mappings), never from runtime variable values — which
   * is what prevents secret resolution from being triggered by injected, untrusted input.
   */
  private void setJobSecretPaths(final JobRecord jobRecord) {
    final var process =
        processState.getProcessByKeyAndTenant(
            jobRecord.getProcessDefinitionKey(), jobRecord.getTenantId());
    if (process == null) {
      return;
    }
    final var element = process.getProcess().getElementById(jobRecord.getElementId());
    if (element instanceof final ExecutableFlowNode flowNode
        && !flowNode.getInputSecretReferences().isEmpty()) {
      jobRecord.setSecretPaths(flowNode.getInputSecretReferences());
    }
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
