/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class AdHocSubProcessInstructionRecord extends UnifiedRecordValue
    implements AdHocSubProcessInstructionRecordValue {

  private static final String DEFAULT_STRING = "";

  private final StringProperty adHocSubProcessInstanceKey =
      new StringProperty("adHocSubProcessInstanceKey", DEFAULT_STRING);
  private final ArrayProperty<AdHocSubProcessActivateElementInstruction> activateElements =
      new ArrayProperty<>("activateElements", AdHocSubProcessActivateElementInstruction::new);
  private final StringProperty tenantId =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public AdHocSubProcessInstructionRecord() {
    super(3);
    declareProperty(adHocSubProcessInstanceKey)
        .declareProperty(activateElements)
        .declareProperty(tenantId);
  }

  @Override
  public String getAdHocSubProcessInstanceKey() {
    return BufferUtil.bufferAsString(adHocSubProcessInstanceKey.getValue());
  }

  public AdHocSubProcessInstructionRecord setAdHocSubProcessInstanceKey(
      final String adHocSubProcessInstanceKey) {
    this.adHocSubProcessInstanceKey.setValue(adHocSubProcessInstanceKey);
    return this;
  }

  @Override
  public List<AdHocSubProcessActivateElementInstructionValue> getActivateElements() {
    return activateElements.stream()
        .map(AdHocSubProcessActivateElementInstructionValue.class::cast)
        .toList();
  }

  /**
   * Returns the {@link ValueArray} of `activateElements` which then can have more elements
   * added/removed. This is used in setting up test scenarios.
   *
   * @return a {@link ValueArray} of flow nodes that can easily be added to.
   */
  @JsonIgnore
  public ValueArray<AdHocSubProcessActivateElementInstruction> activateElements() {
    return activateElements;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId.getValue());
  }

  public AdHocSubProcessInstructionRecord setTenantId(final String tenantId) {
    this.tenantId.setValue(tenantId);
    return this;
  }
}
