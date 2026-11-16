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
package io.camunda.process.test.impl.testCases;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.api.testCases.TestCaseReservationToken;
import org.junit.jupiter.api.Test;

public class TestRunContextTest {

  private static final String RUN_ID = "run-1";

  @Test
  void shouldReadEnabledFlagFromSystemProperty() {
    // given
    System.setProperty(TestRunContext.ENABLED_PROPERTY, "true");
    try {
      // when
      final TestRunContext runContext = TestRunContext.fromSystemProperties();

      // then
      assertThat(runContext.isEnabled()).isTrue();
      assertThat(runContext.getRunId()).isNotBlank();
    } finally {
      System.clearProperty(TestRunContext.ENABLED_PROPERTY);
    }
  }

  @Test
  void shouldBeDisabledByDefault() {
    // when
    final TestRunContext runContext = TestRunContext.fromSystemProperties();

    // then
    assertThat(runContext.isEnabled()).isFalse();
  }

  @Test
  void shouldResolvePlaceholderToFreshTokenPerRunContext() {
    // given
    final TestRunContext firstRun = TestRunContext.disabled();
    final TestRunContext secondRun = TestRunContext.disabled();

    // when
    final String first = firstRun.resolveReservationToken(TestCaseReservationToken.PLACEHOLDER);
    final String second = secondRun.resolveReservationToken(TestCaseReservationToken.PLACEHOLDER);

    // then
    assertThat(first).isNotEqualTo(TestCaseReservationToken.PLACEHOLDER).isNotBlank();
    assertThat(first)
        .describedAs("the same run resolves the placeholder to the same token")
        .isEqualTo(firstRun.resolveReservationToken(TestCaseReservationToken.PLACEHOLDER));
    assertThat(first).describedAs("a new run gets a fresh token").isNotEqualTo(second);
  }

  @Test
  void shouldKeepLiteralAndMissingTokensAsWritten() {
    // given
    final TestRunContext runContext = TestRunContext.disabled();

    // when/then
    assertThat(runContext.resolveReservationToken("literal")).isEqualTo("literal");
    assertThat(runContext.resolveReservationToken(null)).isNull();
  }

  @Test
  void shouldBuildReservedRunScopeTag() {
    // given
    final TestRunContext runContext = TestRunContext.enabled(RUN_ID);

    // when
    final String tag = runContext.getRunScopeTag();

    // then
    assertThat(tag).isEqualTo("camunda:runScope:" + RUN_ID);
  }
}
