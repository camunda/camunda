/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.cases;

import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.BPMN_PROCESS_ID_KEY;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.BUSINESS_ID_KEY;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROCESS_DEFINITION_KEY_KEY;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROCESS_INSTANCE_KEY_KEY;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.STORAGE_ORDINAL_KEY;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.TENANT_ID_KEY;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseLifecycleState;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseStageState;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CaseRecord extends UnifiedRecordValue implements CaseRecordValue {
  private final LongProperty caseKeyProp = new LongProperty("caseKey", -1L);
  private final EnumProperty<CaseLifecycleState> stateProp =
      new EnumProperty<>("state", CaseLifecycleState.class, CaseLifecycleState.ACTIVE);
  private final LongProperty processInstanceKeyProp = new LongProperty(PROCESS_INSTANCE_KEY_KEY);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY);
  private final StringProperty bpmnProcessIdProp = new StringProperty(BPMN_PROCESS_ID_KEY);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final StringProperty businessIdProp = new StringProperty(BUSINESS_ID_KEY, "");
  private final StringProperty titleProp = new StringProperty("title", "");
  private final ArrayProperty<CaseStageRecord> stagesProp =
      new ArrayProperty<>("stages", CaseStageRecord::new);
  private final IntegerProperty storageOrdinalProp = new IntegerProperty(STORAGE_ORDINAL_KEY, 0);

  public CaseRecord() {
    super(10);
    declareProperty(caseKeyProp)
        .declareProperty(stateProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(tenantIdProp)
        .declareProperty(businessIdProp)
        .declareProperty(titleProp)
        .declareProperty(stagesProp)
        .declareProperty(storageOrdinalProp);
  }

  @Override
  public long getCaseKey() {
    return caseKeyProp.getValue();
  }

  public CaseRecord setCaseKey(final long caseKey) {
    caseKeyProp.setValue(caseKey);
    return this;
  }

  @Override
  public CaseLifecycleState getState() {
    return Objects.requireNonNull(stateProp.getValue());
  }

  public CaseRecord setState(final CaseLifecycleState state) {
    stateProp.setValue(state);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public CaseRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public CaseRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public CaseRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public CaseRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public String getBusinessId() {
    return BufferUtil.bufferAsString(businessIdProp.getValue());
  }

  public CaseRecord setBusinessId(final String businessId) {
    businessIdProp.setValue(businessId);
    return this;
  }

  @Override
  public String getTitle() {
    return BufferUtil.bufferAsString(titleProp.getValue());
  }

  public CaseRecord setTitle(final String title) {
    titleProp.setValue(title);
    return this;
  }

  @Override
  public List<CaseStageValue> getStages() {
    return stagesProp.stream().map(CaseStageValue.class::cast).toList();
  }

  public CaseRecord addStage(
      final String elementId, final String name, final CaseStageState state) {
    stagesProp.add().setElementId(elementId).setName(name).setState(state);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinalProp.getValue();
  }

  public CaseRecord setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProp.setValue(storageOrdinal);
    return this;
  }
}
