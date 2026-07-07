/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Records a variable's latest value under its scope so a later completion of that scope can enrich
 * its fact. Overwrite-on-set per {@code (scopeKey, name)}, matching the engine's {@code
 * DbVariableState.setVariableLocal}: each {@code VARIABLE} record is already one atomic {@code
 * (scope, name, value)}, so there is nothing to merge. Emits no fact.
 *
 * <p>Name and value flow as buffer views straight off the record (ADR 0008): the name buffer is the
 * store-key suffix, and a msgpack <em>string</em> value stores its raw UTF-8 payload slice — the
 * dominant case, with no {@code String} round trip. A non-string value (number, boolean, object, …)
 * falls back to its JSON text, as before.
 */
public final class VariableApplier implements EventApplier {

  private final MutableProjectionState state;
  private final MsgPackReader reader = new MsgPackReader();
  private final UnsafeBuffer slice = new UnsafeBuffer(0, 0);

  public VariableApplier(final MutableProjectionState state) {
    this.state = state;
  }

  @Override
  public void apply(final SourceRecord source) {
    final VariableRecord value = (VariableRecord) source.record().getValue();
    state.putVariable(value.getScopeKey(), value.getNameBuffer(), stored(value.getValueBuffer()));
  }

  /**
   * The bytes to store for a msgpack-encoded variable value: a string's UTF-8 payload slice
   * (equivalent to the previous JSON-unquote of {@code "EU"} → EU), anything else as its JSON text.
   * The returned view is only valid until the next call.
   */
  private DirectBuffer stored(final DirectBuffer packed) {
    if (packed.capacity() > 0 && isMsgPackString(packed.getByte(0))) {
      reader.wrap(packed, 0, packed.capacity());
      final int length = reader.readStringLength();
      slice.wrap(packed, reader.getOffset(), length);
      return slice;
    }
    return BufferUtil.wrapString(MsgPackConverter.convertToJson(packed));
  }

  private static boolean isMsgPackString(final byte formatByte) {
    final int format = formatByte & 0xFF;
    return (format >= 0xA0 && format <= 0xBF) // fixstr
        || format == 0xD9 // str8
        || format == 0xDA // str16
        || format == 0xDB; // str32
  }
}
