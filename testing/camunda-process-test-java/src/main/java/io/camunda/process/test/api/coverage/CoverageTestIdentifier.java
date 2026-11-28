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
package io.camunda.process.test.api.coverage;

import io.camunda.process.test.impl.coverage.CoverageTestIdentifierImpl;

/**
 * Identifies a test run for which the process coverage is collected.
 *
 * <p>A test run belongs to a suite. The coverage report groups the test runs by their suite.
 *
 * @see CoverageCollector
 */
public interface CoverageTestIdentifier {

  /**
   * Creates an identifier for a test run of the given suite.
   *
   * @param suiteId the unique id of the suite. All test runs with the same suite id are grouped in
   *     the same suite
   * @param suiteName the name of the suite, to display in the report
   * @param runName the name of the test run, to display in the report
   * @param displayName an optional custom name of the test run to display in the report, or {@code
   *     null} to display the run name
   * @return the identifier
   */
  static CoverageTestIdentifier of(
      final String suiteId,
      final String suiteName,
      final String runName,
      final String displayName) {
    return new CoverageTestIdentifierImpl(suiteId, suiteName, runName, displayName);
  }

  /**
   * Creates an identifier for a test run of the suite of the given test class. The suite is the
   * test class, or the enclosing class if the test class is nested. The suite id is the name of the
   * class, and the suite name is its simple name.
   *
   * @param testClass the class of the test, or a nested test class
   * @param runName the name of the test run, to display in the report
   * @param displayName an optional custom name of the test run to display in the report, or {@code
   *     null} to display the run name
   * @return the identifier
   */
  static CoverageTestIdentifier of(
      final Class<?> testClass, final String runName, final String displayName) {
    final Class<?> suiteClass = CoverageTestIdentifierImpl.suiteClassOf(testClass);
    return of(suiteClass.getName(), suiteClass.getSimpleName(), runName, displayName);
  }

  /**
   * Returns the suite id of the given test class, as {@link #of(Class, String, String)} uses it.
   * Use it to generate the report of the suite with {@link
   * CoverageCollector#generateReport(String)}.
   *
   * @param testClass the class of the test, or a nested test class
   * @return the suite id
   */
  static String suiteIdOf(final Class<?> testClass) {
    return CoverageTestIdentifierImpl.suiteClassOf(testClass).getName();
  }

  /** Returns the unique id of the suite that the test run belongs to. */
  String getSuiteId();

  /** Returns the name of the suite, to display in the report. */
  String getSuiteName();

  /** Returns the name of the test run, to display in the report. */
  String getRunName();

  /**
   * Returns the custom name of the test run to display in the report, or {@code null} if it is not
   * set.
   */
  String getDisplayName();
}
