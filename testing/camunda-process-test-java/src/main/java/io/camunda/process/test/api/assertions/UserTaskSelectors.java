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
package io.camunda.process.test.api.assertions;

import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.response.UserTask;

/** A collection of predefined {@link UserTaskSelector}s. */
public class UserTaskSelectors {

  /**
   * Select the BPMN user task by its ID.
   *
   * @param elementId the ID of the BPMN element.
   * @return the selector
   */
  public static UserTaskSelector byElementId(final String elementId) {
    return new UserTaskElementIdSelector(elementId);
  }

  /**
   * Select the BPMN user task by its ID.
   *
   * @param elementId the ID of the BPMN element.
   * @param processInstanceKey the associated process instance
   * @return the selector
   */
  public static UserTaskSelector byElementId(
      final String elementId, final long processInstanceKey) {
    return new UserTaskElementIdSelector(elementId, processInstanceKey);
  }

  /**
   * Select the BPMN user task by its task name.
   *
   * @param taskName the name of the BPMN element.
   * @return the selector
   */
  public static UserTaskSelector byTaskName(final String taskName) {
    return new UserTaskNameSelector(taskName);
  }

  /**
   * Select the BPMN user task by its task name and the associated processInstanceKey.
   *
   * @param taskName the name of the BPMN element.
   * @param processInstanceKey the associated process instance
   * @return the selector
   */
  public static UserTaskSelector byTaskName(final String taskName, final long processInstanceKey) {
    return new UserTaskNameSelector(taskName, processInstanceKey);
  }

  /**
   * Select the BPMN user task by its processInstanceKey.
   *
   * @param processInstanceKey the associated process instance
   * @return the selector
   */
  public static UserTaskSelector byProcessInstanceKey(final long processInstanceKey) {
    return new UserTaskProcessInstanceSelector(processInstanceKey);
  }

  private static final class UserTaskElementIdSelector implements UserTaskSelector {

    private final String elementId;
    private final Long processInstanceKey;

    private UserTaskElementIdSelector(final String elementId) {
      this(elementId, null);
    }

    private UserTaskElementIdSelector(final String elementId, final Long processInstanceKey) {
      this.elementId = elementId;
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final UserTask userTask) {
      return elementId.equals(userTask.getElementId());
    }

    @Override
    public String describe() {
      if (processInstanceKey != null) {
        return String.format(
            "elementId: %s, processInstanceKey: %d", elementId, processInstanceKey);
      } else {
        return "elementId: " + elementId;
      }
    }

    @Override
    public void applyFilter(final UserTaskFilter filter) {
      filter.elementId(elementId);
      if (processInstanceKey != null) {
        filter.processInstanceKey(processInstanceKey);
      }
    }
  }

  private static final class UserTaskNameSelector implements UserTaskSelector {

    private final String taskName;
    private final Long processInstanceKey;

    private UserTaskNameSelector(final String taskName) {
      this(taskName, null);
    }

    private UserTaskNameSelector(final String taskName, final Long processInstanceKey) {
      this.taskName = taskName;
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final UserTask userTask) {
      return taskName.equals(userTask.getName());
    }

    @Override
    public String describe() {
      if (processInstanceKey != null) {
        return String.format("taskName: %s, processInstanceKey: %d", taskName, processInstanceKey);
      } else {
        return "taskName: " + taskName;
      }
    }

    @Override
    public void applyFilter(final UserTaskFilter filter) {
      if (processInstanceKey != null) {
        filter.processInstanceKey(processInstanceKey);
      }
    }
  }

  private static final class UserTaskProcessInstanceSelector implements UserTaskSelector {

    private final long processInstanceKey;

    private UserTaskProcessInstanceSelector(final long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final UserTask userTask) {
      return userTask.getProcessInstanceKey().equals(processInstanceKey);
    }

    @Override
    public String describe() {
      return String.format("processInstanceKey: %d", processInstanceKey);
    }

    @Override
    public void applyFilter(final UserTaskFilter filter) {
      filter.processInstanceKey(processInstanceKey);
    }
  }
}
