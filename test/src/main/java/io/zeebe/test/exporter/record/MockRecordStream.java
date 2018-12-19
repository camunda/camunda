/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.exporter.record;

import io.zeebe.test.util.stream.StreamWrapper;
import java.util.stream.Stream;

public class MockRecordStream extends StreamWrapper<MockRecord, MockRecordStream> {

  public MockRecordStream(Stream<MockRecord> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MockRecordStream supply(Stream<MockRecord> wrappedStream) {
    return new MockRecordStream(wrappedStream);
  }

  public static MockRecordStream generate() {
    return generate(new MockRecord());
  }

  public static MockRecordStream generate(MockRecord seed) {
    return new MockRecordStream(Stream.iterate(seed, MockRecordStream::generateNextRecord));
  }

  private static MockRecord generateNextRecord(MockRecord previousRecord) {
    final long position = previousRecord.getPosition() + 1;
    final MockRecord nextRecord = (MockRecord) previousRecord.clone();

    return nextRecord
        .setPosition(position)
        .setTimestamp(previousRecord.getTimestamp().plusSeconds(1))
        .setKey(position);
  }
}
