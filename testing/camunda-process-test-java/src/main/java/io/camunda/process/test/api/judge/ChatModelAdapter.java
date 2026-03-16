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
package io.camunda.process.test.api.judge;

/**
 * A functional interface that adapts any chat model to a simple string-in, string-out contract.
 *
 * <p>This can be used any existing LLM integration or any custom implementation that takes a prompt
 * string and returns a response string.
 */
@FunctionalInterface
public interface ChatModelAdapter {

  /**
   * Generates a response for the given prompt.
   *
   * @param prompt the input prompt
   * @return the generated response
   */
  String generate(String prompt);
}
