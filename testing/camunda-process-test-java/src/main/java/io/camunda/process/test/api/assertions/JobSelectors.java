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

import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.response.Job;

/** A collection of predefined {@link JobSelector}s. */
public class JobSelectors {

  /**
   * Select the job by its type.
   *
   * @param jobType the type of the job.
   * @return the selector
   */
  public static JobSelector byJobType(final String jobType) {
    return new JobTypeSelector(jobType);
  }

  /**
   * Select the BPMN job by its element ID.
   *
   * @param elementId the ID of the BPMN element.
   * @return the selector
   */
  public static JobSelector byElementId(final String elementId) {
    return new JobElementIdSelector(elementId);
  }

  /**
   * Select the BPMN job by its element ID.
   *
   * @param elementId the ID of the BPMN element.
   * @param processInstanceKey the associated process instance
   * @return the selector
   */
  public static JobSelector byElementId(final String elementId, final long processInstanceKey) {
    return new JobElementIdSelector(elementId, processInstanceKey);
  }

  /**
   * Select the BPMN job by its process definition ID.
   *
   * @param processDefinitionId the process definition ID
   * @return the selector
   */
  public static JobSelector byProcessDefinitionId(final String processDefinitionId) {
    return new JobProcessDefinitionSelector(processDefinitionId);
  }

  /**
   * Select the BPMN job by its processInstanceKey.
   *
   * @param processInstanceKey the associated process instance
   * @return the selector
   */
  public static JobSelector byProcessInstanceKey(final long processInstanceKey) {
    return new JobProcessInstanceSelector(processInstanceKey);
  }

  private static final class JobTypeSelector implements JobSelector {

    private final String jobType;

    private JobTypeSelector(final String jobType) {
      this.jobType = jobType;
    }

    @Override
    public boolean test(final Job job) {
      return jobType.equals(job.getType());
    }

    @Override
    public String describe() {
      return "jobType: " + jobType;
    }

    @Override
    public void applyFilter(final JobFilter filter) {
      filter.type(jobType);
    }
  }

  private static final class JobElementIdSelector implements JobSelector {

    private final String elementId;
    private final Long processInstanceKey;

    private JobElementIdSelector(final String elementId) {
      this(elementId, null);
    }

    private JobElementIdSelector(final String elementId, final Long processInstanceKey) {
      this.elementId = elementId;
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final Job job) {
      return elementId.equals(job.getElementId());
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
    public void applyFilter(final JobFilter filter) {
      filter.elementId(elementId);
      if (processInstanceKey != null) {
        filter.processInstanceKey(processInstanceKey);
      }
    }
  }

  private static final class JobProcessDefinitionSelector implements JobSelector {

    private final String processDefinitionId;

    private JobProcessDefinitionSelector(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
    }

    @Override
    public boolean test(final Job job) {
      return processDefinitionId.equals(job.getProcessDefinitionId());
    }

    @Override
    public String describe() {
      return String.format("processDefinitionId: %s", processDefinitionId);
    }

    @Override
    public void applyFilter(final JobFilter filter) {
      filter.processDefinitionId(processDefinitionId);
    }
  }

  private static final class JobProcessInstanceSelector implements JobSelector {

    private final long processInstanceKey;

    private JobProcessInstanceSelector(final long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final Job job) {
      return job.getProcessInstanceKey().equals(processInstanceKey);
    }

    @Override
    public String describe() {
      return String.format("processInstanceKey: %d", processInstanceKey);
    }

    @Override
    public void applyFilter(final JobFilter filter) {
      filter.processInstanceKey(processInstanceKey);
    }
  }
}
