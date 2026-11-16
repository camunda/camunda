/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessInstanceRecord extends MsgpackBufferValue<ProcessInstanceRecord>
    implements MsgpackRecordValue, ProcessInstanceRecordValue {

  // Static StringValue keys for property names
  public static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");
  public static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  public static final StringValue ELEMENT_ID_KEY = new StringValue("elementId");
  public static final StringValue VERSION_KEY = new StringValue("version");
  public static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  public static final StringValue BPMN_ELEMENT_TYPE_KEY = new StringValue("bpmnElementType");
  public static final StringValue FLOW_SCOPE_KEY_KEY = new StringValue("flowScopeKey");
  public static final StringValue BPMN_EVENT_TYPE_KEY = new StringValue("bpmnEventType");
  public static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  public static final StringValue PARENT_PROCESS_INSTANCE_KEY_KEY =
      new StringValue("parentProcessInstanceKey");
  public static final StringValue PARENT_ELEMENT_INSTANCE_KEY_KEY =
      new StringValue("parentElementInstanceKey");
  public static final StringValue ELEMENT_INSTANCE_PATH_KEY =
      new StringValue("elementInstancePath");
  public static final StringValue PROCESS_DEFINITION_PATH_KEY =
      new StringValue("processDefinitionPath");
  public static final StringValue CALLING_ELEMENT_PATH_KEY = new StringValue("callingElementPath");
  public static final StringValue TAGS_KEY = new StringValue("tags");
  public static final StringValue ROOT_PROCESS_INSTANCE_KEY =
      new StringValue("rootProcessInstanceKey");
  public static final StringValue STORAGE_ORDINAL_KEY = new StringValue("storageOrdinal");
  public static final StringValue BUSINESS_ID_KEY = new StringValue("businessId");
  public static final StringValue RESUME_FROM_JOB_KEY_KEY = new StringValue("resumeFromJobKey");
  public static final StringValue RESUME_FROM_USER_TASK_KEY_KEY =
      new StringValue("resumeFromUserTaskKey");

  BpmnElementType bpmnElementType = BpmnElementType.UNSPECIFIED;
  DirectBuffer elementId = Decoding.EMPTY;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  int version = -1;
  long processDefinitionKey = -1L;
  long processInstanceKey = -1L;
  long flowScopeKey = -1L;
  BpmnEventType bpmnEventType = BpmnEventType.UNSPECIFIED;
  long parentProcessInstanceKey = -1L;
  long parentElementInstanceKey = -1L;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  final List<List<Long>> elementInstancePath = new ArrayList<>();
  final List<Long> processDefinitionPath = new ArrayList<>();
  final IntArrayList callingElementPath = new IntArrayList();
  final List<DirectBuffer> tags = new ArrayList<>();
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer businessId = Decoding.EMPTY;
  long resumeFromJobKey = -1L;
  long resumeFromUserTaskKey = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final ProcessInstanceRecord record) {
    elementId = record.getElementIdBuffer();
    bpmnProcessId = record.getBpmnProcessIdBuffer();
    flowScopeKey = record.getFlowScopeKey();
    version = record.getVersion();
    processDefinitionKey = record.getProcessDefinitionKey();
    processInstanceKey = record.getProcessInstanceKey();
    bpmnElementType = record.getBpmnElementType();
    bpmnEventType = record.getBpmnEventType();
    parentProcessInstanceKey = record.getParentProcessInstanceKey();
    parentElementInstanceKey = record.getParentElementInstanceKey();
    tenantId = record.tenantId;
    rootProcessInstanceKey = record.getRootProcessInstanceKey();
    storageOrdinal = record.getStorageOrdinal();
    businessId = record.businessId;
    resumeFromJobKey = record.getResumeFromJobKey();
    resumeFromUserTaskKey = record.getResumeFromUserTaskKey();
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementId;
  }

  public ProcessInstanceRecord setBpmnProcessId(
      final DirectBuffer directBuffer, final int offset, final int length) {
    bpmnProcessId = new UnsafeBuffer(directBuffer, offset, length);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementId);
  }

  @Override
  public long getFlowScopeKey() {
    return flowScopeKey;
  }

  @Override
  public BpmnElementType getBpmnElementType() {
    return bpmnElementType;
  }

  @Override
  public long getParentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  @Override
  public long getParentElementInstanceKey() {
    return parentElementInstanceKey;
  }

  public ProcessInstanceRecord setParentElementInstanceKey(final long parentElementInstanceKey) {
    this.parentElementInstanceKey = parentElementInstanceKey;
    return this;
  }

  @Override
  public BpmnEventType getBpmnEventType() {
    return bpmnEventType;
  }

  public ProcessInstanceRecord setBpmnEventType(final BpmnEventType bpmnEventType) {
    this.bpmnEventType = bpmnEventType;
    return this;
  }

  @Override
  public List<List<Long>> getElementInstancePath() {
    final var copy = new ArrayList<List<Long>>(elementInstancePath.size());
    for (final var pathEntry : elementInstancePath) {
      copy.add(new ArrayList<>(pathEntry));
    }
    return copy;
  }

  public ProcessInstanceRecord setElementInstancePath(final List<List<Long>> elementInstancePath) {
    this.elementInstancePath.clear();
    for (final var pathEntry : elementInstancePath) {
      this.elementInstancePath.add(new ArrayList<>(pathEntry));
    }
    return this;
  }

  @Override
  public List<Long> getProcessDefinitionPath() {
    return new ArrayList<>(processDefinitionPath);
  }

  public ProcessInstanceRecord setProcessDefinitionPath(final List<Long> processDefinitionPath) {
    this.processDefinitionPath.clear();
    this.processDefinitionPath.addAll(processDefinitionPath);
    return this;
  }

  @Override
  public List<Integer> getCallingElementPath() {
    final var path = new ArrayList<Integer>(callingElementPath.size());
    for (int i = 0; i < callingElementPath.size(); i++) {
      path.add(callingElementPath.getInt(i));
    }
    return path;
  }

  public ProcessInstanceRecord setCallingElementPath(final List<Integer> callingElementPath) {
    this.callingElementPath.clear();
    callingElementPath.forEach(this.callingElementPath::addInt);
    return this;
  }

  @Override
  public Set<String> getTags() {
    return tags.stream().map(BufferUtil::bufferAsString).collect(Collectors.toSet());
  }

  public ProcessInstanceRecord setTags(final Set<String> tags) {
    this.tags.clear();
    if (tags != null) {
      tags.forEach(tag -> this.tags.add(BufferUtil.wrapString(tag)));
    }
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public ProcessInstanceRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public ProcessInstanceRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public ProcessInstanceRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  @Override
  public long getResumeFromJobKey() {
    return resumeFromJobKey;
  }

  public ProcessInstanceRecord setResumeFromJobKey(final long resumeFromJobKey) {
    this.resumeFromJobKey = resumeFromJobKey;
    return this;
  }

  @Override
  public long getResumeFromUserTaskKey() {
    return resumeFromUserTaskKey;
  }

  public ProcessInstanceRecord setResumeFromUserTaskKey(final long resumeFromUserTaskKey) {
    this.resumeFromUserTaskKey = resumeFromUserTaskKey;
    return this;
  }

  public ProcessInstanceRecord setParentProcessInstanceKey(final long parentProcessInstanceKey) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    return this;
  }

  public ProcessInstanceRecord setBpmnElementType(final BpmnElementType bpmnType) {
    bpmnElementType = bpmnType;
    return this;
  }

  public ProcessInstanceRecord setFlowScopeKey(final long flowScopeKey) {
    this.flowScopeKey = flowScopeKey;
    return this;
  }

  public ProcessInstanceRecord setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  public ProcessInstanceRecord setElementId(final DirectBuffer elementId) {
    return setElementId(elementId, 0, elementId.capacity());
  }

  public ProcessInstanceRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public ProcessInstanceRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public ProcessInstanceRecord setVersion(final int version) {
    this.version = version;
    return this;
  }

  public ProcessInstanceRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  public ProcessInstanceRecord setBpmnProcessId(final DirectBuffer directBuffer) {
    bpmnProcessId = directBuffer;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ProcessInstanceRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public ProcessInstanceRecord resetElementInstancePath() {
    elementInstancePath.clear();
    return this;
  }

  public ProcessInstanceRecord resetCallingElementPath() {
    callingElementPath.clear();
    return this;
  }

  public ProcessInstanceRecord resetProcessDefinitionPath() {
    processDefinitionPath.clear();
    return this;
  }

  public boolean hasParentProcessInstance() {
    return getParentProcessInstanceKey() != -1L;
  }

  public ProcessInstanceRecord setElementId(
      final DirectBuffer elementId, final int offset, final int length) {
    this.elementId = new UnsafeBuffer(elementId, offset, length);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ProcessInstanceRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessInstanceRecord> codec() {
    return ProcessInstanceRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceRecord> ops() {
    return ProcessInstanceRecordOps.INSTANCE;
  }
}
