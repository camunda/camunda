/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultCorrectionsValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "empty",
  "encodedLength",
  "length"
})
public final class JobResultCorrections extends UnpackedObject
    implements JobResultCorrectionsValue {

  private final StringProperty assigneeProp = new StringProperty("assignee", "");
  private final StringProperty dueDateProp = new StringProperty("dueDate", "");
  private final StringProperty followUpDateProp = new StringProperty("followUpDate", "");
  private final ArrayProperty<StringValue> candidateUsersListProp =
      new ArrayProperty<>("candidateUsersList", StringValue::new);
  private final ArrayProperty<StringValue> candidateGroupsListProp =
      new ArrayProperty<>("candidateGroupsList", StringValue::new);
  private final IntegerProperty priorityProp = new IntegerProperty("priority", -1);

  public JobResultCorrections() {
    super(6);
    declareProperty(assigneeProp)
        .declareProperty(dueDateProp)
        .declareProperty(followUpDateProp)
        .declareProperty(candidateUsersListProp)
        .declareProperty(candidateGroupsListProp)
        .declareProperty(priorityProp);
  }

  public void wrap(final JobResultCorrections other) {
    setAssignee(other.getAssignee());
    setDueDate(other.getDueDate());
    setFollowUpDate(other.getFollowUpDate());
    setCandidateUsersList(other.getCandidateUsersList());
    setCandidateGroupsList(other.getCandidateGroupsList());
    setPriority(other.getPriority());
  }

  @JsonIgnore
  public DirectBuffer getAssigneeBuffer() {
    return assigneeProp.getValue();
  }

  @Override
  public String getAssignee() {
    final var buffer = getAssigneeBuffer();
    return BufferUtil.bufferAsString(buffer);
  }

  public JobResultCorrections setAssignee(final String assignee) {
    assigneeProp.setValue(assignee);
    return this;
  }

  @Override
  public String getDueDate() {
    final var buffer = getDueDateBuffer();
    return BufferUtil.bufferAsString(buffer);
  }

  public JobResultCorrections setDueDate(final String dueDate) {
    dueDateProp.setValue(dueDate);
    return this;
  }

  @Override
  public String getFollowUpDate() {
    final var buffer = getFollowUpDateBuffer();
    return BufferUtil.bufferAsString(buffer);
  }

  public JobResultCorrections setFollowUpDate(final String followUpDate) {
    followUpDateProp.setValue(followUpDate);
    return this;
  }

  @Override
  public List<String> getCandidateGroupsList() {
    return StreamSupport.stream(candidateGroupsListProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public JobResultCorrections setCandidateGroupsList(final List<String> candidateGroups) {
    candidateGroupsListProp.reset();
    candidateGroups.forEach(
        candidateGroup ->
            candidateGroupsListProp.add().wrap(BufferUtil.wrapString(candidateGroup)));
    return this;
  }

  @Override
  public List<String> getCandidateUsersList() {
    return StreamSupport.stream(candidateUsersListProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public JobResultCorrections setCandidateUsersList(final List<String> candidateUsers) {
    candidateUsersListProp.reset();
    candidateUsers.forEach(
        candidateUser -> candidateUsersListProp.add().wrap(BufferUtil.wrapString(candidateUser)));
    return this;
  }

  @Override
  public int getPriority() {
    return priorityProp.getValue();
  }

  public JobResultCorrections setPriority(final int priority) {
    priorityProp.setValue(priority);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getDueDateBuffer() {
    return dueDateProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getFollowUpDateBuffer() {
    return followUpDateProp.getValue();
  }
}
