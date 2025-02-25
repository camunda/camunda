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
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultValue;
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

  private final BooleanProperty deniedProp = new BooleanProperty("denied", false);
  private final ArrayProperty<StringValue> correctedAttributesProp =
      new ArrayProperty<>("correctedAttributes", StringValue::new);
  private final ObjectProperty<JobResultCorrections> correctionsProp =
      new ObjectProperty<>("corrections", new JobResultCorrections());
  private final StringProperty deniedReasonProp = new StringProperty("deniedReason", EMPTY_STRING);

  public JobResult() {
    super(4);
    declareProperty(deniedProp)
        .declareProperty(correctionsProp)
        .declareProperty(correctedAttributesProp)
        .declareProperty(deniedReasonProp);
  }

  /** Sets all properties to current instance from provided user task job data */
  public void wrap(final JobResult result) {
    deniedProp.setValue(result.isDenied());
    setCorrectedAttributes(result.getCorrectedAttributes());
    setCorrections(result.getCorrections());
    setDeniedReason(result.getDeniedReason());
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
}
