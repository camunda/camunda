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
package io.camunda.process.test.impl.coverage;

import io.camunda.process.test.api.coverage.CoverageTestIdentifier;
import java.util.Objects;

public final class CoverageTestIdentifierImpl implements CoverageTestIdentifier {

  private final String suiteId;
  private final String suiteName;
  private final String runName;
  private final String displayName;

  public CoverageTestIdentifierImpl(
      final String suiteId,
      final String suiteName,
      final String runName,
      final String displayName) {
    this.suiteId = Objects.requireNonNull(suiteId, "suiteId");
    this.suiteName = Objects.requireNonNull(suiteName, "suiteName");
    this.runName = Objects.requireNonNull(runName, "runName");
    this.displayName = displayName;
  }

  /** Returns the class that identifies the suite: the enclosing class of a nested test class. */
  public static Class<?> suiteClassOf(final Class<?> testClass) {
    return testClass.getEnclosingClass() != null ? testClass.getEnclosingClass() : testClass;
  }

  @Override
  public String getSuiteId() {
    return suiteId;
  }

  @Override
  public String getSuiteName() {
    return suiteName;
  }

  @Override
  public String getRunName() {
    return runName;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }
}
