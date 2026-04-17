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
package io.camunda.zeebe.model.bpmn.builder;

import java.util.function.Consumer;

/** A fluent builder for elements with execution listeners. */
public interface ZeebeExecutionListenersBuilder<B> {

  B zeebeStartExecutionListener(String type, String retries);

  B zeebeStartExecutionListener(String type);

  B zeebeEndExecutionListener(String type, String retries);

  B zeebeEndExecutionListener(String type);

  /**
   * Adds a {@code beforeAll} execution listener to a multi-instance activity. The listener job is
   * created and completed <em>before</em> the input collection or loop cardinality is evaluated and
   * before any child element instances are created. Variables set by this listener are available to
   * the {@code inputCollection} and {@code loopCardinality} expressions.
   *
   * <p>This listener type is only valid on multi-instance activities.
   *
   * @param type the job type of the listener worker
   * @param retries the number of retries for the listener job
   * @return this builder
   */
  default B zeebeBeforeAllExecutionListener(final String type, final String retries) {
    return null;
  }

  /**
   * Adds a {@code beforeAll} execution listener to a multi-instance activity using the default
   * retry count. See {@link #zeebeBeforeAllExecutionListener(String, String)} for full semantics.
   *
   * @param type the job type of the listener worker
   * @return this builder
   */
  default B zeebeBeforeAllExecutionListener(final String type) {
    return null;
  }

  B zeebeExecutionListener(
      final Consumer<ExecutionListenerBuilder> executionListenerBuilderConsumer);
}
