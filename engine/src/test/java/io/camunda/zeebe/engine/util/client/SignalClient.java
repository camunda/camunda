/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */

package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.SignalRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SignalClient {

  private static final Function<Long, Record<SignalRecordValue>> SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.signalRecords(SignalIntent.BROADCASTED)
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final SignalRecord signalRecord = new SignalRecord();
  private final Function<Long, Record<SignalRecordValue>> expectation = SUCCESS_EXPECTATION;

  public SignalClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public SignalClient withSignalName(final String signalName) {
    signalRecord.setSignalName(signalName);
    return this;
  }

  public SignalClient withVariables(final String variables) {
    signalRecord.setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
    return this;
  }

  public SignalClient withVariables(final DirectBuffer variables) {
    signalRecord.setVariables(variables);
    return this;
  }

  public SignalClient withVariable(final String key, final Object value) {
    signalRecord.setVariables(MsgPackUtil.asMsgPack(key, value));
    return this;
  }

  public SignalClient withVariables(final Map<String, Object> variables) {
    signalRecord.setVariables(MsgPackUtil.asMsgPack(variables));
    return this;
  }

  public Record<SignalRecordValue> broadcast() {
    final long position = writer.writeCommand(SignalIntent.BROADCAST, signalRecord);
    return expectation.apply(position);
  }
}
