/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.GlobalVariableRecordValue;
import java.util.stream.Stream;

public final class GlobalVariableRecordStream
    extends ExporterRecordStream<GlobalVariableRecordValue, GlobalVariableRecordStream> {

  public GlobalVariableRecordStream(final Stream<Record<GlobalVariableRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected GlobalVariableRecordStream supply(
      final Stream<Record<GlobalVariableRecordValue>> wrappedStream) {
    return new GlobalVariableRecordStream(wrappedStream);
  }
}
