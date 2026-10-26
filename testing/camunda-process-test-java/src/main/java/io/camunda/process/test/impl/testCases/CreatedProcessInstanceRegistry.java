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
 * <p>A held timer exists only on an instance created with {@code holdTimers}, and only a
 * CREATE_PROCESS_INSTANCE instruction sets that, so every instance a TRIGGER_TIMER may address was
 * created by the same test case and is recorded here. Resolving a held timer from this registry
 * rather than from a cluster query is what keeps a test case off an instance it does not own: one
 * left behind by an earlier test case of the same test method, or one belonging to somebody else
 * running against the same cluster.
 */
public class CreatedProcessInstanceRegistry {

  // a CONDITIONAL_BEHAVIOR action runs on a background thread of the conditional behavior engine
  // and may create a process instance, so registration races the main thread's resolution
  private final List<CreatedProcessInstance> createdProcessInstances = new CopyOnWriteArrayList<>();

  public void register(
      final String processDefinitionId,
      final long processInstanceKey,
      final boolean holdsTimers,
      final boolean isolated) {
    createdProcessInstances.add(
        new CreatedProcessInstance(processDefinitionId, processInstanceKey, holdsTimers, isolated));
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

  /**
   * Resolves the process instance that owns the held timer to trigger.
   *
   * <p>Matches on the process definition id because that is the only property of the DSL's process
   * instance selector. A new selector property has to be honoured here too, as TRIGGER_TIMER
   * resolves the instance itself instead of going through {@code
   * InstructionSelectorFactory#buildProcessInstanceSelector}.
   *
   * @param processDefinitionId the process definition id the instruction selected
   * @return the key of the single matching instance created by this test case
   * @throws IllegalStateException if no instance matches, or more than one does
   */
  public long resolveHeldTimerProcessInstanceKey(final String processDefinitionId) {
    final List<CreatedProcessInstance> matches =
        createdProcessInstances.stream()
            .filter(CreatedProcessInstance::holdsTimers)
            .filter(instance -> instance.getProcessDefinitionId().equals(processDefinitionId))
            .collect(Collectors.toList());

    if (matches.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "Expected to trigger a held timer of process '%s', but this test case created no such"
                  + " process instance. A held timer can only be triggered on an instance that the"
                  + " same test case created with holdTimers. A held timer inside a called process"
                  + " is not addressable yet.",
              processDefinitionId));
    }
    if (matches.size() > 1) {
      throw new IllegalStateException(
          String.format(
              "Expected to trigger a held timer of process '%s', but this test case created %d"
                  + " process instances of it (process instance keys: %s). A held timer is"
                  + " addressed by its process instance, which is ambiguous here. Create only one"
                  + " instance of the process in this test case.",
              processDefinitionId,
              matches.size(),
              matches.stream()
                  .map(instance -> String.valueOf(instance.getProcessInstanceKey()))
                  .collect(Collectors.joining(", "))));
    }
    return matches.get(0).getProcessInstanceKey();
  }

  private static final class CreatedProcessInstance {

    private final String processDefinitionId;
    private final long processInstanceKey;
    private final boolean holdsTimers;
    private final boolean isolated;

    private CreatedProcessInstance(
        final String processDefinitionId,
        final long processInstanceKey,
        final boolean holdsTimers,
        final boolean isolated) {
      this.processDefinitionId = processDefinitionId;
      this.processInstanceKey = processInstanceKey;
      this.holdsTimers = holdsTimers;
      this.isolated = isolated;
    }

    private String getProcessDefinitionId() {
      return processDefinitionId;
    }

    private long getProcessInstanceKey() {
      return processInstanceKey;
    }

    private boolean holdsTimers() {
      return holdsTimers;
    }

    private boolean isIsolated() {
      return isolated;
    }
  }
}
