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
package io.camunda.process.test.api.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Defines the source for scenario test cases to be used in a parameterized JUnit test. The argument
 * provider reads the scenarios from the given directory or from the given files.
 *
 * <p>Example usage:
 *
 * <pre>
 *   &#064;ParameterizedTest
 *   &#064;TestScenarioSource
 *   void shouldPass(final TestCase testCase, final String scenarioFile) {
 *     // given - when - then
 *     testScenarioRunner.run(testCase);
 *   }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(TestScenarioArgumentProvider.class)
public @interface TestScenarioSource {

  /**
   * The classpath directory to read the scenario files from. Defaults to "/scenarios".
   *
   * @return the directory path
   */
  String directory() default "/scenarios";

  /**
   * The names of the scenario files in the directory to read. If no files are given, all files in
   * the directory are read.
   *
   * @return the file names
   */
  String[] fileNames() default {};

  /**
   * The file extension of the scenario files to read. Only used if no specific files are given.
   * Defaults to ".json".
   *
   * @return the file extension
   */
  String fileExtension() default ".json";
}
