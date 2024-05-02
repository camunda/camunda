/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A JUnit 5 extension to run JMH test cases as part of a JUnit pipeline. These tests are tagged
 * with the {@code performance} tag, which are excluded from the main pipeline by default.
 *
 * <p>You can use it like this:
 *
 * <pre>{@code
 * final class MyTest {
 *   &#64;JMHTest("measurePropertyFoo", jmhClass = MyBenchmarkClass.class)
 *   void shouldBenchmarkFoo(final @TempDir Path tempDir, final JMHTestCase testCase) {
 *     // given - when - then
 *     testCase.withParam("tempDir", tempDir).run().isWithinDeviation(6.32, 0.01);
 *   }
 * }
 * }</pre>
 *
 * Or you can use it directly from your JMH class:
 *
 * <pre>{@code
 * &#64;Warmup(iterations = 50, time = 1)
 * &#64;Measurement(iterations = 25, time = 1)
 * &#64;Fork(
 *     value = 1,
 *     jvmArgs = {"-Xmx4g", "-Xms4g"})
 * &#64;BenchmarkMode(Mode.Throughput)
 * &#64;OutputTimeUnit(TimeUnit.SECONDS)
 * &#64;State(org.openjdk.jmh.annotations.Scope.Benchmark)
 * final class MyBenchmarkTest {
 *   &#64;JMHTest("measurePropertyFoo")
 *   void shouldBenchmarkFoo(final JMHTestCase testCase) {
 *     // given - when - then
 *     testCase.run().isWithinDeviation(6.32, 0.01);
 *   }
 *
 *   &#64;Benchmark
 *   public void measurePropertyFoo() {
 *     // do stuff
 *   }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(JMHTestExtension.class)
@Documented
@Inherited
@Tag("performance")
@Test
public @interface JMHTest {

  /** The name of the benchmark to run */
  String value();

  /**
   * The class containing JMH benchmarks; if omitted, defaults to the class containing the annotated
   * method
   */
  Class<?> jmhClass() default None.class;

  final class None {}
}
