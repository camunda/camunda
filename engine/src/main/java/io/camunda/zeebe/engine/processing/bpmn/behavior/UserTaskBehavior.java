/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class UserTaskBehavior {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  public UserTaskBehavior(final KeyGenerator keyGenerator, final StateWriter stateWriter) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
  }

  public void createUserTask(
      final BpmnElementContext context, final ExecutableJobWorkerTask element) {
    final long userTaskKey = keyGenerator.nextKey();

    final UserTaskRecord userTaskRecord = new UserTaskRecord();
    userTaskRecord
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setProcessDefinitionVersion(context.getProcessVersion())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setElementId(context.getElementId())
        .setTenantId(context.getTenantId());

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    // cheating :D
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);
  }
}
