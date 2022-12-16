/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.Collection;
import java.util.Collections;
import org.agrona.DirectBuffer;

final class JobCollector {
  private final VariableState variableState;

  public JobCollector(final VariableState variableState) {
    this.variableState = variableState;
  }

  void collectJob(final JobRecord jobRecord) {
    jobRecord.setDeadline(30_000L).setWorker("push");
    setJobVariables(Collections.emptyList(), jobRecord, jobRecord.getElementInstanceKey());
  }

  void setJobVariables(
      final Collection<DirectBuffer> requestedVariables,
      final JobRecord jobRecord,
      final long elementInstanceKey) {
    if (elementInstanceKey >= 0) {
      final DirectBuffer variables = collectVariables(requestedVariables, elementInstanceKey);
      jobRecord.setVariables(variables);
    } else {
      jobRecord.setVariables(DocumentValue.EMPTY_DOCUMENT);
    }
  }

  private DirectBuffer collectVariables(
      final Collection<DirectBuffer> variableNames, final long elementInstanceKey) {
    final DirectBuffer variables;

    if (variableNames.isEmpty()) {
      variables = variableState.getVariablesAsDocument(elementInstanceKey);
    } else {
      variables = variableState.getVariablesAsDocument(elementInstanceKey, variableNames);
    }

    return variables;
  }
}
