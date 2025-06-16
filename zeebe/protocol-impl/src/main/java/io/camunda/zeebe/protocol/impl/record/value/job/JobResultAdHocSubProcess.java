/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultAdHocSubProcessActivateElementValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultAdHocSubProcessValue;
import java.util.List;
import java.util.stream.StreamSupport;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "empty",
  "encodedLength",
  "length"
})
public class JobResultAdHocSubProcess extends UnpackedObject
    implements JobResultAdHocSubProcessValue {

  private final ArrayProperty<JobResultAdHocSubProcessActivateElement> activateElementsProp =
      new ArrayProperty<>("activateElements", JobResultAdHocSubProcessActivateElement::new);

  private final BooleanProperty isCompletionConditionFulfilledProp =
      new BooleanProperty("isCompletionConditionFulfilled", false);

  private final BooleanProperty isCancelRemainingInstancesProp =
      new BooleanProperty("isCancelRemainingInstances", false);

  public JobResultAdHocSubProcess() {
    super(3);
    declareProperty(activateElementsProp)
        .declareProperty(isCompletionConditionFulfilledProp)
        .declareProperty(isCancelRemainingInstancesProp);
  }

  public void wrap(final JobResultAdHocSubProcess other) {
    isCompletionConditionFulfilledProp.setValue(
        other.isCompletionConditionFulfilledProp.getValue());
    isCancelRemainingInstancesProp.setValue(other.isCancelRemainingInstancesProp.getValue());
    activateElementsProp.reset();
    other.activateElementsProp.forEach(
        activateElement -> activateElementsProp.add().wrap(activateElement));
  }

  @Override
  public List<JobResultAdHocSubProcessActivateElementValue> getActivateElements() {
    return StreamSupport.stream(activateElementsProp.spliterator(), false)
        .map(JobResultAdHocSubProcessActivateElementValue.class::cast)
        .toList();
  }

  @Override
  public boolean isCompletionConditionFulfilled() {
    return isCompletionConditionFulfilledProp.getValue();
  }

  @Override
  public boolean isCancelRemainingInstances() {
    return isCancelRemainingInstancesProp.getValue();
  }

  public JobResultAdHocSubProcess setCancelRemainingInstances(
      final boolean cancelRemainingInstances) {
    isCancelRemainingInstancesProp.setValue(cancelRemainingInstances);
    return this;
  }

  public JobResultAdHocSubProcess setCompletionConditionFulfilled(
      final boolean isCompletionConditionFulfilled) {
    isCompletionConditionFulfilledProp.setValue(isCompletionConditionFulfilled);
    return this;
  }

  @JsonIgnore
  public ValueArray<JobResultAdHocSubProcessActivateElement> activateElements() {
    return activateElementsProp;
  }
}
