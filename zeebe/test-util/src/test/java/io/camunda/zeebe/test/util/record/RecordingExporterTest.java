/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public final class RecordingExporterTest {

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
    public long getKey() {
      return 0;
    }

    @Override
    public long getTimestamp() {
      return -1;
    }

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
    public String getBrokerVersion() {
      return null;
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
      return 1;
    }

    @Override
    public ValueType getValueType() {
      return VALUE_TYPE;
    }

    @Override
    public TestValue getValue() {
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

    @Override
    public Record<TestValue> copyOf() {
      return this;
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
