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
package io.zeebe.util.sched;

/**
 * Maintains multiple levels of queues for each thread. Levels can be used for priorities (each
 * thread maintains a queue for each priority) or other things like IO-devices.
 */
public class MultiLevelWorkstealingGroup {
  private final WorkStealingGroup[] workStealingGroups;

  public MultiLevelWorkstealingGroup(int numOfThreads, int levels) {
    workStealingGroups = new WorkStealingGroup[levels];
    for (int i = 0; i < levels; i++) {
      workStealingGroups[i] = new WorkStealingGroup(numOfThreads);
    }
  }

  public ActorTask getNextTask(int level) {
    return workStealingGroups[level].getNextTask();
  }

  public void submit(ActorTask task, int level, int threadId) {
    workStealingGroups[level].submit(task, threadId);
  }
}
