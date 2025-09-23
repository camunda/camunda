/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.perf;

import io.camunda.zeebe.msgpack.util.EnumParser;
import io.camunda.zeebe.msgpack.util.TrieEnumParser;
import io.camunda.zeebe.msgpack.util.ZeroAllocEnumParser;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/*
 * Measures enum parser performance and is compatible with allocation rate profiling.
 * To measure allocation rate, run with JMH's -prof gc option, e.g.:
 *   java -jar target/benchmarks.jar -prof gc
 */
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 3, time = 3)
@Fork(1)
public class EnumParserBenchmark {

  // Parsers
  private EnumParser<SmallEnum> smallTrieParser;
  private EnumParser<MediumEnum> mediumTrieParser;
  private EnumParser<LargeEnum> largeTrieParser;
  // Simple parsers (old implementation)
  private SimpleEnumParser<SmallEnum> smallSimpleParser;
  private SimpleEnumParser<MediumEnum> mediumSimpleParser;
  private SimpleEnumParser<LargeEnum> largeSimpleParser;
  // Zero-allocation enum parsers
  private ZeroAllocEnumParser<SmallEnum> smallZeroAllocParser;
  private ZeroAllocEnumParser<MediumEnum> mediumZeroAllocParser;
  private ZeroAllocEnumParser<LargeEnum> largeZeroAllocParser;
  // Parsers for LargeEnumNoPrefix
  private EnumParser<LargeEnumNoPrefix> largeNoPrefixTrieParser;
  private SimpleEnumParser<LargeEnumNoPrefix> largeNoPrefixSimpleParser;
  private ZeroAllocEnumParser<LargeEnumNoPrefix> largeNoPrefixZeroAllocParser;
  // Test data
  private DirectBuffer[] smallEnumBuffers;
  private DirectBuffer[] mediumEnumBuffers;
  private DirectBuffer[] largeEnumBuffers;
  private DirectBuffer[] largeNoPrefixBuffers;
  private DirectBuffer[] invalidBuffers;
  // Random index for test data selection
  private int currentIndex = 0;

  @Setup
  public void setup() {
    // Initialize optimized parsers
    smallTrieParser = new TrieEnumParser<>(SmallEnum.class);
    mediumTrieParser = new TrieEnumParser<>(MediumEnum.class);
    largeTrieParser = new TrieEnumParser<>(LargeEnum.class);

    // Initialize simple parsers
    smallSimpleParser = new SimpleEnumParser<>(SmallEnum.class);
    mediumSimpleParser = new SimpleEnumParser<>(MediumEnum.class);
    largeSimpleParser = new SimpleEnumParser<>(LargeEnum.class);

    // Initialize zero-allocation parsers
    smallZeroAllocParser = new ZeroAllocEnumParser<>(SmallEnum.class);
    mediumZeroAllocParser = new ZeroAllocEnumParser<>(MediumEnum.class);
    largeZeroAllocParser = new ZeroAllocEnumParser<>(LargeEnum.class);

    // Initialize LargeEnumNoPrefix parsers
    largeNoPrefixTrieParser = new TrieEnumParser<>(LargeEnumNoPrefix.class);
    largeNoPrefixSimpleParser = new SimpleEnumParser<>(LargeEnumNoPrefix.class);
    largeNoPrefixZeroAllocParser = new ZeroAllocEnumParser<>(LargeEnumNoPrefix.class);

    // Prepare test data
    setupTestBuffers();
  }

