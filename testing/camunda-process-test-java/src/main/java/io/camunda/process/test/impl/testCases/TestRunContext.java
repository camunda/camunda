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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the isolation state of a single test-case run. When isolation is enabled, every process
 * instance started during the run is tagged with a unique run ID (as a process variable) so that
 * later assertions and instructions can be scoped to that run's instances instead of selecting the
 * oldest instance of a process definition.
 *
 * <p>Enabled with the system property {@value #ENABLED_PROPERTY}. Off by default.
 */
public final class TestRunContext {

  /** Variable name under which the run ID is stored on every started instance. */
  public static final String RUN_ID_VARIABLE_NAME = "__camundaProcessTestRunId";

  /** System property that enables run-ID based test isolation. */
  public static final String ENABLED_PROPERTY = "camunda.process-test.run-isolation.enabled";

  private final boolean enabled;
  private final String runId;

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
   * Returns the given variables with the run ID added, or the variables unchanged when isolation is
   * disabled. The returned map is a copy; the input is never mutated.
   */
  public Map<String, Object> applyRunId(final Map<String, Object> variables) {
    if (!enabled) {
      return variables;
    }
    final Map<String, Object> merged =
        new LinkedHashMap<>(variables == null ? Collections.emptyMap() : variables);
    merged.put(RUN_ID_VARIABLE_NAME, runId);
    return merged;
  }
}
