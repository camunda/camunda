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

import io.camunda.process.test.api.testCases.TestCaseJobReservationToken;
import java.util.UUID;

/**
 * Holds the isolation state of a single test-case run. When isolation is enabled, every process
 * instance started during the run is tagged with a unique run ID (as a reserved instance tag) so
 * that later assertions and instructions can be scoped to that run's instances instead of selecting
 * the oldest instance of a process definition.
 *
 * <p>Enabled with the system property {@value #ENABLED_PROPERTY}. Off by default.
 */
public final class TestRunContext {

  /**
   * Reserved tag prefix that marks the run-scope tag. Must match the engine's {@code
   * TagUtil.RUN_SCOPE_TAG_PREFIX}. The engine treats only a tag with this prefix as the run scope
   * for signal broadcast (and, later, message correlation).
   */
  public static final String RUN_SCOPE_TAG_PREFIX = "camunda:runScope:";

  /** System property that enables run-ID based test isolation. */
  public static final String ENABLED_PROPERTY = "camunda.process-test.run-isolation.enabled";

  private final boolean enabled;
  private final String runId;
  private final String jobReservationToken = "cpt-" + UUID.randomUUID();

  TestRunContext(final boolean enabled, final String runId) {
    this.enabled = enabled;
    this.runId = runId;
  }

  /** Creates a context with a fresh run ID, reading the enabled flag from the system property. */
  public static TestRunContext fromSystemProperties() {
    return new TestRunContext(Boolean.getBoolean(ENABLED_PROPERTY), UUID.randomUUID().toString());
  }

  /** Creates a disabled context. Started instances are not tagged and selectors are not scoped. */
  public static TestRunContext disabled() {
    return new TestRunContext(false, UUID.randomUUID().toString());
  }

  /** Creates an enabled context with the given run ID. */
  public static TestRunContext enabled(final String runId) {
    return new TestRunContext(true, runId);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getRunId() {
    return runId;
  }

  /**
   * Resolves the job reservation token a test case instruction names. The placeholder {@link
   * TestCaseJobReservationToken#PLACEHOLDER} stands for this run's fresh token, so a saved test
   * case never contains a real token; any other value is used as written.
   *
   * @param token the token as written in the test case, or {@code null} if the instruction has none
   * @return the token to send, or {@code null} if the instruction has none
   */
  public String resolveJobReservationToken(final String token) {
    return TestCaseJobReservationToken.PLACEHOLDER.equals(token) ? jobReservationToken : token;
  }

  /** Returns the reserved run-scope tag ({@code camunda:runScope:<runId>}) for this run. */
  public String getRunScopeTag() {
    return RUN_SCOPE_TAG_PREFIX + runId;
  }
}
