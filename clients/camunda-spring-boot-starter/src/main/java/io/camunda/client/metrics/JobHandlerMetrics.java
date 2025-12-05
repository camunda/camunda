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
package io.camunda.client.metrics;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import io.camunda.client.metrics.MetricsRecorder.TimerMetricsContext;
import java.util.Map;

/**
 * Defines standard metrics constants for job handlers, including metric names, actions, and tags.
 *
 * <p>These constants are used for recording and tagging job handler metrics such as invocations,
 * execution times, and job actions (activated, completed, failed, BPMN error).
 */
public final class JobHandlerMetrics {
  private JobHandlerMetrics() {}

  public static CounterMetricsContext counter(final ActivatedJob activatedJob) {
    return new CounterMetricsContext(
        Name.INVOCATION.asString(),
        Map.ofEntries(Map.entry(Tag.TYPE.asString(), activatedJob.getType())),
        1);
  }

  public static TimerMetricsContext timer(final ActivatedJob activatedJob) {
    return new TimerMetricsContext(
        Name.EXECUTION_TIME.asString(),
        Map.ofEntries(Map.entry(Tag.TYPE.asString(), activatedJob.getType())));
  }

  /** Contains constants for the tags used in job handler metrics. */
  public enum Tag {
    TYPE("type"),
    ACTION("action");

    private final String name;

    Tag(final String name) {
      this.name = name;
    }

    public String asString() {
      return name;
    }
  }

  /** Contains constants for the names of job handler metrics. */
  public enum Name {
    INVOCATION("camunda.job.invocations"),
    EXECUTION_TIME("camunda.job.execution-time");

    private final String name;

    Name(final String name) {
      this.name = name;
    }

    public String asString() {
      return name;
    }
  }

  /**
   * Contains constants representing the possible actions performed on a job.
   *
   * <p>These are used as values for the {@link Tag#ACTION} tag.
   */
  public enum Action {
    ACTIVATED("activated"),
    COMPLETED("completed"),
    FAILED("failed"),
    BPMN_ERROR("bpmn-error");

    private final String name;

    Action(final String name) {
      this.name = name;
    }

    public String asString() {
      return name;
    }
  }
}
