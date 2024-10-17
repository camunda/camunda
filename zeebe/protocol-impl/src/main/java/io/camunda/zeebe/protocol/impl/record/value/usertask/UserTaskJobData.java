/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class UserTaskJobData extends UnpackedObject {

  private final LongProperty userTaskKeyProp = new LongProperty("userTaskKey", -1);
  private final StringProperty assigneeProp = new StringProperty("assignee", "");
  private final ArrayProperty<StringValue> candidateGroupsListProp =
      new ArrayProperty<>("candidateGroupsList", StringValue::new);
  private final ArrayProperty<StringValue> candidateUsersListProp =
      new ArrayProperty<>("candidateUsersList", StringValue::new);
  private final StringProperty dueDateProp = new StringProperty("dueDate", "");
  private final StringProperty followUpDateProp = new StringProperty("followUpDate", "");
  private final LongProperty formKeyProp = new LongProperty("formKey", -1);
  private final IntegerProperty priorityProp = new IntegerProperty("priority", 50);

  public UserTaskJobData() {
    super(9);
    declareProperty(userTaskKeyProp);
    declareProperty(assigneeProp);
    declareProperty(candidateGroupsListProp);
    declareProperty(candidateUsersListProp);
    declareProperty(dueDateProp);
    declareProperty(followUpDateProp);
    declareProperty(formKeyProp);
    declareProperty(priorityProp);
  }

  /** Sets all properties to current instance from provided user task job data */
  public void setProperties(final UserTaskJobData userTaskJobData) {
    userTaskKeyProp.setValue(userTaskJobData.getUserTaskKey());
    assigneeProp.setValue(userTaskJobData.getAssignee());
    setCandidateGroupsList(userTaskJobData.getCandidateGroupsList());
    setCandidateUsersList(userTaskJobData.getCandidateUsersList());
    dueDateProp.setValue(userTaskJobData.getDueDate());
    followUpDateProp.setValue(userTaskJobData.getFollowUpDate());
    formKeyProp.setValue(userTaskJobData.getFormKey());
    priorityProp.setValue(userTaskJobData.getPriority());
  }

  public String getAssignee() {
    return bufferAsString(assigneeProp.getValue());
  }

  public UserTaskJobData setAssignee(final String assignee) {
    assigneeProp.setValue(assignee);
    return this;
  }

  public long getUserTaskKey() {
    return userTaskKeyProp.getValue();
  }

  public UserTaskJobData setUserTaskKey(final long userTaskKey) {
    userTaskKeyProp.setValue(userTaskKey);
    return this;
  }

  public List<String> getCandidateGroupsList() {
    return StreamSupport.stream(candidateGroupsListProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  public UserTaskJobData setCandidateGroupsList(final List<String> candidateGroups) {
    candidateGroupsListProp.reset();
    candidateGroups.forEach(
        candidateGroup ->
            candidateGroupsListProp.add().wrap(BufferUtil.wrapString(candidateGroup)));
    return this;
  }

  public List<String> getCandidateUsersList() {
    return StreamSupport.stream(candidateUsersListProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  public UserTaskJobData setCandidateUsersList(final List<String> candidateUsers) {
    candidateUsersListProp.reset();
    candidateUsers.forEach(
        candidateUser -> candidateUsersListProp.add().wrap(BufferUtil.wrapString(candidateUser)));
    return this;
  }

  public String getDueDate() {
    return bufferAsString(dueDateProp.getValue());
  }

  public UserTaskJobData setDueDate(final String dueDate) {
    dueDateProp.setValue(dueDate);
    return this;
  }

  public String getFollowUpDate() {
    return bufferAsString(followUpDateProp.getValue());
  }

  public UserTaskJobData setFollowUpDate(final String followUpDate) {
    followUpDateProp.setValue(followUpDate);
    return this;
  }

  public long getFormKey() {
    return formKeyProp.getValue();
  }

  public UserTaskJobData setFormKey(final long formKey) {
    formKeyProp.setValue(formKey);
    return this;
  }

  public int getPriority() {
    return priorityProp.getValue();
  }

  public UserTaskJobData setPriority(final int priority) {
    priorityProp.setValue(priority);
    return this;
  }
}
