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
package io.camunda.spring.client.jobhandling.result;

import io.camunda.client.api.response.ActivatedJob;

public class ResultProcessorContext {
  private final Object result;
  private final ActivatedJob job;

  public ResultProcessorContext(final Object result, final ActivatedJob job) {
    this.result = result;
    this.job = job;
  }

  public Object getResult() {
    return result;
  }

  public ActivatedJob getJob() {
    return job;
  }
}
