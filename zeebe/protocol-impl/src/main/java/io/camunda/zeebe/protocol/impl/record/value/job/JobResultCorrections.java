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
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultCorrectionsValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({
  /* encodedLength and empty were inherited from ObjectValue, length is inherited from
  MsgpackBufferValue; they have no purpose in exported JSON records*/
  "empty",
  "encodedLength",
  "length"
})
@MsgpackPojo
@NullMarked
public final class JobResultCorrections extends MsgpackBufferValue<JobResultCorrections>
    implements JobResultCorrectionsValue {

  DirectBuffer assignee = Decoding.EMPTY;
  DirectBuffer dueDate = Decoding.EMPTY;
  DirectBuffer followUpDate = Decoding.EMPTY;
  final List<DirectBuffer> candidateUsersList = new ArrayList<>();
  final List<DirectBuffer> candidateGroupsList = new ArrayList<>();
  int priority = -1;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

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
    return assignee;
  }

  @Override
  public String getAssignee() {
    return BufferUtil.bufferAsString(assignee);
  }

  public JobResultCorrections setAssignee(final String assignee) {
    this.assignee = BufferUtil.wrapString(assignee);
    return this;
  }

  @Override
  public String getDueDate() {
    return BufferUtil.bufferAsString(dueDate);
  }

  public JobResultCorrections setDueDate(final String dueDate) {
    this.dueDate = BufferUtil.wrapString(dueDate);
    return this;
  }

  @Override
  public String getFollowUpDate() {
    return BufferUtil.bufferAsString(followUpDate);
  }

  public JobResultCorrections setFollowUpDate(final String followUpDate) {
    this.followUpDate = BufferUtil.wrapString(followUpDate);
    return this;
  }

  @Override
  public List<String> getCandidateGroupsList() {
    return candidateGroupsList.stream().map(BufferUtil::bufferAsString).toList();
  }

  public JobResultCorrections setCandidateGroupsList(final List<String> candidateGroups) {
    candidateGroupsList.clear();
    candidateGroups.forEach(
        candidateGroup -> candidateGroupsList.add(BufferUtil.wrapString(candidateGroup)));
    return this;
  }

  @Override
  public List<String> getCandidateUsersList() {
    return candidateUsersList.stream().map(BufferUtil::bufferAsString).toList();
  }

  public JobResultCorrections setCandidateUsersList(final List<String> candidateUsers) {
    candidateUsersList.clear();
    candidateUsers.forEach(
        candidateUser -> candidateUsersList.add(BufferUtil.wrapString(candidateUser)));
    return this;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  public JobResultCorrections setPriority(final int priority) {
    this.priority = priority;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getDueDateBuffer() {
    return dueDate;
  }

  @JsonIgnore
  public DirectBuffer getFollowUpDateBuffer() {
    return followUpDate;
  }

  @Override
  protected MsgpackCodec<JobResultCorrections> codec() {
    return JobResultCorrectionsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<JobResultCorrections> ops() {
    return JobResultCorrectionsOps.INSTANCE;
  }
}
