/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.msgpack.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
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

@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
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
    state.writePojoBatch[i].write(state.writeBuffers[i], 0);
  }

  @Benchmark
  public void deserializeWithConstructor(final BenchmarkState state) {
    final Pojo result = new Pojo();
    final int i = state.nextIndex();
    result.wrap(state.writeBuffers[i]);
  }

  @Benchmark
  public void serialize(final BenchmarkState state) {
    state.pojo.write(state.writeBuffer, 0);
  }

  @Benchmark
  public Pojo deserializeWithConstructor(final BenchmarkState state) {
    final Pojo result = new Pojo();
    result.wrap(state.readBuffer);
    return result;
  }

  @Benchmark
  public Pojo deserializeWithoutConstructor(final BenchmarkState state) {
    state.pojoDeser.wrap(state.readBuffer);
    return state.pojoDeser;
  }

  @Benchmark
  public Map<String, Object> deserializeasMap(final BenchmarkState state) {
    return MsgPackUtil.asMap(state.readBuffer);
  }

  @State(Scope.Thread)
  public static class BenchmarkState {

    public Pojo pojoDeser;
    Pojo pojo;
    UnsafeBuffer writeBuffer;
    UnsafeBuffer readBuffer;

    @Setup
    public void setup() {
      // Initialize the Pojo with representative data
      pojo = new Pojo();
      pojo.setEnum(Pojo.POJOEnum.BAR);
      pojo.setLong(123456789L);
      pojo.setInt(42);
      pojo.setString(BufferUtil.wrapString("benchmark test string"));
      pojo.setBinary(BufferUtil.wrapString("binary data"));
      pojo.nestedObject().setLong(987654321L);

      // Allocate buffers for serialization/deserialization
      writeBuffer = new UnsafeBuffer(new byte[1024]);
      readBuffer = new UnsafeBuffer(new byte[1024]);

      // Pre-serialize the object for deserialization benchmark
      pojo.write(readBuffer, 0);
      pojoDeser = new Pojo();
    }
  }
}
