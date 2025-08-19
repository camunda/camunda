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
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ArrayValue;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;

public final class ProcessInstanceRecord extends UnifiedRecordValue
    implements ProcessInstanceRecordValue {

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

  private final StringProperty bpmnProcessIdProp = new StringProperty(BPMN_PROCESS_ID_KEY, "");
  private final IntegerProperty versionProp = new IntegerProperty(VERSION_KEY, -1);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1L);

  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1L);
  private final StringProperty elementIdProp = new StringProperty(ELEMENT_ID_KEY, "");

  private final LongProperty flowScopeKeyProp = new LongProperty(FLOW_SCOPE_KEY_KEY, -1L);

  private final EnumProperty<BpmnElementType> bpmnElementTypeProp =
      new EnumProperty<>(BPMN_ELEMENT_TYPE_KEY, BpmnElementType.class, BpmnElementType.UNSPECIFIED);

  private final EnumProperty<BpmnEventType> bpmnEventTypeProp =
      new EnumProperty<>(BPMN_EVENT_TYPE_KEY, BpmnEventType.class, BpmnEventType.UNSPECIFIED);

  private final LongProperty parentProcessInstanceKeyProp =
      new LongProperty(PARENT_PROCESS_INSTANCE_KEY_KEY, -1L);
  private final LongProperty parentElementInstanceKeyProp =
      new LongProperty(PARENT_ELEMENT_INSTANCE_KEY_KEY, -1L);

  private final ArrayProperty<ArrayValue<LongValue>> elementInstancePathProp =
      new ArrayProperty<>(ELEMENT_INSTANCE_PATH_KEY, () -> new ArrayValue<>(LongValue::new));
  private final ArrayProperty<LongValue> processDefinitionPathProp =
      new ArrayProperty<>(PROCESS_DEFINITION_PATH_KEY, LongValue::new);
  private final ArrayProperty<IntegerValue> callingElementPathProp =
      new ArrayProperty<>(CALLING_ELEMENT_PATH_KEY, IntegerValue::new);

  private final ArrayProperty<StringValue> tagsProp =
      new ArrayProperty<>(TAGS_KEY, StringValue::new);

  public ProcessInstanceRecord() {
    super(15);
    declareProperty(bpmnElementTypeProp)
        .declareProperty(elementIdProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(versionProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(flowScopeKeyProp)
        .declareProperty(bpmnEventTypeProp)
        .declareProperty(parentProcessInstanceKeyProp)
        .declareProperty(parentElementInstanceKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(elementInstancePathProp)
        .declareProperty(processDefinitionPathProp)
        .declareProperty(callingElementPathProp)
        .declareProperty(tagsProp);
  }

  public void wrap(final ProcessInstanceRecord record) {
    elementIdProp.setValue(record.getElementIdBuffer());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    flowScopeKeyProp.setValue(record.getFlowScopeKey());
    versionProp.setValue(record.getVersion());
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    processInstanceKeyProp.setValue(record.getProcessInstanceKey());
    bpmnElementTypeProp.setValue(record.getBpmnElementType());
    bpmnEventTypeProp.setValue(record.getBpmnEventType());
    parentProcessInstanceKeyProp.setValue(record.getParentProcessInstanceKey());
    parentElementInstanceKeyProp.setValue(record.getParentElementInstanceKey());
    tenantIdProp.setValue(record.getTenantId());

    // TODO check the impact of this, does this inherit tags into subprocesses?
    //    setTags(record.getTags());
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  public ProcessInstanceRecord setBpmnProcessId(
      final DirectBuffer directBuffer, final int offset, final int length) {
    bpmnProcessIdProp.setValue(directBuffer, offset, length);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public ProcessInstanceRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementIdProp.getValue());
  }

  @Override
  public long getFlowScopeKey() {
    return flowScopeKeyProp.getValue();
  }

  @Override
  public BpmnElementType getBpmnElementType() {
    return bpmnElementTypeProp.getValue();
  }

  @Override
  public long getParentProcessInstanceKey() {
    return parentProcessInstanceKeyProp.getValue();
  }

  @Override
  public long getParentElementInstanceKey() {
    return parentElementInstanceKeyProp.getValue();
  }

  public ProcessInstanceRecord setParentElementInstanceKey(final long parentElementInstanceKey) {
    parentElementInstanceKeyProp.setValue(parentElementInstanceKey);
    return this;
  }

  @Override
  public BpmnEventType getBpmnEventType() {
    return bpmnEventTypeProp.getValue();
  }

  public ProcessInstanceRecord setBpmnEventType(final BpmnEventType bpmnEventType) {
    bpmnEventTypeProp.setValue(bpmnEventType);
    return this;
  }

  @Override
  public List<List<Long>> getElementInstancePath() {
    final var elementInstancePath = new ArrayList<List<Long>>();
    elementInstancePathProp.forEach(
        pe -> {
          final var pathEntry = new ArrayList<Long>();
          pe.forEach(e -> pathEntry.add(e.getValue()));
          elementInstancePath.add(pathEntry);
        });
    return elementInstancePath;
  }

  public ProcessInstanceRecord setElementInstancePath(final List<List<Long>> elementInstancePath) {
    elementInstancePathProp.reset();
    elementInstancePath.forEach(
        pathEntry -> {
          final var entry = elementInstancePathProp.add();
          pathEntry.forEach(element -> entry.add().setValue(element));
        });
    return this;
  }

  @Override
  public List<Long> getProcessDefinitionPath() {
    final var processDefinitionPath = new ArrayList<Long>();
    processDefinitionPathProp.forEach(e -> processDefinitionPath.add(e.getValue()));
    return processDefinitionPath;
  }

  public ProcessInstanceRecord setProcessDefinitionPath(final List<Long> processDefinitionPath) {
    processDefinitionPathProp.reset();
    processDefinitionPath.forEach(e -> processDefinitionPathProp.add().setValue(e));
    return this;
  }

  @Override
  public List<Integer> getCallingElementPath() {
    final var callingElementPath = new ArrayList<Integer>();
    callingElementPathProp.forEach(e -> callingElementPath.add(e.getValue()));
    return callingElementPath;
  }

  public ProcessInstanceRecord setCallingElementPath(final List<Integer> callingElementPath) {
    callingElementPathProp.reset();
    callingElementPath.forEach(e -> callingElementPathProp.add().setValue(e));
    return this;
  }

  @Override
  public Set<String> getTags() {
    final var tags = new HashSet<String>();
    tagsProp.forEach(e -> tags.add(e.toString()));
    return tags;
  }

  public ProcessInstanceRecord setTags(final Set<String> tags) {
    tagsProp.reset();
    tags.forEach(e -> tagsProp.add().wrap(e));
    return this;
  }

  public ProcessInstanceRecord setParentProcessInstanceKey(final long parentProcessInstanceKey) {
    parentProcessInstanceKeyProp.setValue(parentProcessInstanceKey);
    return this;
  }

  public ProcessInstanceRecord setBpmnElementType(final BpmnElementType bpmnType) {
    bpmnElementTypeProp.setValue(bpmnType);
    return this;
  }

  public ProcessInstanceRecord setFlowScopeKey(final long flowScopeKey) {
    flowScopeKeyProp.setValue(flowScopeKey);
    return this;
  }

  public ProcessInstanceRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public ProcessInstanceRecord setElementId(final DirectBuffer elementId) {
    return setElementId(elementId, 0, elementId.capacity());
  }

  public ProcessInstanceRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  public ProcessInstanceRecord setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  public ProcessInstanceRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public ProcessInstanceRecord setBpmnProcessId(final DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer);
    return this;
  }

  public ProcessInstanceRecord resetElementInstancePath() {
    elementInstancePathProp.reset();
    return this;
  }

  public ProcessInstanceRecord resetCallingElementPath() {
    callingElementPathProp.reset();
    return this;
  }

  public ProcessInstanceRecord resetProcessDefinitionPath() {
    processDefinitionPathProp.reset();
    return this;
  }

  public boolean hasParentProcess() {
    return getParentProcessInstanceKey() != -1L;
  }

  public ProcessInstanceRecord setElementId(
      final DirectBuffer elementId, final int offset, final int length) {
    elementIdProp.setValue(elementId, offset, length);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public ProcessInstanceRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
