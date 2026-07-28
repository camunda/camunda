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
package io.camunda.runner;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Power-form configuration for {@link LiveBpmn#run(RunOptions)} / {@link LiveBpmn#run(RunOptions,
 * Cluster)}.
 *
 * <p>Use the static factory and chain mutators:
 *
 * <pre>{@code
 * LiveBpmn.createExecutableProcess("p")
 *     // ...
 *     .run(
 *         RunOptions.of(50)
 *             .pacing(Duration.ofMillis(100))
 *             .variables(i -> Map.of("orderId", "ORDER-" + i)),
 *         cluster);
 * }</pre>
 */
public final class RunOptions {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

  private final int instances;
  private Duration pacing; // null = eager (default)
  private Duration timeout = DEFAULT_TIMEOUT;
  private String[] extraTags = new String[0];
  private Map<String, Object> constantVariables;
  private IntFunction<Map<String, Object>> variableGenerator;

  private RunOptions(final int instances) {
    if (instances < 0) {
      throw new IllegalArgumentException("instances must be >= 0, got " + instances);
    }
    this.instances = instances;
  }

  public static RunOptions of(final int instances) {
    return new RunOptions(instances);
  }

  /** Spacing between {@code createInstance} calls. {@code null} or omitted = eager (no pacing). */
  public RunOptions pacing(final Duration between) {
    this.pacing = between;
    return this;
  }

  /**
   * Default timeout (currently not enforced by {@link Run#await(Duration)} which takes its own).
   */
  public RunOptions timeout(final Duration max) {
    if (max == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
    this.timeout = max;
    return this;
  }

  /** Extra tags to apply to every created instance, in addition to the auto-tags. */
  public RunOptions tags(final String... extraTags) {
    this.extraTags = extraTags == null ? new String[0] : extraTags.clone();
    return this;
  }

  /** Same variables on every instance. Last-write-wins versus {@link #variables(IntFunction)}. */
  public RunOptions variables(final Map<String, Object> vars) {
    this.constantVariables = vars;
    this.variableGenerator = null;
    return this;
  }

  /** Per-index variables generator. Last-write-wins versus {@link #variables(Map)}. */
  public RunOptions variables(final IntFunction<Map<String, Object>> generator) {
    this.variableGenerator = generator;
    this.constantVariables = null;
    return this;
  }

  // ---------------------------------------------------------------------------
  // Accessors used by the runner pipeline
  // ---------------------------------------------------------------------------

  public int instances() {
    return instances;
  }

  public Duration pacingOrNull() {
    return pacing;
  }

  public Duration timeout() {
    return timeout;
  }

  public String[] extraTags() {
    return extraTags.clone();
  }

  /** Resolves variables for the {@code i}-th instance. Never returns {@code null}. */
  public Map<String, Object> variablesFor(final int index) {
    if (variableGenerator != null) {
      final Map<String, Object> generated = variableGenerator.apply(index);
      return generated == null ? Collections.emptyMap() : generated;
    }
    if (constantVariables != null) {
      return constantVariables;
    }
    return Collections.emptyMap();
  }
}
