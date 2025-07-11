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
package io.camunda.process.test.impl.runtime;

import java.util.concurrent.atomic.AtomicReference;

/** Singleton for a long-running Camunda runtime used during Camunda Process Tests. */
public enum CamundaProcessTestGlobalRuntime {
  INSTANCE;

  private final AtomicReference<CamundaProcessTestRuntime> runtime = new AtomicReference<>(null);
  private final AtomicReference<CamundaProcessTestRuntimeBuilder> runtimeBuilder =
      new AtomicReference<>(null);

  public void initialize(final CamundaProcessTestRuntimeBuilder runtimeBuilder) {
    this.runtimeBuilder.compareAndSet(null, runtimeBuilder);
    this.runtime.compareAndSet(
        null, new CamundaProcessTestGlobalContainerRuntime(runtimeBuilder.build()));
  }

  public CamundaProcessTestRuntime getRuntime() {
    return runtime.get();
  }

  public CamundaProcessTestRuntimeBuilder getRuntimeBuilder() {
    return runtimeBuilder.get();
  }

  // Accessible via reflection for testing
  private void resetRuntime() throws Exception {
    if (runtime.get() != null) {
      runtime.get().close();
    }

    runtime.set(null);
    runtimeBuilder.set(null);
  }
}
