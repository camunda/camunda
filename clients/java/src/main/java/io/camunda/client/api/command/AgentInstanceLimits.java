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

/**
 * A snapshot of the agent instance's operational limits, as recorded on a CONFIGURATION history
 * item.
 */
public interface AgentInstanceLimits {
  long getMaxTokens();

  int getMaxModelCalls();

  int getMaxToolCalls();

  /**
   * Creates a limits snapshot with the given values.
   *
   * @param maxTokens the token limit. Use -1 for no limit.
   * @param maxModelCalls the model-call limit. Use -1 for no limit.
   * @param maxToolCalls the tool-call limit. Use -1 for no limit.
   * @return a new {@link AgentInstanceLimits}
   */
  static AgentInstanceLimits of(
      final long maxTokens, final int maxModelCalls, final int maxToolCalls) {
    return new AgentInstanceLimits() {
      @Override
      public long getMaxTokens() {
        return maxTokens;
      }

      @Override
      public int getMaxModelCalls() {
        return maxModelCalls;
      }

      @Override
      public int getMaxToolCalls() {
        return maxToolCalls;
      }
    };
  }
}
