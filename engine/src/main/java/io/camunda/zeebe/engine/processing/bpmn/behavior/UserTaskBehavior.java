/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior.JobProperties;
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

  public long createUserTask(final BpmnElementContext context, final JobProperties jobProperties) {

    final long userTaskKey = keyGenerator.nextKey();
    final UserTaskRecord userTaskRecord = createUserTaskRecordForContext(context);

    final String assignee = jobProperties.getAssignee();
    if (assignee != null) {
      userTaskRecord.setAssignee(assignee);
    }

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);

    return userTaskKey;
  }

  public void userTaskCreated(final BpmnElementContext context, final long userTaskKey) {

    final UserTaskRecord userTaskRecord = createUserTaskRecordForContext(context);
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);
  }

  private static UserTaskRecord createUserTaskRecordForContext(final BpmnElementContext context) {
    return new UserTaskRecord()
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setProcessDefinitionVersion(context.getProcessVersion())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setElementId(context.getElementId())
        .setTenantId(context.getTenantId());
  }
}