  private void setupTestBuffers() {
    // Small enum buffers
    final SmallEnum[] smallValues = SmallEnum.values();
    smallEnumBuffers = new DirectBuffer[smallValues.length];
    for (int i = 0; i < smallValues.length; i++) {
      final byte[] bytes = smallValues[i].name().getBytes(StandardCharsets.US_ASCII);
      smallEnumBuffers[i] = new UnsafeBuffer(bytes);
    }

    // Medium enum buffers
    final MediumEnum[] mediumValues = MediumEnum.values();
    mediumEnumBuffers = new DirectBuffer[mediumValues.length];
    for (int i = 0; i < mediumValues.length; i++) {
      final byte[] bytes = mediumValues[i].name().getBytes(StandardCharsets.US_ASCII);
      mediumEnumBuffers[i] = new UnsafeBuffer(bytes);
    }

    // Large enum buffers
    final LargeEnum[] largeValues = LargeEnum.values();
    largeEnumBuffers = new DirectBuffer[largeValues.length];
    for (int i = 0; i < largeValues.length; i++) {
      final byte[] bytes = largeValues[i].name().getBytes(StandardCharsets.US_ASCII);
      largeEnumBuffers[i] = new UnsafeBuffer(bytes);
    }

    // LargeEnumNoPrefix buffers
    final LargeEnumNoPrefix[] largeNoPrefixValues = LargeEnumNoPrefix.values();
    largeNoPrefixBuffers = new DirectBuffer[largeNoPrefixValues.length];
    for (int i = 0; i < largeNoPrefixValues.length; i++) {
      final byte[] bytes = largeNoPrefixValues[i].name().getBytes(StandardCharsets.US_ASCII);
      largeNoPrefixBuffers[i] = new UnsafeBuffer(bytes);
    }

    // Invalid enum buffers
    final String[] invalidNames = {
      "INVALID", "NOT_FOUND", "WRONG_NAME", "FAKE_ENUM", "DOES_NOT_EXIST"
    };
    invalidBuffers = new DirectBuffer[invalidNames.length];
    for (int i = 0; i < invalidNames.length; i++) {
      final byte[] bytes = invalidNames[i].getBytes(StandardCharsets.US_ASCII);
      invalidBuffers[i] = new UnsafeBuffer(bytes);
    }
  }

