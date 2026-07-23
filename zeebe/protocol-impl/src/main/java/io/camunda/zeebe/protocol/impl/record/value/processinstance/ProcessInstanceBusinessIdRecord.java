/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBusinessIdRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public final class ProcessInstanceBusinessIdRecord extends UnifiedRecordValue
    implements ProcessInstanceBusinessIdRecordValue {

  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  // Static StringValue keys to avoid memory waste
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue BUSINESS_ID_KEY = new StringValue("businessId");
  private static final StringValue ROOT_PROCESS_INSTANCE_KEY_KEY =
      new StringValue("rootProcessInstanceKey");
  private static final StringValue STORAGE_ORDINAL_KEY_KEY = new StringValue("storageOrdinalKey");
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");

  private final LongProperty processInstanceKeyProperty =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1);
  private final StringProperty businessIdProperty = new StringProperty(BUSINESS_ID_KEY, "");
  private final StringProperty tenantIdProperty =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty rootProcessInstanceKeyProperty =
      new LongProperty(ROOT_PROCESS_INSTANCE_KEY_KEY, -1);
  private final IntegerProperty storageOrdinalKeyProperty =
      new IntegerProperty(STORAGE_ORDINAL_KEY_KEY, 0);
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1L);
  private final StringProperty bpmnProcessIdProperty = new StringProperty(BPMN_PROCESS_ID_KEY, "");

  public ProcessInstanceBusinessIdRecord() {
    super(7);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(businessIdProperty)
        .declareProperty(tenantIdProperty)
        .declareProperty(rootProcessInstanceKeyProperty)
        .declareProperty(storageOrdinalKeyProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(bpmnProcessIdProperty);
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessIdProperty.getValue());
  }

  public ProcessInstanceBusinessIdRecord setBusinessId(final String businessId) {
    businessIdProperty.setValue(businessId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessInstanceBusinessIdRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public ProcessInstanceBusinessIdRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProperty.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKeyProperty.getValue();
  }

  public ProcessInstanceBusinessIdRecord setRootProcessInstanceKey(
      final long rootProcessInstanceKey) {
    rootProcessInstanceKeyProperty.setValue(rootProcessInstanceKey);
    return this;
  }

  @Override
  public int getStorageOrdinalKey() {
    return storageOrdinalKeyProperty.getValue();
  }

  public ProcessInstanceBusinessIdRecord setStorageOrdinalKey(final int storageOrdinalKey) {
    storageOrdinalKeyProperty.setValue(storageOrdinalKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProperty.getValue());
  }

  public ProcessInstanceBusinessIdRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProperty.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProperty.getValue());
  }

  public ProcessInstanceBusinessIdRecord setTenantId(final String tenantId) {
    tenantIdProperty.setValue(tenantId);
    return this;
  }
}
