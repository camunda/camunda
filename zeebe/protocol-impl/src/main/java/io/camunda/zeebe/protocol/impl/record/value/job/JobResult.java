/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static io.camunda.zeebe.msgpack.value.StringValue.EMPTY_STRING;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultActivateElementValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultValue;
import io.camunda.zeebe.protocol.record.value.JobResultType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.StreamSupport;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "empty",
  "encodedLength",
  "length"
})
public class JobResult extends UnpackedObject implements JobResultValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue TYPE_KEY = new StringValue("type");
  private static final StringValue DENIED_KEY = new StringValue("denied");
  private static final StringValue CORRECTED_ATTRIBUTES_KEY =
      new StringValue("correctedAttributes");
  private static final StringValue CORRECTIONS_KEY = new StringValue("corrections");
  private static final StringValue DENIED_REASON_KEY = new StringValue("deniedReason");
  private static final StringValue ACTIVATE_ELEMENTS_KEY = new StringValue("activateElements");

  private final EnumProperty<JobResultType> typeProp =
      new EnumProperty<>(TYPE_KEY, JobResultType.class, JobResultType.USER_TASK);

  // User task properties
  private final BooleanProperty deniedProp = new BooleanProperty(DENIED_KEY, false);
  private final ArrayProperty<StringValue> correctedAttributesProp =
      new ArrayProperty<>(CORRECTED_ATTRIBUTES_KEY, StringValue::new);
  private final ObjectProperty<JobResultCorrections> correctionsProp =
      new ObjectProperty<>(CORRECTIONS_KEY, new JobResultCorrections());
  private final StringProperty deniedReasonProp =
      new StringProperty(DENIED_REASON_KEY, EMPTY_STRING);

  // Ad-hoc subprocess properties
  private final ArrayProperty<JobResultActivateElement> activateElementsProp =
      new ArrayProperty<>(ACTIVATE_ELEMENTS_KEY, JobResultActivateElement::new);

  public JobResult() {
    super(6);
    declareProperty(typeProp)
        .declareProperty(deniedProp)
        .declareProperty(correctionsProp)
        .declareProperty(correctedAttributesProp)
        .declareProperty(deniedReasonProp)
        .declareProperty(activateElementsProp);
  }

  /** Sets all properties to current instance from provided user task job data */
  public void wrap(final JobResult result) {
    typeProp.setValue(result.getType());
    deniedProp.setValue(result.isDenied());
    setCorrectedAttributes(result.getCorrectedAttributes());
    setCorrections(result.getCorrections());
    setDeniedReason(result.getDeniedReason());
    setActivateElements(result.getActivateElements());
  }

  @Override
  public JobResultType getType() {
    return typeProp.getValue();
  }

  public JobResult setType(final JobResultType type) {
    typeProp.setValue(type);
    return this;
  }

  @Override
  public boolean isDenied() {
    return deniedProp.getValue();
  }

  public JobResult setDenied(final boolean denied) {
    deniedProp.setValue(denied);
    return this;
  }

  @Override
  public String getDeniedReason() {
    return bufferAsString(deniedReasonProp.getValue());
  }

  public JobResult setDeniedReason(final String deniedReason) {
    deniedReasonProp.setValue(deniedReason);
    return this;
  }

  @Override
  public List<String> getCorrectedAttributes() {
    return StreamSupport.stream(correctedAttributesProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public JobResult setCorrectedAttributes(final List<String> correctedAttributes) {
    correctedAttributesProp.reset();
    correctedAttributes.forEach(
        attribute -> correctedAttributesProp.add().wrap(BufferUtil.wrapString(attribute)));
    return this;
  }

  @Override
  public JobResultCorrections getCorrections() {
    return correctionsProp.getValue();
  }

  public JobResult setCorrections(final JobResultCorrections corrections) {
    correctionsProp.getValue().wrap(corrections);
    return this;
  }

  @Override
  public List<JobResultActivateElementValue> getActivateElements() {
    return StreamSupport.stream(activateElementsProp.spliterator(), false)
        .map(
            activateElement -> {
              final var copy = new JobResultActivateElement();
              copy.copyFrom(activateElement);
              return (JobResultActivateElementValue) copy;
            })
        .toList();
  }

  public JobResult setActivateElements(final List<JobResultActivateElementValue> elements) {
    activateElementsProp.reset();
    elements.forEach(
        element -> activateElementsProp.add().copyFrom((JobResultActivateElement) element));
    return this;
  }

  public JobResult addActivateElement(final JobResultActivateElement activateElement) {
    activateElementsProp.add().copyFrom(activateElement);
    return this;
  }
}
