/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.Map;
import org.agrona.DirectBuffer;

public class UserTaskProperties extends ObjectValue {
  private final StringProperty assigneeProp = new StringProperty("assignee", "");
  private final StringProperty candidateGroupsProp = new StringProperty("candidateGroups", "");
  private final LongProperty userTaskKeyProp = new LongProperty("userTaskKey", -1L);

  private final DocumentProperty variableProp = new DocumentProperty("taskVariables");

  public UserTaskProperties() {
    declareProperty(assigneeProp)
        .declareProperty(candidateGroupsProp)
        .declareProperty(userTaskKeyProp)
        .declareProperty(variableProp);
  }

  public void wrap(final UserTaskProperties properties) {
    setAssignee(properties.getAssignee())
        .setCandidateGroups(properties.getCandidateGroups())
        .setUserTaskKey(properties.getUserTaskKey())
        .setVariables(properties.getVariablesBuffer());
  }

  public String getAssignee() {
    return bufferAsString(assigneeProp.getValue());
  }

  public UserTaskProperties setAssignee(final String assignee) {
    assigneeProp.setValue(assignee);
    return this;
  }

  public String getCandidateGroups() {
    return bufferAsString(candidateGroupsProp.getValue());
  }

  public UserTaskProperties setCandidateGroups(final String candidateGroups) {
    candidateGroupsProp.setValue(candidateGroups);
    return this;
  }

  public Long getUserTaskKey() {
    return userTaskKeyProp.getValue();
  }

  public UserTaskProperties setUserTaskKey(final Long userTaskKey) {
    userTaskKeyProp.setValue(userTaskKey);
    return this;
  }

  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variableProp.getValue());
  }

  public UserTaskProperties setVariables(final DirectBuffer variables) {
    variableProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variableProp.getValue();
  }
}
