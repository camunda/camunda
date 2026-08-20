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
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class AdHocSubProcessInstructionRecord extends UnifiedRecordValue
    implements AdHocSubProcessInstructionRecordValue {
  private static final StringValue AD_HOC_SUB_PROCESS_INSTANCE_KEY =
      new StringValue("adHocSubProcessInstanceKey");
  private static final StringValue ACTIVATE_ELEMENTS = new StringValue("activateElements");
  private static final StringValue CANCEL_REMAINING_INSTANCES =
      new StringValue("cancelRemainingInstances");
  private static final StringValue TENANT_ID = new StringValue("tenantId");
  private static final StringValue COMPLETION_CONDITION_FULFILLED =
      new StringValue("completionConditionFulfilled");

  private final LongProperty adHocSubProcessInstanceKey =
      new LongProperty(AD_HOC_SUB_PROCESS_INSTANCE_KEY, -1L);
  private final ArrayProperty<AdHocSubProcessActivateElementInstruction> activateElements =
      new ArrayProperty<>(ACTIVATE_ELEMENTS, AdHocSubProcessActivateElementInstruction::new);
  private final BooleanProperty cancelRemainingInstances =
      new BooleanProperty(CANCEL_REMAINING_INSTANCES, false);
  private final StringProperty tenantId =
      new StringProperty(TENANT_ID, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final BooleanProperty completionConditionFulfilledProp =
      new BooleanProperty(COMPLETION_CONDITION_FULFILLED, false);

  public AdHocSubProcessInstructionRecord() {
    super(5);
    declareProperty(adHocSubProcessInstanceKey)
        .declareProperty(activateElements)
        .declareProperty(cancelRemainingInstances)
        .declareProperty(tenantId)
        .declareProperty(completionConditionFulfilledProp);
  }

  @Override
  public long getAdHocSubProcessInstanceKey() {
    return adHocSubProcessInstanceKey.getValue();
  }

  public AdHocSubProcessInstructionRecord setAdHocSubProcessInstanceKey(
      final long adHocSubProcessInstanceKey) {
    this.adHocSubProcessInstanceKey.setValue(adHocSubProcessInstanceKey);
    return this;
  }

  @Override
  public List<AdHocSubProcessActivateElementInstructionValue> getActivateElements() {
    return activateElements.stream()
        .map(AdHocSubProcessActivateElementInstructionValue.class::cast)
        .toList();
  }

  @Override
  public boolean isCancelRemainingInstances() {
    return cancelRemainingInstances.getValue();
  }

  @Override
  public boolean isCompletionConditionFulfilled() {
    return completionConditionFulfilledProp.getValue();
  }

  public AdHocSubProcessInstructionRecord setCompletionConditionFulfilled(final boolean fulfilled) {
    completionConditionFulfilledProp.setValue(fulfilled);
    return this;
  }

  public AdHocSubProcessInstructionRecord setCancelRemainingInstances(
      final boolean cancelRemainingInstances) {
    this.cancelRemainingInstances.setValue(cancelRemainingInstances);
    return this;
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
