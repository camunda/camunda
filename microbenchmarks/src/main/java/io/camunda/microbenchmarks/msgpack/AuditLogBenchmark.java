/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms1G", "-Xmx1G"})
public class AuditLogBenchmark {

  public static void main(final String[] args) throws RunnerException {
    final Options options =
        new OptionsBuilder()
            .addProfiler("gc")
            .include(AuditLogBenchmark.class.getSimpleName())
            .build();
    new Runner(options).run();
  }

  @Benchmark
  public AuditLogInfo serialize(final BenchmarkState state) {
    return AuditLogInfo.of(state.record);
  }

  @State(Scope.Thread)
  public static class BenchmarkState {

    private static final String INDEX_NAME = "test-audit-log";
    private static final String ENTITY_ID = "test-id";
    private static final String USERNAME = "test-user";
    private static final String TENANT = "test-tenant";
    int index = 0;
    private final List<Record> RECORDS = new ArrayList<>();
    private final Pojo.POJOEnum[] enumValues = Pojo.POJOEnum.values();
    private final ProtocolFactory factory = new ProtocolFactory();
    private io.camunda.zeebe.protocol.record.Record<RecordValue> record;

    public io.camunda.zeebe.protocol.record.Record<RecordValue> getRecord() {
      return record;
    }

    @Setup
    public void setup() {

      final ImmutableProcessInstanceModificationRecordValue value =
          ImmutableProcessInstanceModificationRecordValue.builder()
              .from(factory.generateObject(ImmutableProcessInstanceModificationRecordValue.class))
              .withTenantId(TENANT)
              .build();

      record =
          factory.generateRecord(
              ValueType.PROCESS_INSTANCE_MODIFICATION,
              r ->
                  r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                      .withValue(value)
                      .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, USERNAME)));
    }
  }
}
