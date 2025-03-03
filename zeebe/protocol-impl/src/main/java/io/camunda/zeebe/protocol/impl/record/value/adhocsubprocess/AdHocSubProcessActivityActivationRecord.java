/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessActivityActivationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class AdHocSubProcessActivityActivationRecord extends UnifiedRecordValue
    implements AdHocSubProcessActivityActivationRecordValue {

  private static final String DEFAULT_STRING = "";

  private final StringProperty adHocSubProcessInstanceKey =
      new StringProperty("adHocSubProcessInstanceKey", DEFAULT_STRING);
  private final ArrayProperty<AdHocSubProcessActivityActivationFlowNode> flowNodes =
      new ArrayProperty<>("flowNodes", AdHocSubProcessActivityActivationFlowNode::new);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public AdHocSubProcessActivityActivationRecord() {
    super(3);
    declareProperty(adHocSubProcessInstanceKey)
        .declareProperty(flowNodes)
        .declareProperty(tenantIdProp);
  }

  @Override
  public String getAdHocSubProcessInstanceKey() {
    return BufferUtil.bufferAsString(adHocSubProcessInstanceKey.getValue());
  }

  public AdHocSubProcessActivityActivationRecord setAdHocSubProcessInstanceKey(
      final String adHocSubProcessInstanceKey) {
    this.adHocSubProcessInstanceKey.setValue(adHocSubProcessInstanceKey);
    return this;
  }

  @Override
  public List<AdHocSubProcessActivityActivationFlowNodeValue> getFlowNodes() {
    return flowNodes.stream()
        .map(AdHocSubProcessActivityActivationFlowNodeValue.class::cast)
        .toList();
  }

  /**
   * This function exists only to make setting up the test for the `JsonSerializableToJsonTest`.
   *
   * @return a {@link ValueArray} of flow nodes that can easily be added to.
   */
  public ValueArray<AdHocSubProcessActivityActivationFlowNode> flowNodes() {
    return flowNodes;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public AdHocSubProcessActivityActivationRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
