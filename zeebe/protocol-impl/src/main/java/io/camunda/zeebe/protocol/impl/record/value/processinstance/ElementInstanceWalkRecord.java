/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ElementInstanceWalkRecordValue;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ElementInstanceWalkRecord extends MsgpackBufferValue<ElementInstanceWalkRecord>
    implements MsgpackRecordValue, ElementInstanceWalkRecordValue {

  long processInstanceKey = -1L;
  long processDefinitionKey = -1L;

  /** The element instance to suspend. */
  long elementInstanceKey = -1L;

  /**
   * The parent of {@code elementInstanceKey}, or {@code -1} for the process instance; used to
   * continue the walk if the element instance is gone.
   */
  long parentKey = -1L;

  /**
   * The child of {@code elementInstanceKey} that was suspended last, so the walk continues after
   * it; {@code -1} to start at the first child.
   */
  long index = -1L;

  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public ElementInstanceWalkRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ElementInstanceWalkRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public ElementInstanceWalkRecord setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  @Override
  public long getParentKey() {
    return parentKey;
  }

  public ElementInstanceWalkRecord setParentKey(final long parentKey) {
    this.parentKey = parentKey;
    return this;
  }

  @Override
  public long getIndex() {
    return index;
  }

  public ElementInstanceWalkRecord setIndex(final long index) {
    this.index = index;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ElementInstanceWalkRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ElementInstanceWalkRecord> codec() {
    return ElementInstanceWalkRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ElementInstanceWalkRecord> ops() {
    return ElementInstanceWalkRecordOps.INSTANCE;
  }
}
