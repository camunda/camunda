/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.cases;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CaseRecord extends UnifiedRecordValue implements CaseRecordValue {
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey");
  private final LongProperty processDefinitionKeyProp = new LongProperty("processDefinitionKey");
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final StringProperty businessIdProp = new StringProperty("businessId", "");
  private final StringProperty titleProp = new StringProperty("title", "");
  private final ArrayProperty<CaseStageRecord> stagesProp =
      new ArrayProperty<>("stages", CaseStageRecord::new);
  private final IntegerProperty storageOrdinalProp = new IntegerProperty("storageOrdinal", 0);

  public CaseRecord() {
    super(8);
    declareProperty(processInstanceKeyProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(tenantIdProp)
        .declareProperty(businessIdProp)
        .declareProperty(titleProp)
        .declareProperty(stagesProp)
        .declareProperty(storageOrdinalProp);
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

  /**
   * This method is expensive because it copies each element before returning it.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<CaseStageValue> getStages() {
    // we need to make a copy of each element in the ArrayProperty while iterating it because the
    // inner values are updated during the iteration
    return stagesProp.stream()
        .map(
            element -> {
              final var elementCopy = new CaseStageRecord();
              elementCopy.copy(element);
              return (CaseStageValue) elementCopy;
            })
        .toList();
  }

  public CaseRecord addStage(final String elementId, final CaseStageStatus status) {
    stagesProp.add().setElementId(elementId).setStatus(status);
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
