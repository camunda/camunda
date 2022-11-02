/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;

import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamProcessorRule;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.verification.VerificationWithTimeout;

public final class StreamProcessorReprocessingTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();

  @Before
  public void setup() {
    final var mockEventApplier = mock(EventApplier.class);
    streamProcessorRule.withEventApplierFactory(state -> mockEventApplier);
  }
}
