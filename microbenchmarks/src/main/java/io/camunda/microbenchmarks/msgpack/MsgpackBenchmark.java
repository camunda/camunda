/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.TimeUnit;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms1G", "-Xmx1G"})
public class MsgpackBenchmark {

  public static void main(final String[] args) throws RunnerException {
    final Options options =
        new OptionsBuilder()
            .addProfiler("gc")
            .include(MsgpackBenchmark.class.getSimpleName())
            .build();
    new Runner(options).run();
  }

  @Benchmark
  public void serialize(final BenchmarkState state) {
    final int i = state.nextIndex();
    state.writePojos[i].write(state.writeBuffers[i], 0);
  }

  @Benchmark
  public void deserializeWithConstructor(final BenchmarkState state) {
    final Pojo result = new Pojo();
    final int i = state.nextIndex();
    result.wrap(state.writeBuffers[i]);
  }

  @Benchmark
  public void deserializeWithoutConstructor(final BenchmarkState state) {
    final int i = state.nextIndex();
    final Pojo result = state.readPojos[i];
    result.reset();
    result.wrap(state.writeBuffers[i]);
  }

  @State(Scope.Thread)
  public static class BenchmarkState {

    private static final int BUFFER_CAPACITY = 1024;

    @Param({"1000", "10000", "100000"})
    public int batchSize;

    UnsafeBuffer[] writeBuffers;
    Pojo[] readPojos;
    Pojo[] writePojos;
    int index = 0;
    private final Pojo.POJOEnum[] enumValues = Pojo.POJOEnum.values();

    @Setup
    public void setup() {
      writeBuffers = new UnsafeBuffer[batchSize];
      readPojos = new Pojo[batchSize];
      writePojos = new Pojo[batchSize];

      for (int i = 0; i < batchSize; i++) {
        writeBuffers[i] = new UnsafeBuffer(new byte[BUFFER_CAPACITY]);
        readPojos[i] = new Pojo();
        writePojos[i] = createPojo(i);
        writePojos[i].write(writeBuffers[i], 0);
      }
    }

    int nextIndex() {
      return index++ % batchSize;
    }

    Pojo createPojo(final int seed) {
      final Pojo pojo = new Pojo();
      pojo.setEnum(enumValues[seed % enumValues.length]);
      pojo.setLong(123_456_789L + seed);
      pojo.setInt(42 + seed);
      pojo.setString(BufferUtil.wrapString("benchmark test string " + seed));
      pojo.setBinary(BufferUtil.wrapString("binary data " + seed));
      pojo.nestedObject().setLong(987_654_321L + seed);
      return pojo;
    }
  }
}
