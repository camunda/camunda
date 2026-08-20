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

/**
 * A fluent builder for job worker related properties of elements that are based on jobs and are
 * processed by job workers (e.g. service tasks).
 */
public interface ZeebeJobWorkerPropertiesBuilder<T> {

  /**
   * Sets a static type for the job.
   *
   * @param type the type of the job
   * @return the builder instance
   */
  T zeebeJobType(final String type);

  /**
   * Sets a dynamic type for the job that is retrieved from the given expression.
   *
   * @param expression the expression for the type of the job
   * @return the builder instance
   */
  T zeebeJobTypeExpression(final String expression);

  /**
   * Sets a static number of retries for the job.
   *
   * @param retries the number of job retries
   * @return the builder instance
   */
  T zeebeJobRetries(final String retries);

  /**
   * Sets a dynamic number of retries for the job that is retrieved from the given expression
   *
   * @param expression the expression for the number of job retries
   * @return the builder instance
   */
  T zeebeJobRetriesExpression(final String expression);

  /**
   * Adds a custom task header for the job. Can be called multiple times.
   *
   * @param key the key of the custom header
   * @param value the value of the custom header
   * @return the builder instance
   */
  T zeebeTaskHeader(final String key, final String value);
}
