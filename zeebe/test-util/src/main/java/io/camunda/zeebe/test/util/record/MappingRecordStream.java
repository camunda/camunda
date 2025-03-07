/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import java.util.stream.Stream;

public class MappingRecordStream
    extends ExporterRecordStream<MappingRecordValue, MappingRecordStream> {

  public MappingRecordStream(final Stream<Record<MappingRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MappingRecordStream supply(final Stream<Record<MappingRecordValue>> wrappedStream) {
    return new MappingRecordStream(wrappedStream);
  }

  public MappingRecordStream withClaimName(final String claimName) {
    return valueFilter(v -> v.getClaimName().equals(claimName));
  }

  public MappingRecordStream withClaimValue(final String claimValue) {
    return valueFilter(v -> v.getClaimValue().equals(claimValue));
  }
}
