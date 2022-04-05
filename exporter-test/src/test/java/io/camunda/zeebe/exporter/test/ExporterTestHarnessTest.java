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
package io.camunda.zeebe.exporter.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Duration;
import org.agrona.collections.MutableBoolean;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterTestHarnessTest {
  private static final String ID = "test";

  private final ProtocolFactory factory = new ProtocolFactory();
  private final TestExporter exporter = new TestExporter();
  private final ExporterTestHarness harness = new ExporterTestHarness(exporter);

  @Test
  void shouldConfigureExporter() throws Exception {
    // given
    final var config = new Object();

    // when
    harness.configure(ID, config);

    // then
    assertThat(exporter.getContext())
        .as("context's configuration has the right ID and instantiates the given config")
        .extracting(Context::getConfiguration)
        .asInstanceOf(InstanceOfAssertFactories.type(Configuration.class))
        .extracting(Configuration::getId, c -> c.instantiate(Object.class))
        .containsExactly(ID, config);
  }

  @Test
  void shouldOpenExporter() throws Exception {
    // given
    harness.configure(ID, (Object) null);

    // when
    harness.open();

    // then
    assertThat(exporter.getController()).isNotNull();
  }

  @Test
  void shouldCloseExporter() throws Exception {
    // given
    harness.configure(ID, (Object) null);
    harness.open();

    // when
    harness.close();

    // then
    assertThat(exporter.isClosed()).as("Exporter#close() was called").isTrue();
  }

  @Test
  void shouldExportRecord() throws Exception {
    // given
    final var record = factory.generateRecord(builder -> builder.withPartitionId(1));
    harness.configure(ID, (Object) null);
    harness.open();

    // when
    harness.export(record);

    // then
    assertThat(exporter.getExportedRecords()).containsExactly(record);
  }

  @Test
  void shouldExportRecords() throws Exception {
    // given
    harness.configure(ID, (Object) null);
    harness.open();

    // when
    final var records = harness.export(1, 5);

    // then
    assertThat(exporter.getExportedRecords())
        .containsExactlyElementsOf(records)
        .map(Record::getPosition)
        .containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void shouldResetScheduledTasksOnReopen() throws Exception {
    // given
    final MutableBoolean wasRan = new MutableBoolean();
    harness.configure(ID, (Object) null);
    harness.open();

    // when
    exporter.scheduleTask(Duration.ZERO, () -> wasRan.set(true));
    harness.close();
    harness.open();

    // then
    assertThat(harness.getScheduledTasks()).isEmpty();
    harness.runScheduledTasks(Duration.ofMinutes(10));
    assertThat(wasRan.get()).as("task should not have run").isFalse();
  }

  @Test
  void shouldRunScheduledTasks() throws Exception {
    // given
    final MutableBoolean wasRan = new MutableBoolean();
    harness.configure(ID, (Object) null);
    harness.open();

    // when
    exporter.scheduleTask(Duration.ofMinutes(1), () -> wasRan.set(true));

    // then
    assertThat(harness.getScheduledTasks()).isNotEmpty();
    harness.runScheduledTasks(Duration.ofMinutes(10));
    assertThat(wasRan.get()).as("task should have run").isTrue();
  }

  @Test
  void shouldUpdateLastExportedPosition() throws Exception {
    // given
    harness.configure(ID, (Object) null);
    harness.open();

    // when
    exporter.updateLastExportedPosition(1);

    // then
    assertThat(harness.getLastExportedRecordPosition()).isEqualTo(1);
  }

  @Test
  void shouldTrackRecordFilter() throws Exception {
    // given
    final var recordFilter =
        new RecordFilter() {
          @Override
          public boolean acceptType(final RecordType recordType) {
            return false;
          }

          @Override
          public boolean acceptValue(final ValueType valueType) {
            return false;
          }
        };

    harness.configure(ID, (Object) null);

    // when
    exporter.setRecordFilter(recordFilter);

    // then
    assertThat(harness.getRecordFilter()).isSameAs(recordFilter);
  }

  @Nested
  final class LifecycleTest {
    @Test
    void shouldNotConfigureIfOpened() throws Exception {
      // given
      harness.configure(ID, (Object) null);
      harness.open();

      // then
      assertThatCode(() -> harness.configure(ID, (Object) null))
          .isInstanceOf(IllegalStateException.class);
      assertThatCode(() -> harness.configure(ID, ignored -> null))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotOpenIfNotConfigured() {
      // then
      assertThatCode(harness::open).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotOpenIfOpened() throws Exception {
      // given
      harness.configure(ID, (Object) null);
      harness.open();

      // then
      assertThatCode(harness::open).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotCloseIfNotConfigured() {
      // then
      assertThatCode(harness::close).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotExportIfNotConfigured() {
      // given
      final Record<RecordValue> record = factory.generateRecord();

      // then
      assertThatCode(() -> harness.export(record)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldOpenOnExportIfNotOpened() throws Exception {
      // given
      final Record<RecordValue> record = factory.generateRecord();
      harness.configure(ID, (Object) null);

      // when
      harness.export(record);

      // then
      assertThat(exporter.getController()).isNotNull();
    }
  }
}
