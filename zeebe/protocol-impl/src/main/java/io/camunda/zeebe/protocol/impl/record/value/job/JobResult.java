/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultActivateElementValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultValue;
import io.camunda.zeebe.protocol.record.value.JobResultType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({
  /* encodedLength and empty were inherited from ObjectValue, length is inherited from
  MsgpackBufferValue; they have no purpose in exported JSON records*/
  "empty",
  "encodedLength",
  "length"
})
@MsgpackPojo
@NullMarked
public final class JobResult extends MsgpackBufferValue<JobResult> implements JobResultValue {

  JobResultType type = JobResultType.USER_TASK;

  // User task properties
  boolean denied = false;
  final JobResultCorrections corrections = new JobResultCorrections();
  final List<DirectBuffer> correctedAttributes = new ArrayList<>();
  DirectBuffer deniedReason = Decoding.EMPTY;

  // Ad-hoc subprocess properties
  final List<JobResultActivateElement> activateElements = new ArrayList<>();
  boolean isCompletionConditionFulfilled = false;
  boolean isCancelRemainingInstances = false;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  /** Sets all properties to current instance from provided user task job data */
  public void wrap(final JobResult result) {
    type = result.getType();
    denied = result.isDenied();
    setCorrectedAttributes(result.getCorrectedAttributes());
    setCorrections(result.getCorrections());
    setDeniedReason(result.getDeniedReason());
    setActivateElements(result.getActivateElements());
    setCompletionConditionFulfilled(result.isCompletionConditionFulfilled());
    setCancelRemainingInstances(result.isCancelRemainingInstances());
  }

  @Override
  public JobResultType getType() {
    return type;
  }

  public JobResult setType(final JobResultType type) {
    this.type = type;
    return this;
  }

  @Override
  public boolean isDenied() {
    return denied;
  }

  public JobResult setDenied(final boolean denied) {
    this.denied = denied;
    return this;
  }

  @Override
  public String getDeniedReason() {
    return bufferAsString(deniedReason);
  }

  public JobResult setDeniedReason(final String deniedReason) {
    this.deniedReason = BufferUtil.wrapString(deniedReason);
    return this;
  }

  @Override
  public List<String> getCorrectedAttributes() {
    return correctedAttributes.stream().map(BufferUtil::bufferAsString).toList();
  }

  public JobResult setCorrectedAttributes(final List<String> correctedAttributes) {
    this.correctedAttributes.clear();
    correctedAttributes.forEach(
        attribute -> this.correctedAttributes.add(BufferUtil.wrapString(attribute)));
    return this;
  }

  @Override
  public JobResultCorrections getCorrections() {
    return corrections;
  }

  public JobResult setCorrections(final JobResultCorrections corrections) {
    this.corrections.wrap(corrections);
    return this;
  }

  @Override
  public List<JobResultActivateElementValue> getActivateElements() {
    return activateElements.stream().<JobResultActivateElementValue>map(JobResult::copyOf).toList();
  }

  public JobResult setActivateElements(final List<JobResultActivateElementValue> elements) {
    activateElements.clear();
    elements.forEach(element -> addActivateElement((JobResultActivateElement) element));
    return this;
  }

  @Override
  public boolean isCompletionConditionFulfilled() {
    return isCompletionConditionFulfilled;
  }

  public JobResult setCompletionConditionFulfilled(final boolean completionConditionFulfilled) {
    isCompletionConditionFulfilled = completionConditionFulfilled;
    return this;
  }

  @Override
  public boolean isCancelRemainingInstances() {
    return isCancelRemainingInstances;
  }

  public JobResult setCancelRemainingInstances(final boolean cancelRemainingInstances) {
    isCancelRemainingInstances = cancelRemainingInstances;
    return this;
  }

  public JobResult addActivateElement(final JobResultActivateElement activateElement) {
    activateElements.add(copyOf(activateElement));
    return this;
  }

  private static JobResultActivateElement copyOf(final JobResultActivateElement source) {
    final var copy = new JobResultActivateElement();
    copy.copyFrom(source);
    return copy;
  }

  @Override
  protected MsgpackCodec<JobResult> codec() {
    return JobResultCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<JobResult> ops() {
    return JobResultOps.INSTANCE;
  }
}
