/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandProcessor;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class UserTaskProcessor implements TypedRecordProcessor<UserTaskRecord> {

  private final UserTaskCommandProcessors commandProcessors;

  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public UserTaskProcessor(
      final ProcessingState state,
      final KeyGenerator keyGenerator,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.authCheckBehavior = authCheckBehavior;
    commandProcessors =
        new UserTaskCommandProcessors(
            state, keyGenerator, bpmnBehaviors, writers, authCheckBehavior);

    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> command) {
    final UserTaskIntent intent = (UserTaskIntent) command.getIntent();

    final var commandProcessor = commandProcessors.getCommandProcessor(intent);
    commandProcessor
        .validateCommand(command)
        .ifRightOrLeft(
            persistedRecord -> processRecord(commandProcessor, command, persistedRecord),
            violation -> {
              rejectionWriter.appendRejection(command, violation.getLeft(), violation.getRight());
              responseWriter.writeRejectionOnCommand(
                  command, violation.getLeft(), violation.getRight());
            });
  }

  private void processRecord(
      final UserTaskCommandProcessor processor,
      final TypedRecord<UserTaskRecord> command,
      final UserTaskRecord persistedRecord) {

    processor.onCommand(command, persistedRecord);
    processor.onFinalizeCommand(command, persistedRecord);
  }
}