  // Small enum benchmarks
  @Benchmark
  public void smallEnumTrie(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = smallEnumBuffers[currentIndex % smallEnumBuffers.length];
    currentIndex++;
    final SmallEnum result = smallTrieParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  @Benchmark
  public void smallEnumSimple(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = smallEnumBuffers[currentIndex % smallEnumBuffers.length];
    currentIndex++;
    final SmallEnum result = smallSimpleParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  @Benchmark
  public void smallEnumZeroAlloc(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = smallEnumBuffers[currentIndex % smallEnumBuffers.length];
    currentIndex++;
    final SmallEnum result = smallZeroAllocParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  // Medium enum benchmarks
  @Benchmark
  public void mediumEnumTrie(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = mediumEnumBuffers[currentIndex % mediumEnumBuffers.length];
    currentIndex++;
    final MediumEnum result = mediumTrieParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  @Benchmark
  public void mediumEnumSimple(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = mediumEnumBuffers[currentIndex % mediumEnumBuffers.length];
    currentIndex++;
    final MediumEnum result = mediumSimpleParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  @Benchmark
  public void mediumEnumZeroAlloc(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = mediumEnumBuffers[currentIndex % mediumEnumBuffers.length];
    currentIndex++;
    final MediumEnum result = mediumZeroAllocParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  // Large enum benchmarks
  @Benchmark
  public void largeEnumTrie(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = largeEnumBuffers[currentIndex % largeEnumBuffers.length];
    currentIndex++;
    final LargeEnum result = largeTrieParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  @Benchmark
  public void largeEnumSimple(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = largeEnumBuffers[currentIndex % largeEnumBuffers.length];
    currentIndex++;
    final LargeEnum result = largeSimpleParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  @Benchmark
  public void largeEnumZeroAlloc(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = largeEnumBuffers[currentIndex % largeEnumBuffers.length];
    currentIndex++;
    final LargeEnum result = largeZeroAllocParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  // LargeEnumNoPrefix benchmarks
  @Benchmark
  public void largeEnumNoPrefixTrie(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = largeNoPrefixBuffers[currentIndex % largeNoPrefixBuffers.length];
    currentIndex++;
    final LargeEnumNoPrefix result = largeNoPrefixTrieParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  @Benchmark
  public void largeEnumNoPrefixSimple(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = largeNoPrefixBuffers[currentIndex % largeNoPrefixBuffers.length];
    currentIndex++;
    final LargeEnumNoPrefix result = largeNoPrefixSimpleParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  @Benchmark
  public void largeEnumNoPrefixZeroAlloc(final org.openjdk.jmh.infra.Blackhole blackhole) {
    final DirectBuffer buffer = largeNoPrefixBuffers[currentIndex % largeNoPrefixBuffers.length];
    currentIndex++;
    final LargeEnumNoPrefix result =
        largeNoPrefixZeroAllocParser.parse(buffer, 0, buffer.capacity());
    blackhole.consume(result);
  }

  //  // Invalid enum benchmarks (worst case for simple parser)
  //  @Benchmark
  //  public void invalidEnumTrie(final org.openjdk.jmh.infra.Blackhole blackhole) {
  //    final DirectBuffer buffer = invalidBuffers[currentIndex % invalidBuffers.length];
  //    currentIndex++;
  //    final SmallEnum result = smallTrieParser.parse(buffer, 0, buffer.capacity());
  //    blackhole.consume(result);
  //  }
  //
  //  @Benchmark
  //  public void invalidEnumSimple(final org.openjdk.jmh.infra.Blackhole blackhole) {
  //    final DirectBuffer buffer = invalidBuffers[currentIndex % invalidBuffers.length];
  //    currentIndex++;
  //    final SmallEnum result = smallSimpleParser.parse(buffer, 0, buffer.capacity());
  //    blackhole.consume(result);
  //  }
  //
  //  @Benchmark
  //  public void invalidEnumZeroAlloc(final org.openjdk.jmh.infra.Blackhole blackhole) {
  //    final DirectBuffer buffer = invalidBuffers[currentIndex % invalidBuffers.length];
  //    currentIndex++;
  //    final SmallEnum result = smallZeroAllocParser.parse(buffer, 0, buffer.capacity());
  //    blackhole.consume(result);
  //  }

  // Benchmarks with offset (to test real-world scenarios)
  //  @Benchmark
  //  public void mediumEnumTrieWithOffset(org.openjdk.jmh.infra.Blackhole blackhole) {
  //    final DirectBuffer buffer = mediumEnumBuffers[currentIndex % mediumEnumBuffers.length];
  //    currentIndex++;
  //    // Simulate parsing from middle of buffer
  //    final String prefix = "PREFIX_";
  //    final byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);
  //    final byte[] originalBytes = new byte[buffer.capacity()];
  //    buffer.getBytes(0, originalBytes);
  //
  //    final byte[] combinedBytes = new byte[prefixBytes.length + originalBytes.length];
  //    System.arraycopy(prefixBytes, 0, combinedBytes, 0, prefixBytes.length);
  //    System.arraycopy(originalBytes, 0, combinedBytes, prefixBytes.length, originalBytes.length);
  //
  //    final DirectBuffer combinedBuffer = new UnsafeBuffer(combinedBytes);
  //    MediumEnum result = mediumTrieParser.parse(combinedBuffer, prefixBytes.length,
  // originalBytes.length);
  //    blackhole.consume(result);
  //  }
  //
  //  @Benchmark
  //  public void mediumEnumSimpleWithOffset(org.openjdk.jmh.infra.Blackhole blackhole) {
  //    final DirectBuffer buffer = mediumEnumBuffers[currentIndex % mediumEnumBuffers.length];
  //    currentIndex++;
  //    // Simple parser needs to create substring, so it's less efficient with offsets
  //    final String prefix = "PREFIX_";
  //    final byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);
  //    final byte[] originalBytes = new byte[buffer.capacity()];
  //    buffer.getBytes(0, originalBytes);
  //
  //    final byte[] combinedBytes = new byte[prefixBytes.length + originalBytes.length];
  //    System.arraycopy(prefixBytes, 0, combinedBytes, 0, prefixBytes.length);
  //    System.arraycopy(originalBytes, 0, combinedBytes, prefixBytes.length, originalBytes.length);
  //
  //    final DirectBuffer combinedBuffer = new UnsafeBuffer(combinedBytes);
  //    MediumEnum result = mediumSimpleParser.parse(combinedBuffer, prefixBytes.length,
  // originalBytes.length);
  //    blackhole.consume(result);
  //  }

  public static void main(final String[] args) throws RunnerException {
    final Options opt =
        new OptionsBuilder()
            .include(EnumParserBenchmark.class.getSimpleName())
            .addProfiler("gc")
            // To measure allocation rate, add .addProfiler("gc") or run with -prof gc
            // Example: java -jar target/benchmarks.jar -prof gc
            .build();

    new Runner(opt).run();
  }

  // Simple enum parser (old implementation)
  private static class SimpleEnumParser<E extends Enum<E>> {
    private final Class<E> enumClass;

    public SimpleEnumParser(final Class<E> enumClass) {
      this.enumClass = enumClass;
    }

    public E parse(final DirectBuffer buffer, final int offset, final int length) {
      try {
        // This is the allocation-heavy approach
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        final String enumName = new String(bytes, StandardCharsets.US_ASCII);
        return Enum.valueOf(enumClass, enumName);
      } catch (final IllegalArgumentException e) {
        return null; // Invalid enum name
      }
    }
  }

  // Test enums of different sizes
  enum SmallEnum {
    RED,
    GREEN,
    BLUE
  }

  enum MediumEnum {
    ALPHA,
    BETA,
    GAMMA,
    DELTA,
    EPSILON,
    ZETA,
    ETA,
    THETA,
    IOTA,
    KAPPA,
    LAMBDA,
    MU,
    NU,
    XI,
    OMICRON,
    PI,
    RHO,
    SIGMA,
    TAU,
    UPSILON
  }

  enum LargeEnum {
    VALUE_001,
    VALUE_002,
    VALUE_003,
    VALUE_004,
    VALUE_005,
    VALUE_006,
    VALUE_007,
    VALUE_008,
    VALUE_009,
    VALUE_010,
    VALUE_011,
    VALUE_012,
    VALUE_013,
    VALUE_014,
    VALUE_015,
    VALUE_016,
    VALUE_017,
    VALUE_018,
    VALUE_019,
    VALUE_020,
    VALUE_021,
    VALUE_022,
    VALUE_023,
    VALUE_024,
    VALUE_025,
    VALUE_026,
    VALUE_027,
    VALUE_028,
    VALUE_029,
    VALUE_030,
    LONG_ENUM_NAME_WITH_MANY_UNDERSCORES_AND_NUMBERS_123,
    ANOTHER_VERY_LONG_ENUM_NAME_FOR_TESTING_PERFORMANCE,
    SHORT_A,
    SHORT_B,
    SHORT_C,
    SHORT_D,
    SHORT_E
  }

  enum LargeEnumNoPrefix {
    ALPHA,
    BETA,
    GAMMA,
    DELTA,
    EPSILON,
    ZETA,
    THETA,
    IOTA,
    KAPPA,
    LAMBDA,
    MU,
    NU,
    XI,
    OMICRON,
    PI,
    RHO,
    SIGMA,
    TAU,
    UPSILON,
    OMEGA,
    FOOBAR,
    BARFOO,
    RANDOM1,
    RANDOM2,
    RANDOM3,
    RANDOM4,
    RANDOM5,
    RANDOM6,
    RANDOM7,
    RANDOM8,
    RANDOM9,
    RANDOM10,
    LONG_ENUM_NAME_WITH_MANY_UNDERSCORES_AND_NUMBERS_123,
    ANOTHER_VERY_LONG_ENUM_NAME_FOR_TESTING_PERFORMANCE,
    SHORT_A,
    SHORT_B,
    SHORT_C,
    SHORT_D,
    SHORT_E
  }
}
