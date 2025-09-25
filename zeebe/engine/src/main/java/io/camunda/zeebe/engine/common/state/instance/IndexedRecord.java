/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class IndexedRecord extends UnpackedObject implements DbValue {
  // Static StringValue keys for property names
  private static final StringValue KEY_KEY = new StringValue("key");
  private static final StringValue STATE_KEY = new StringValue("state");
  private static final StringValue PROCESS_INSTANCE_RECORD_KEY =
      new StringValue("processInstanceRecord");

  private final LongProperty keyProp = new LongProperty(KEY_KEY, 0L);
  private final EnumProperty<ProcessInstanceIntent> stateProp =
      new EnumProperty<>(STATE_KEY, ProcessInstanceIntent.class);
  private final ObjectProperty<ProcessInstanceRecord> valueProp =
      new ObjectProperty<>(PROCESS_INSTANCE_RECORD_KEY, new ProcessInstanceRecord());

  IndexedRecord() {
    super(3);
    declareProperty(keyProp).declareProperty(stateProp).declareProperty(valueProp);
  }

  public IndexedRecord(
      final long key,
      final ProcessInstanceIntent instanceState,
      final ProcessInstanceRecord record) {
    this();
    keyProp.setValue(key);
    stateProp.setValue(instanceState);
    setValue(record);
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public IndexedRecord setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public ProcessInstanceIntent getState() {
    return stateProp.getValue();
  }

  public IndexedRecord setState(final ProcessInstanceIntent state) {
    stateProp.setValue(state);
    return this;
  }

  public boolean hasState(final ProcessInstanceIntent state) {
    return getState() == state;
  }

  public ProcessInstanceRecord getValue() {
    return valueProp.getValue();
  }

  public IndexedRecord setValue(final ProcessInstanceRecord value) {
    final MutableDirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = value.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    value.write(valueBuffer, 0);
    valueProp.getValue().wrap(valueBuffer, 0, encodedLength);

    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] bytes = new byte[length];
    final UnsafeBuffer mutableBuffer = new UnsafeBuffer(bytes);
    buffer.getBytes(offset, bytes, 0, length);
    super.wrap(mutableBuffer, 0, length);
  }
}
