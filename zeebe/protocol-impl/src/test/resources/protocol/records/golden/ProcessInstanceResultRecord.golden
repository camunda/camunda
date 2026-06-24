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
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;

public final class ProcessInstanceResultRecord extends UnifiedRecordValue
    implements ProcessInstanceResultRecordValue {

  // Static StringValue keys for property names
  public static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");
  public static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  public static final StringValue VERSION_KEY = new StringValue("version");
  public static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  public static final StringValue VARIABLES_KEY = new StringValue("variables");
  public static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  public static final StringValue TAGS_KEY = new StringValue("tags");
  public static final StringValue BUSINESS_ID_KEY = new StringValue("businessId");

  private final StringProperty bpmnProcessIdProperty = new StringProperty(BPMN_PROCESS_ID_KEY, "");
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1);
  private final IntegerProperty versionProperty = new IntegerProperty(VERSION_KEY, -1);
  private final StringProperty tenantIdProperty =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final DocumentProperty variablesProperty = new DocumentProperty(VARIABLES_KEY);
  private final LongProperty processInstanceKeyProperty =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1);
  private final ArrayProperty<StringValue> tagsProperty =
      new ArrayProperty<>(TAGS_KEY, StringValue::new);
  private final StringProperty businessIdProperty = new StringProperty(BUSINESS_ID_KEY, "");

  public ProcessInstanceResultRecord() {
    super(8);
    declareProperty(bpmnProcessIdProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(processInstanceKeyProperty)
        .declareProperty(versionProperty)
        .declareProperty(tenantIdProperty)
        .declareProperty(variablesProperty)
        .declareProperty(tagsProperty)
        .declareProperty(businessIdProperty);
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProperty.getValue());
  }

  public ProcessInstanceResultRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  public ProcessInstanceResultRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public int getVersion() {
    return versionProperty.getValue();
  }

  public ProcessInstanceResultRecord setVersion(final int version) {
    versionProperty.setValue(version);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public ProcessInstanceResultRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProperty.setValue(key);
    return this;
  }

  @Override
  public Set<String> getTags() {
    return StreamSupport.stream(tagsProperty.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toSet());
  }

  public ProcessInstanceResultRecord setTags(final Set<String> tags) {
    tagsProperty.reset();
    if (tags != null) {
      tags.forEach(tag -> tagsProperty.add().wrap(BufferUtil.wrapString(tag)));
    }
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessIdProperty.getValue());
  }

  public ProcessInstanceResultRecord setBusinessId(final String businessId) {
    businessIdProperty.setValue(businessId);
    return this;
  }

  public ProcessInstanceResultRecord setBusinessId(final DirectBuffer businessId) {
    businessIdProperty.setValue(businessId);
    return this;
  }

  public ProcessInstanceResultRecord setProcessInstanceKey(final long instanceKey) {
    processInstanceKeyProperty.setValue(instanceKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessIdProperty.getValue();
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProperty.getValue();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public ProcessInstanceResultRecord setVariables(final DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProperty.getValue());
  }

  public ProcessInstanceResultRecord setTenantId(final String tenantId) {
    tenantIdProperty.setValue(tenantId);
    return this;
  }
}
