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
package io.camunda.zeebe.exporter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.slf4j.Logger;

public final class ExporterTest {

  @Test
  public void shouldAllowExporterToThrowCheckedExceptions() {
    // given
    final Exception expectedException = new IOException("catch me");

    final Exporter exporter =
        new Exporter() {
          @Override
          public void configure(final Context context) throws Exception {
            throw expectedException;
          }

          @Override
          public void export(final Record<?> record) {}
        };

    // then
    assertThatThrownBy(() -> exporter.configure(null)).isEqualTo(expectedException);
  }

  @Test
  public void shouldAllowFilteringOnIntent() {
    // given
    final TestExporterContainer exporter = new TestExporterContainer();
    final var record = new TestMessageRecord(MessageIntent.EXPIRED);
    exporter.exportRecord(record);

    // then
    assertThat(exporter.isExported(record)).isTrue();
  }

  @Test
  public void shouldRejectDisallowedIntent() {
    // given
    final TestExporterContainer exporter = new TestExporterContainer();
    final var record = new TestMessageRecord(MessageIntent.PUBLISHED);

    // then
    assertThat(exporter.isExported(record)).isFalse();
  }

  private static final class TestExporterContainer {
    private final MessageExpiredSupportingExporter exporter;
    private final TestContext context;

    public TestExporterContainer() {
      context = new TestContext();
      exporter = new MessageExpiredSupportingExporter();
      exporter.configure(context);
    }

    public void exportRecord(final Record<?> record) {
      if (context.getFilter().acceptValue(record.getValueType())
          && context.getFilter().acceptType(record.getRecordType())
          && context.getFilter().acceptIntent(record.getIntent())) {
        exporter.export(record);
      }
    }

    public boolean isExported(final Record<?> record) {
      return exporter.isExported(record);
    }
  }

  private static final class MessageExpiredSupportingExporter implements Exporter {
    private final List<Record<?>> records = new ArrayList<>();

    public boolean isExported(final Record<?> record) {
      return records.contains(record);
    }

    @Override
    public void configure(final Context context) {
      context.setFilter(new MessageExpiredFilter());
    }

    @Override
    public void export(final Record<?> record) {
      records.add(record);
    }
  }

  private static final class TestContext implements Context {
    private RecordFilter filter;

    @Override
    public MeterRegistry getMeterRegistry() {
      return null;
    }

    @Override
    public Logger getLogger() {
      return null;
    }

    @Override
    public InstantSource clock() {
      return null;
    }

    @Override
    public Configuration getConfiguration() {
      return null;
    }

    @Override
    public int getPartitionId() {
      return 0;
    }

    public RecordFilter getFilter() {
      return filter;
    }

    @Override
    public void setFilter(final RecordFilter filter) {
      this.filter = filter;
    }
  }

  private static final class MessageExpiredFilter implements RecordFilter {
    @Override
    public boolean acceptType(final RecordType recordType) {
      return true;
    }

    @Override
    public boolean acceptValue(final ValueType valueType) {
      return true;
    }

    @Override
    public boolean acceptIntent(final Intent intent) {
      if (intent instanceof MessageIntent) {
        return intent == MessageIntent.EXPIRED;
      }
      return true;
    }
  }

  private record TestMessageRecord(Intent intent) implements Record<MessageRecordValue> {
    @Override
    public long getPosition() {
      return 0;
    }

    @Override
    public long getSourceRecordPosition() {
      return 0;
    }

    @Override
    public long getKey() {
      return 0;
    }

    @Override
    public long getTimestamp() {
      return 0;
    }

    @Override
    public Intent getIntent() {
      return intent;
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
      return "";
    }

    @Override
    public String getBrokerVersion() {
      return "";
    }

    @Override
    public Map<String, Object> getAuthorizations() {
      return Map.of();
    }

    @Override
    public Agent getAgent() {
      return null;
    }

    @Override
    public int getRecordVersion() {
      return 0;
    }

    @Override
    public ValueType getValueType() {
      return null;
    }

    @Override
    public MessageRecordValue getValue() {
      return null;
    }

    @Override
    public long getOperationReference() {
      return 0;
    }

    @Override
    public long getBatchOperationReference() {
      return 0;
    }
  }
}
