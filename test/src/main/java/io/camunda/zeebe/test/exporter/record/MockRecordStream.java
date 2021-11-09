/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter.record;

import io.camunda.zeebe.test.util.stream.StreamWrapper;
import java.util.stream.Stream;

/**
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class MockRecordStream extends StreamWrapper<MockRecord, MockRecordStream> {

  public MockRecordStream(final Stream<MockRecord> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MockRecordStream supply(final Stream<MockRecord> wrappedStream) {
    return new MockRecordStream(wrappedStream);
  }

  public static MockRecordStream generate() {
    return generate(new MockRecord());
  }

  public static MockRecordStream generate(final MockRecord seed) {
    return new MockRecordStream(Stream.iterate(seed, MockRecordStream::generateNextRecord));
  }

  private static MockRecord generateNextRecord(final MockRecord previousRecord) {
    final long position = previousRecord.getPosition() + 1;
    final MockRecord nextRecord = (MockRecord) previousRecord.clone();

    return nextRecord
        .setPosition(position)
        .setTimestamp(previousRecord.getTimestamp() + 1)
        .setKey(position);
  }
}
