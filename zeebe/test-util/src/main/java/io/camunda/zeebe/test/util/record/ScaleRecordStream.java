/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.scaling.ScaleRecordValue;
import java.util.stream.Stream;

public class ScaleRecordStream extends ExporterRecordStream<ScaleRecordValue, ScaleRecordStream> {

  public ScaleRecordStream(final Stream<Record<ScaleRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ScaleRecordStream supply(final Stream<Record<ScaleRecordValue>> wrappedStream) {
    return new ScaleRecordStream(wrappedStream);
  }
}
