/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */

package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.SignalClient;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.SignalRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class BroadcastSignalTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String SIGNAL_NAME = "a";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private SignalClient signalClient;

  @Before
  public void init() {
    signalClient = ENGINE_RULE.signal().withSignalName(SIGNAL_NAME);
  }

  @Test
  public void shouldBroadcastSignal() {
    // when
    final Record<SignalRecordValue> record = signalClient.broadcast();

    // then
    assertThat(record.getValue().getVariables()).isEmpty();

    Assertions.assertThat(record)
        .hasIntent(SignalIntent.BROADCASTED)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.SIGNAL);

    Assertions.assertThat(record.getValue()).hasSignalName(SIGNAL_NAME);
  }

  @Test
  public void shouldBroadcastSignalWithVariables() {
    // when
    final Record<SignalRecordValue> record =
        signalClient.withVariables("{'foo':'bar'}").broadcast();

    // then
    assertThat(record.getValue().getVariables()).containsExactly(entry("foo", "bar"));
  }

  @Test
  public void shouldBroadcastSignalWithDifferentName() {
    // when
    final Record<SignalRecordValue> firstRecord = signalClient.withSignalName("a").broadcast();

    final Record<SignalRecordValue> secondRecord = signalClient.withSignalName("b").broadcast();

    // then
    assertThat(firstRecord.getKey()).isLessThan(secondRecord.getKey());
  }
}
