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

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Task;

/**
 * A builder for tasks that are based on jobs and should be processed by job workers. For example,
 * service tasks.
 */
public abstract class AbstractJobWorkerTaskBuilder<
        B extends AbstractJobWorkerTaskBuilder<B, T>, T extends Task>
    extends AbstractTaskBuilder<B, T> implements ZeebeJobWorkerElementBuilder<B> {

  private final ZeebeJobWorkerPropertiesBuilder<B> jobWorkerPropertiesBuilder;

  protected AbstractJobWorkerTaskBuilder(
      final BpmnModelInstance modelInstance, final T element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
    // delegates to the element builder but keeping this class for backward compatibility
    jobWorkerPropertiesBuilder = new ZeebeJobWorkerPropertiesBuilderImpl<>(myself);
  }

  @Override
  public B zeebeJobType(final String type) {
    return jobWorkerPropertiesBuilder.zeebeJobType(type);
  }

  @Override
  public B zeebeJobTypeExpression(final String expression) {
    return jobWorkerPropertiesBuilder.zeebeJobTypeExpression(expression);
  }

  @Override
  public B zeebeJobRetries(final String retries) {
    return jobWorkerPropertiesBuilder.zeebeJobRetries(retries);
  }

  @Override
  public B zeebeJobRetriesExpression(final String expression) {
    return jobWorkerPropertiesBuilder.zeebeJobRetriesExpression(expression);
  }

  @Override
  public B zeebeTaskHeader(final String key, final String value) {
    return jobWorkerPropertiesBuilder.zeebeTaskHeader(key, value);
  }
}
