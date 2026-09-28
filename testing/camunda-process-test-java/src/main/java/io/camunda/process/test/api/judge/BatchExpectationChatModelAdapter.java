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

import java.util.Map;

/**
 * Extends {@link ChatModelAdapter} for adapters that can evaluate several named natural language
 * expectations against one actual value in a single call, returning a truth value (0.0-1.0) per
 * expectation instead of free-form text.
 *
 * <p>This is the seam for typed classifier backends (as opposed to text-generation chat models): an
 * adapter implementing this interface is not expected to support free-form prompts, and may throw
 * {@link UnsupportedOperationException} from {@link #generate(String)}.
 *
 * <p>Any {@link ChatModelAdapter} configured as the judge's chat model that also implements this
 * interface is used automatically for both single-expectation and multi-expectation judge
 * assertions; a plain {@link ChatModelAdapter} only supports single-expectation evaluation.
 */
public interface BatchExpectationChatModelAdapter extends ChatModelAdapter {

  /**
   * Evaluates the given actual value against several named expectations in one call.
   *
   * @param actualValue the value being evaluated
   * @param namedExpectations expectations keyed by an opaque, caller-assigned id
   * @return a truth value (0.0-1.0) per expectation id, using the same keys as {@code
   *     namedExpectations}
   */
  Map<String, Double> evaluateExpectations(
      String actualValue, Map<String, String> namedExpectations);
}
