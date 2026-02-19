/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api.testCases;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Defines the source for test cases to be used in a parameterized JUnit test. The argument provider
 * reads the test cases from the given directory or from the given files.
 *
 * <p>Example usage:
 *
 * <pre>
 *   &#064;ParameterizedTest
 *   &#064;TestCaseSource
 *   void shouldPass(final TestCase testCase, final String filename) {
 *     // given - when - then
 *     testCaseRunner.run(testCase);
 *   }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(TestCaseArgumentProvider.class)
public @interface TestCaseSource {

  /**
   * The classpath directory to read the test cases files from. Defaults to "/test-cases".
   *
   * @return the directory path
   */
  String directory() default "/test-cases";

  /**
   * The names of the test cases files in the directory to read. If no files are given, all files in
   * the directory are read.
   *
   * @return the file names
   */
  String[] fileNames() default {};

  /**
   * The file extension of the test cases files to read. Only used if no specific files are given.
   * Defaults to ".json".
   *
   * @return the file extension
   */
  String fileExtension() default ".json";
}
