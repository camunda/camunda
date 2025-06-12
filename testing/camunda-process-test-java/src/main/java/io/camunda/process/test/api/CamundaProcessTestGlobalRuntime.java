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
package io.camunda.process.test.api;

import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.runtime.CamundaProcessTestGlobalContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;

/** Singleton for a long-running Camunda runtime used during Camunda Process Tests. */
public enum CamundaProcessTestGlobalRuntime {
  INSTANCE;

  private CamundaProcessTestGlobalContainerRuntime runtime = null;

  public CamundaProcessTestRuntime getRuntime(final ContainerFactory containerFactory) {
    if (runtime == null) {
      runtime = new CamundaProcessTestGlobalContainerRuntime(containerFactory);
    }

    return runtime;
  }
}
