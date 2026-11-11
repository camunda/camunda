/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.pojo.instance;

import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.agrona.DirectBuffer;

/**
 * Plain data. Buffers are stored by reference: whoever sets one keeps ownership and must not modify
 * it while this object is in use. The deserializer fills buffers it allocates itself.
 */
@MsgpackPojo
public final class IndexedRecordData {
  @MsgpackProperty("key")
  private long key;

  @MsgpackProperty("state")
  private ProcessInstanceIntent state;

  /** The encoded msgpack map of the {@code ProcessInstanceRecord}. */
  @MsgpackProperty(value = "processInstanceRecord", raw = true)
  private DirectBuffer processInstanceRecord = io.camunda.zeebe.msgpack.pojo.Decoding.EMPTY;

  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  public ProcessInstanceIntent getState() {
    return state;
  }

  public void setState(final ProcessInstanceIntent state) {
    this.state = state;
  }

  public DirectBuffer getProcessInstanceRecord() {
    return processInstanceRecord;
  }

  public void setProcessInstanceRecord(final DirectBuffer processInstanceRecord) {
    this.processInstanceRecord = processInstanceRecord;
  }
}
