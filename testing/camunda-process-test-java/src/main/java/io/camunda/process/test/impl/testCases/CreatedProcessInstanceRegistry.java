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
package io.camunda.process.test.impl.testCases;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * The process instances that the running test case created, in creation order.
 *
 * <p>An isolated instance parks until the test case ends it: it waits on jobs no worker takes, so
 * it never completes on its own. The test case cancels every such instance when it finishes.
 */
public class CreatedProcessInstanceRegistry {

  // a CONDITIONAL_BEHAVIOR action runs on a background thread of the conditional behavior engine
  // and may create a process instance, so registration races the main thread reading this list
  private final List<CreatedProcessInstance> createdProcessInstances = new CopyOnWriteArrayList<>();

  public void register(final long processInstanceKey, final boolean isolated) {
    createdProcessInstances.add(new CreatedProcessInstance(processInstanceKey, isolated));
  }

  public void clear() {
    createdProcessInstances.clear();
  }

  /** The keys of the created instances that park until someone ends them. */
  public List<Long> isolatedProcessInstanceKeys() {
    return createdProcessInstances.stream()
        .filter(CreatedProcessInstance::isIsolated)
        .map(CreatedProcessInstance::getProcessInstanceKey)
        .collect(Collectors.toList());
  }

  private static final class CreatedProcessInstance {

    private final long processInstanceKey;
    private final boolean isolated;

    private CreatedProcessInstance(final long processInstanceKey, final boolean isolated) {
      this.processInstanceKey = processInstanceKey;
      this.isolated = isolated;
    }

    private long getProcessInstanceKey() {
      return processInstanceKey;
    }

    private boolean isIsolated() {
      return isolated;
    }
  }
}
