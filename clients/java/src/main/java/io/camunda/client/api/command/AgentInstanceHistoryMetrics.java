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
package io.camunda.client.api.command;

/** Per-call token and latency metrics for an ASSISTANT agent instance history item. */
public final class AgentInstanceHistoryMetrics {
  private Long inputTokens;
  private Long outputTokens;
  private Long durationMs;

  public AgentInstanceHistoryMetrics inputTokens(final long inputTokens) {
    this.inputTokens = inputTokens;
    return this;
  }

  public AgentInstanceHistoryMetrics outputTokens(final long outputTokens) {
    this.outputTokens = outputTokens;
    return this;
  }

  public AgentInstanceHistoryMetrics durationMs(final long durationMs) {
    this.durationMs = durationMs;
    return this;
  }

  public Long getInputTokens() {
    return inputTokens;
  }

  public Long getOutputTokens() {
    return outputTokens;
  }

  public Long getDurationMs() {
    return durationMs;
  }
}
