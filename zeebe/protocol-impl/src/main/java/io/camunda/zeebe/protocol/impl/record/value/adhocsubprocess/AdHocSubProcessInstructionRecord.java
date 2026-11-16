/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class AdHocSubProcessInstructionRecord
    extends MsgpackBufferValue<AdHocSubProcessInstructionRecord>
    implements MsgpackRecordValue, AdHocSubProcessInstructionRecordValue {

  long adHocSubProcessInstanceKey = -1L;
  final List<AdHocSubProcessActivateElementInstruction> activateElements = new ArrayList<>();
  boolean cancelRemainingInstances = false;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  boolean completionConditionFulfilled = false;
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getAdHocSubProcessInstanceKey() {
    return adHocSubProcessInstanceKey;
  }

  public AdHocSubProcessInstructionRecord setAdHocSubProcessInstanceKey(
      final long adHocSubProcessInstanceKey) {
    this.adHocSubProcessInstanceKey = adHocSubProcessInstanceKey;
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
    return cancelRemainingInstances;
  }

  @Override
  public boolean isCompletionConditionFulfilled() {
    return completionConditionFulfilled;
  }

  public AdHocSubProcessInstructionRecord setCompletionConditionFulfilled(final boolean fulfilled) {
    completionConditionFulfilled = fulfilled;
    return this;
  }

  public AdHocSubProcessInstructionRecord setCancelRemainingInstances(
      final boolean cancelRemainingInstances) {
    this.cancelRemainingInstances = cancelRemainingInstances;
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
    return new ActivateElements(activateElements);
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public AdHocSubProcessInstructionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public AdHocSubProcessInstructionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<AdHocSubProcessInstructionRecord> codec() {
    return AdHocSubProcessInstructionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AdHocSubProcessInstructionRecord> ops() {
    return AdHocSubProcessInstructionRecordOps.INSTANCE;
  }

  /**
   * The mutable accessor of {@code activateElements}: it adds and removes the elements of the list,
   * so that code written against the former {@code ArrayProperty} keeps working.
   */
  private static final class ActivateElements
      implements ValueArray<AdHocSubProcessActivateElementInstruction> {

    final List<AdHocSubProcessActivateElementInstruction> elements;

    private ActivateElements(final List<AdHocSubProcessActivateElementInstruction> elements) {
      this.elements = elements;
    }

    @Override
    public AdHocSubProcessActivateElementInstruction add() {
      final var element = new AdHocSubProcessActivateElementInstruction();
      elements.add(element);
      return element;
    }

    @Override
    public AdHocSubProcessActivateElementInstruction add(final int index) {
      final var element = new AdHocSubProcessActivateElementInstruction();
      elements.add(index, element);
      return element;
    }

    @Override
    public AdHocSubProcessActivateElementInstruction get(final int index) {
      return elements.get(index);
    }

    @Override
    public AdHocSubProcessActivateElementInstruction remove(final int index) {
      return elements.remove(index);
    }

    @Override
    public int size() {
      return elements.size();
    }

    @Override
    public Stream<AdHocSubProcessActivateElementInstruction> stream() {
      return elements.stream();
    }

    @Override
    public Iterator<AdHocSubProcessActivateElementInstruction> iterator() {
      return elements.iterator();
    }
  }
}
