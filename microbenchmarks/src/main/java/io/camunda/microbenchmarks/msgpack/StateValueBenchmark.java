/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.ElementInstanceV1;
import io.camunda.zeebe.engine.state.variable.VariableInstance;
import io.camunda.zeebe.engine.state.variable.VariableInstanceV1;
import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Compares the frozen UnpackedObject based state classes ({@code impl=v1}) with their generated
 * codec replacements ({@code impl=generated}), for the access patterns of the state: writing a
 * value, reading into a fresh instance and reading into a reused instance.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms1G", "-Xmx1G"})
public class StateValueBenchmark {

  public static void main(final String[] args) throws RunnerException {
    final Options options =
        new OptionsBuilder()
            .addProfiler("gc")
            .include(StateValueBenchmark.class.getSimpleName())
            .build();
    new Runner(options).run();
  }

  @Benchmark
  public int write(final BenchmarkState state) {
    final int i = state.nextIndex();
    return state.writers[i].write(state.buffers[i], 0);
  }

  @Benchmark
  public BufferReader readIntoFreshInstance(final BenchmarkState state) {
    final int i = state.nextIndex();
    final var result = state.factory.get();
    result.wrap(state.buffers[i], 0, state.lengths[i]);
    return result;
  }

  @Benchmark
  public BufferReader resetAndReadIntoReusedInstance(final BenchmarkState state) {
    final int i = state.nextIndex();
    final var result = state.reused;
    state.reusedReset.reset();
    result.wrap(state.buffers[i], 0, state.lengths[i]);
    return result;
  }

  @State(Scope.Thread)
  public static class BenchmarkState {

    private static final int BUFFER_CAPACITY = 1024;
    private static final int BATCH_SIZE = 10_000;

    @Param({"variable", "element", "record"})
    public String value;

    @Param({"v1", "generated"})
    public String impl;

    BufferWriter[] writers;
    UnsafeBuffer[] buffers;
    int[] lengths;
    Supplier<BufferReader> factory;
    BufferReader reused;
    Recyclable reusedReset;
    int index;

    @Setup
    public void setup() {
      writers = new BufferWriter[BATCH_SIZE];
      buffers = new UnsafeBuffer[BATCH_SIZE];
      lengths = new int[BATCH_SIZE];

      switch (value + "/" + impl) {
        case "variable/v1" -> init(VariableInstanceV1::new, i -> variableV1(i));
        case "variable/generated" -> init(VariableInstance::new, i -> variable(i));
        case "element/v1" -> init(ElementInstanceV1::new, i -> elementV1(i));
        case "record/v1", "record/generated" -> init(ProcessInstanceRecord::new, i -> record(i));
        case "element/generated" -> init(ElementInstance::new, i -> element(i));
        default -> throw new IllegalArgumentException(value + "/" + impl);
      }
    }

    private <T extends BufferReader & BufferWriter & Recyclable> void init(
        final Supplier<T> newInstance, final IntFunction<T> filled) {
      factory = newInstance::get;
      final T instance = newInstance.get();
      reused = instance;
      reusedReset = instance;
      for (int i = 0; i < BATCH_SIZE; i++) {
        final T writer = filled.apply(i);
        writers[i] = writer;
        buffers[i] = new UnsafeBuffer(new byte[BUFFER_CAPACITY]);
        lengths[i] = writer.write(buffers[i], 0);
      }
    }

    int nextIndex() {
      return index++ % BATCH_SIZE;
    }

    private static ProcessInstanceRecord record(final int seed) {
      return new ProcessInstanceRecord()
          .setBpmnProcessId("process-" + seed)
          .setElementId("service-task-" + seed)
          .setBpmnElementType(BpmnElementType.SERVICE_TASK)
          .setProcessDefinitionKey(2_251_799_813_685_249L + seed)
          .setProcessInstanceKey(2_251_799_813_685_300L + seed)
          .setFlowScopeKey(2_251_799_813_685_300L + seed)
          .setVersion(1 + seed % 5);
    }

    private static DirectBuffer json(final int seed) {
      return BufferUtil.wrapString("{\"orderId\":" + seed + "}");
    }

    private static VariableInstance variable(final int seed) {
      return new VariableInstance()
          .setKey(100L + seed)
          .setValue(json(seed), 0, json(seed).capacity());
    }

    private static VariableInstanceV1 variableV1(final int seed) {
      return new VariableInstanceV1()
          .setKey(100L + seed)
          .setValue(json(seed), 0, json(seed).capacity());
    }

    private static ElementInstance element(final int seed) {
      return new ElementInstance(
          100L + seed, ProcessInstanceIntent.ELEMENT_ACTIVATED, record(seed));
    }

    private static ElementInstanceV1 elementV1(final int seed) {
      return new ElementInstanceV1(
          100L + seed, ProcessInstanceIntent.ELEMENT_ACTIVATED, record(seed));
    }
  }
}
