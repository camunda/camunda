/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
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
 * Compares hand-written {@code UnpackedObject}s ({@code impl=unpacked}) with plain classes using a
 * generated codec ({@code impl=generated}) that store the same bytes, for the access patterns of
 * state values: writing a value, reading into a fresh instance, reading into a reused instance and
 * deep copying into a reused instance ({@code BufferReader.copyFrom} serializes to a new array and
 * reads it back, the generated {@code copyFrom} copies field by field). {@code variable} is a hot
 * state value with a binary payload, {@code full} has every field type the generator supports. Run
 * with the {@code gc} profiler to see the allocation rate.
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
  public BufferReader readIntoReusedInstance(final BenchmarkState state) {
    final int i = state.nextIndex();
    final var result = state.reused;
    result.wrap(state.buffers[i], 0, state.lengths[i]);
    return result;
  }

  @Benchmark
  public BufferReader copyIntoReusedInstance(final BenchmarkState state) {
    state.copyAt.accept(state.nextIndex());
    return state.reused;
  }

  @State(Scope.Thread)
  public static class BenchmarkState {

    private static final int BUFFER_CAPACITY = 1024;
    private static final int BATCH_SIZE = 10_000;

    @Param({"variable", "full"})
    public String value;

    @Param({"unpacked", "generated"})
    public String impl;

    BufferWriter[] writers;
    UnsafeBuffer[] buffers;
    int[] lengths;
    Supplier<BufferReader> factory;
    BufferReader reused;
    IntConsumer copyAt;
    int index;

    @Setup
    public void setup() {
      writers = new BufferWriter[BATCH_SIZE];
      buffers = new UnsafeBuffer[BATCH_SIZE];
      lengths = new int[BATCH_SIZE];

      switch (value + "/" + impl) {
        case "variable/unpacked" ->
            init(
                UnpackedVariable::new,
                i -> new UnpackedVariable().setKey(100L + i).setValue(json(i)),
                UnpackedVariable::copyFrom);
        case "variable/generated" ->
            init(
                PojoVariable::new,
                i -> new PojoVariable().setKey(100L + i).setValue(json(i)),
                PojoVariable::copyFrom);
        case "full/unpacked" ->
            init(UnpackedFull::new, i -> new UnpackedFull().fill(i), UnpackedFull::copyFrom);
        case "full/generated" ->
            init(PojoFull::new, i -> new PojoFull().fill(i), PojoFull::copyFrom);
        default -> throw new IllegalArgumentException(value + "/" + impl);
      }
    }

    private <T extends BufferReader & BufferWriter> void init(
        final Supplier<T> newInstance, final IntFunction<T> filled, final BiConsumer<T, T> copy) {
      factory = newInstance::get;
      final T instance = newInstance.get();
      reused = instance;
      final Object[] sources = new Object[BATCH_SIZE];
      copyAt = i -> copy.accept(instance, castToT(sources[i]));
      for (int i = 0; i < BATCH_SIZE; i++) {
        final T writer = filled.apply(i);
        sources[i] = writer;
        writers[i] = writer;
        buffers[i] = new UnsafeBuffer(new byte[BUFFER_CAPACITY]);
        lengths[i] = writer.write(buffers[i], 0);
      }
    }

    @SuppressWarnings("unchecked")
    private static <T> T castToT(final Object o) {
      return (T) o;
    }

    int nextIndex() {
      final int current = index;
      index = (current + 1) % BATCH_SIZE;
      return current;
    }

    private static org.agrona.DirectBuffer json(final int seed) {
      return BufferUtil.wrapString("{\"orderId\":" + seed + "}");
    }
  }
}
