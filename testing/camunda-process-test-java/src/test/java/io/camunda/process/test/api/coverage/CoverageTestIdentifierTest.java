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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CoverageTestIdentifierTest {

  @Test
  void shouldCreateIdentifierFromSuiteAndRun() {
    // when
    final CoverageTestIdentifier identifier =
        CoverageTestIdentifier.of("suite-id", "Suite name", "run-1", "My test");

    // then
    assertThat(identifier.getSuiteId()).isEqualTo("suite-id");
    assertThat(identifier.getSuiteName()).isEqualTo("Suite name");
    assertThat(identifier.getRunName()).isEqualTo("run-1");
    assertThat(identifier.getDisplayName()).isEqualTo("My test");
  }

  @Test
  void shouldAllowMissingDisplayName() {
    // when
    final CoverageTestIdentifier identifier =
        CoverageTestIdentifier.of("suite-id", "Suite name", "run-1", null);

    // then
    assertThat(identifier.getDisplayName()).isNull();
  }

  @Test
  void shouldRejectMissingSuiteIdOrRunName() {
    assertThatThrownBy(() -> CoverageTestIdentifier.of(null, "Suite name", "run-1", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> CoverageTestIdentifier.of("suite-id", null, "run-1", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> CoverageTestIdentifier.of("suite-id", "Suite name", null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldUseTestClassAsSuite() {
    // when
    final CoverageTestIdentifier identifier =
        CoverageTestIdentifier.of(CoverageTestIdentifierTest.class, "shouldRun", "Run");

    // then
    assertThat(identifier.getSuiteId()).isEqualTo(CoverageTestIdentifierTest.class.getName());
    assertThat(identifier.getSuiteName()).isEqualTo("CoverageTestIdentifierTest");
    assertThat(identifier.getRunName()).isEqualTo("shouldRun");
    assertThat(identifier.getDisplayName()).isEqualTo("Run");
  }

  @Test
  void shouldUseEnclosingClassAsSuiteForNestedTestClass() {
    // when
    final CoverageTestIdentifier identifier =
        CoverageTestIdentifier.of(NestedTests.class, "NestedTests#shouldRun", null);

    // then
    assertThat(identifier.getSuiteId()).isEqualTo(CoverageTestIdentifierTest.class.getName());
    assertThat(identifier.getSuiteName()).isEqualTo("CoverageTestIdentifierTest");
    assertThat(identifier.getRunName()).isEqualTo("NestedTests#shouldRun");
  }

  @Test
  void shouldGroupNestedAndEnclosingTestClassInSameSuite() {
    // when
    final String suiteId = CoverageTestIdentifier.suiteIdOf(CoverageTestIdentifierTest.class);
    final String nestedSuiteId = CoverageTestIdentifier.suiteIdOf(NestedTests.class);

    // then
    assertThat(nestedSuiteId).isEqualTo(suiteId);
  }

  @SuppressWarnings("unused")
  static class NestedTests {}
}
