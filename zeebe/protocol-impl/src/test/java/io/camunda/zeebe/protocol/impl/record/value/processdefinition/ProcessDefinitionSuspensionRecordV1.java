/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processdefinition;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class ProcessDefinitionSuspensionRecordV1 extends UnifiedRecordValue
    implements ProcessDefinitionSuspensionRecordValue {

  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final BooleanProperty includeProcessInstancesProp =
      new BooleanProperty("includeProcessInstances", false);
  private final ArrayProperty<ProcessDefinitionSuspensionResolvedVersion> resolvedVersionsProp =
      new ArrayProperty<>("resolvedVersions", ProcessDefinitionSuspensionResolvedVersion::new);
  private final LongProperty batchOperationKeyProp = new LongProperty("batchOperationKey", -1);

  public ProcessDefinitionSuspensionRecordV1() {
    super(7);
    declareProperty(processDefinitionKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(versionProp)
        .declareProperty(tenantIdProp)
        .declareProperty(includeProcessInstancesProp)
        .declareProperty(resolvedVersionsProp)
        .declareProperty(batchOperationKeyProp);
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public ProcessDefinitionSuspensionRecordV1 setProcessDefinitionKey(final long key) {
    processDefinitionKeyProp.setValue(key);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public ProcessDefinitionSuspensionRecordV1 setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  public ProcessDefinitionSuspensionRecordV1 setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ProcessDefinitionSuspensionRecordV1 setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public boolean isIncludeProcessInstances() {
    return includeProcessInstancesProp.getValue();
  }

  public ProcessDefinitionSuspensionRecordV1 setIncludeProcessInstances(
      final boolean includeProcessInstances) {
    includeProcessInstancesProp.setValue(includeProcessInstances);
    return this;
  }

  @Override
  public List<ProcessDefinitionSuspensionResolvedVersionValue> getResolvedVersions() {
    return resolvedVersionsProp.stream().collect(Collectors.toList());
  }

  public ProcessDefinitionSuspensionRecordV1 addResolvedVersion(
      final ProcessDefinitionSuspensionResolvedVersionValue resolvedVersion) {
    resolvedVersionsProp
        .add()
        .setProcessDefinitionKey(resolvedVersion.getProcessDefinitionKey())
        .setVersion(resolvedVersion.getVersion())
        .setOutcome(resolvedVersion.getOutcome());
    return this;
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public ProcessDefinitionSuspensionRecordV1 setBatchOperationKey(final long batchOperationKey) {
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }
}
