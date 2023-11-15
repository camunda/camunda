/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_BPMN_PROCESS_ID;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_INSTANCE_KEY;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class UserTaskRecord extends UnifiedRecordValue implements UserTaskRecordValue {

  private static final String EMPTY_STRING = "";

  private final LongProperty userTaskKeyProp = new LongProperty("userTaskKey", -1);
  private final StringProperty assigneeProp = new StringProperty("assignee", EMPTY_STRING);
  private final StringProperty candidateGroupsProp =
      new StringProperty("candidateGroups", EMPTY_STRING);
  private final StringProperty candidateUsersProp =
      new StringProperty("candidateUsers", EMPTY_STRING);
  private final StringProperty dueDateProp = new StringProperty("dueDate", EMPTY_STRING);
  private final StringProperty followUpDateProp = new StringProperty("followUpDate", EMPTY_STRING);
  private final LongProperty formKeyProp = new LongProperty("formKey", -1);

  private final DocumentProperty variableProp = new DocumentProperty("variables");

  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROP_PROCESS_INSTANCE_KEY, -1L);
  private final StringProperty bpmnProcessIdProp =
      new StringProperty(PROP_PROCESS_BPMN_PROCESS_ID, EMPTY_STRING);
  private final IntegerProperty processDefinitionVersionProp =
      new IntegerProperty("processDefinitionVersion", -1);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty elementIdProp = new StringProperty("elementId", EMPTY_STRING);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public UserTaskRecord() {
    declareProperty(userTaskKeyProp)
        .declareProperty(assigneeProp)
        .declareProperty(candidateGroupsProp)
        .declareProperty(candidateUsersProp)
        .declareProperty(dueDateProp)
        .declareProperty(followUpDateProp)
        .declareProperty(formKeyProp)
        .declareProperty(variableProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionVersionProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(tenantIdProp);
  }

  public void wrapWithoutVariables(final UserTaskRecord record) {
    userTaskKeyProp.setValue(record.getUserTaskKey());
    assigneeProp.setValue(record.getAssigneeBuffer());
    candidateGroupsProp.setValue(record.getCandidateGroupsBuffer());
    candidateUsersProp.setValue(record.getCandidateUsersBuffer());
    dueDateProp.setValue(record.getDueDateBuffer());
    followUpDateProp.setValue(record.getFollowUpDateBuffer());
    formKeyProp.setValue(record.getFormKey());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    processDefinitionVersionProp.setValue(record.getProcessDefinitionVersion());
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    processInstanceKeyProp.setValue(record.getProcessInstanceKey());
    elementIdProp.setValue(record.getElementIdBuffer());
    elementInstanceKeyProp.setValue(record.getElementInstanceKey());
    tenantIdProp.setValue(record.getTenantIdBuffer());
  }

  public void wrap(final UserTaskRecord record) {
    wrapWithoutVariables(record);
    variableProp.setValue(record.getVariablesBuffer());
  }

  public UserTaskRecord resetVariables() {
    variableProp.reset();
    return this;
  }

  @Override
  public long getUserTaskKey() {
    return userTaskKeyProp.getValue();
  }

  @Override
  public String getAssignee() {
    return bufferAsString(assigneeProp.getValue());
  }

  @Override
  public String getCandidateGroups() {
    return bufferAsString(candidateGroupsProp.getValue());
  }

  @Override
  public String getCandidateUsers() {
    return bufferAsString(candidateUsersProp.getValue());
  }

  @Override
  public String getDueDate() {
    return bufferAsString(dueDateProp.getValue());
  }

  @Override
  public String getFollowUpDate() {
    return bufferAsString(followUpDateProp.getValue());
  }

  @Override
  public long getFormKey() {
    return formKeyProp.getValue();
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementIdProp.getValue());
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersionProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public UserTaskRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  public UserTaskRecord setProcessDefinitionVersion(final int version) {
    processDefinitionVersionProp.setValue(version);
    return this;
  }

  public UserTaskRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public UserTaskRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public UserTaskRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  public UserTaskRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public UserTaskRecord setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public UserTaskRecord setFormKey(final long formKey) {
    formKeyProp.setValue(formKey);
    return this;
  }

  public UserTaskRecord setFollowUpDate(final String followUpDate) {
    followUpDateProp.setValue(followUpDate);
    return this;
  }

  public UserTaskRecord setFollowUpDate(final DirectBuffer followUpDate) {
    followUpDateProp.setValue(followUpDate);
    return this;
  }

  public UserTaskRecord setDueDate(final String dueDate) {
    dueDateProp.setValue(dueDate);
    return this;
  }

  public UserTaskRecord setDueDate(final DirectBuffer dueDate) {
    dueDateProp.setValue(dueDate);
    return this;
  }

  public UserTaskRecord setCandidateUsers(final String candidateUsers) {
    candidateUsersProp.setValue(candidateUsers);
    return this;
  }

  public UserTaskRecord setCandidateUsers(final DirectBuffer candidateUsers) {
    candidateUsersProp.setValue(candidateUsers);
    return this;
  }

  public UserTaskRecord setCandidateGroups(final String candidateGroups) {
    candidateGroupsProp.setValue(candidateGroups);
    return this;
  }

  public UserTaskRecord setCandidateGroups(final DirectBuffer candidateGroups) {
    candidateGroupsProp.setValue(candidateGroups);
    return this;
  }

  public UserTaskRecord setAssignee(final String assignee) {
    assigneeProp.setValue(assignee);
    return this;
  }

  public UserTaskRecord setAssignee(final DirectBuffer assignee) {
    assigneeProp.setValue(assignee);
    return this;
  }

  public UserTaskRecord setUserTaskKey(final long userTaskKey) {
    userTaskKeyProp.setValue(userTaskKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public UserTaskRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variableProp.getValue());
  }

  public UserTaskRecord setVariables(final DirectBuffer variables) {
    variableProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getAssigneeBuffer() {
    return assigneeProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getCandidateGroupsBuffer() {
    return candidateGroupsProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getCandidateUsersBuffer() {
    return candidateUsersProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDueDateBuffer() {
    return dueDateProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getFollowUpDateBuffer() {
    return followUpDateProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variableProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public UserTaskRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }
}
