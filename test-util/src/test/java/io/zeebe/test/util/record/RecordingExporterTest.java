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
package io.zeebe.test.util.record;

import static io.zeebe.test.util.record.RecordingExporter.records;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public class RecordingExporterTest {

  public static final ValueType VALUE_TYPE = ValueType.JOB;

  @Before
  public void setUp() {
    RecordingExporter.reset();
  }

  @Test
  public void shouldCollectToList() {
    // given
    final RecordingExporter exporter = new RecordingExporter();
    exporter.export(new TestRecord(1));
    exporter.export(new TestRecord(2));
    exporter.export(new TestRecord(3));

    // when
    final List<Record<TestValue>> list =
        records(VALUE_TYPE, TestValue.class).collect(Collectors.toList());

    // then
    assertThat(list).extracting(Record::getPosition).containsExactly(1L, 2L, 3L);
  }

  public static class TestRecord implements Record<TestValue> {

    private final long position;

    public TestRecord(final long position) {
      this.position = position;
    }

    @Override
    public long getPosition() {
      return position;
    }

    @Override
    public long getSourceRecordPosition() {
      return 0;
    }

    @Override
    public int getProducerId() {
      return 0;
    }

    @Override
    public long getKey() {
      return 0;
    }

    @Override
    public Instant getTimestamp() {
      return null;
    }

    @Override
    public RecordMetadata getMetadata() {
      return new RecordMetadata() {
        @Override
        public Intent getIntent() {
          return null;
        }

        @Override
        public int getPartitionId() {
          return 0;
        }

        @Override
        public RecordType getRecordType() {
          return null;
        }

        @Override
        public RejectionType getRejectionType() {
          return null;
        }

        @Override
        public String getRejectionReason() {
          return null;
        }

        @Override
        public ValueType getValueType() {
          return VALUE_TYPE;
        }

        @Override
        public String toJson() {
          return null;
        }
      };
    }

    @Override
    public TestValue getValue() {
      return null;
    }

    @Override
    public String toJson() {
      return null;
    }
  }

  public static class TestValue implements RecordValue {
    @Override
    public String toJson() {
      return "{}";
    }
  }
}
