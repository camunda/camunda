/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.probe;

import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.util.buffer.BufferWriter;

/** A probe as pushed to a worker: the record carries the input with the secret values injected. */
public interface ActivatedProbe extends BufferWriter {

  long probeKey();

  ProbeRecord probeRecord();
}
