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

import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.ProcessInstance;

/** A collection of predefined {@link ProcessInstanceSelector}s. */
public class ProcessInstanceSelectors {

  /**
   * Select the process instance by its process instance key.
   *
   * @param processInstanceKey the key of the process instance.
   * @return the selector
   */
  public static ProcessInstanceSelector byKey(final long processInstanceKey) {
    return new ProcessInstanceKeySelector(processInstanceKey);
  }

  /**
   * Select the process instance by its process definition id.
   *
   * @param processDefinitionId the process definition id of the process instance.
   * @return the selector
   */
  public static ProcessInstanceSelector byProcessId(final String processDefinitionId) {
    return new ProcessDefinitionIdSelector(processDefinitionId);
  }

  private static final class ProcessInstanceKeySelector implements ProcessInstanceSelector {

    private final long processInstanceKey;

    private ProcessInstanceKeySelector(final long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final ProcessInstance processInstance) {
      return processInstance.getProcessInstanceKey().equals(processInstanceKey);
    }

    @Override
    public String describe() {
      return String.format("key: %s", processInstanceKey);
    }

    @Override
    public void applyFilter(final ProcessInstanceFilter filter) {
      filter.processInstanceKey(processInstanceKey);
    }
  }

  private static final class ProcessDefinitionIdSelector implements ProcessInstanceSelector {

    private final String processDefinitionId;

    private ProcessDefinitionIdSelector(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
    }

    @Override
    public boolean test(final ProcessInstance processInstance) {
      return processInstance.getProcessDefinitionId().equals(processDefinitionId);
    }

    @Override
    public String describe() {
      return String.format("process-id: '%s'", processDefinitionId);
    }

    @Override
    public void applyFilter(final ProcessInstanceFilter filter) {
      filter.processDefinitionId(processDefinitionId);
    }
  }
}
