/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.processtest;

import io.camunda.zeebe.engine.inmemory.InMemoryEngine;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Duration;

public class ProcessTestVerifications {

  private final InMemoryEngine engine;
  private final Duration idleTimeout;

  public ProcessTestVerifications(final InMemoryEngine engine, final Duration idleTimeout) {
    this.engine = engine;
    this.idleTimeout = idleTimeout;
  }

  public VerificationResult verifyProcessInstanceCompleted(final long processInstanceKey) {
    engine.waitForIdleState(idleTimeout);

    final Intent processInstanceState =
        engine.getRecordStreamView().getRecords().stream()
            .filter(record -> record.getValueType() == ValueType.PROCESS_INSTANCE)
            .filter(
                record -> {
                  if (record.getValue() instanceof final ProcessInstanceRecordValue value) {
                    return value.getProcessInstanceKey() == processInstanceKey
                        && value.getBpmnElementType() == BpmnElementType.PROCESS;
                  } else {
                    return false;
                  }
                })
            .map(Record::getIntent)
            .toList()
            .getLast();

    if (processInstanceState == ProcessInstanceIntent.ELEMENT_COMPLETED) {
      return VerificationResult.success();
    } else {
      return new VerificationResult(
          false,
          "Process instance [key: %d] should be completed but was %s."
              .formatted(processInstanceKey, getStateName(processInstanceState)));
    }
  }

  private static String getStateName(final Intent state) {
    return switch (state) {
      case ProcessInstanceIntent.ELEMENT_ACTIVATED -> "active";
      case ProcessInstanceIntent.ELEMENT_COMPLETED -> "completed";
      case ProcessInstanceIntent.ELEMENT_TERMINATED -> "terminated";
      default -> state.name();
    };
  }
}
