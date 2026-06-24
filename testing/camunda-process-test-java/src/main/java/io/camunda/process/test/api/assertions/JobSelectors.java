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

import io.camunda.client.api.search.enums.JobKind;
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
   * Select the job by its BPMN element ID.
   *
   * @param elementId the ID of the BPMN element.
   * @return the selector
   */
  public static JobSelector byElementId(final String elementId) {
    return new JobElementIdSelector(elementId);
  }

  /**
   * Select the job by its process definition ID.
   *
   * @param processDefinitionId the process definition ID
   * @return the selector
   */
  public static JobSelector byProcessDefinitionId(final String processDefinitionId) {
    return new JobProcessDefinitionSelector(processDefinitionId);
  }

  /**
   * Select the job by its kind.
   *
   * @param jobKind the kind of the job
   * @return the selector
   */
  public static JobSelector byJobKind(final JobKind jobKind) {
    return new JobKindSelector(jobKind);
  }

  /**
   * Select the job by its process instance key.
   *
   * @param processInstanceKey the process instance key
   * @return the selector
   */
  public static JobSelector byProcessInstanceKey(final Long processInstanceKey) {
    return new JobProcessInstanceKeySelector(processInstanceKey);
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

    private JobElementIdSelector(final String elementId) {
      this.elementId = elementId;
    }

    @Override
    public boolean test(final Job job) {
      return elementId.equals(job.getElementId());
    }

    @Override
    public String describe() {
      return "elementId: " + elementId;
    }

    @Override
    public void applyFilter(final JobFilter filter) {
      filter.elementId(elementId);
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
      return "processDefinitionId: " + processDefinitionId;
    }

    @Override
    public void applyFilter(final JobFilter filter) {
      filter.processDefinitionId(processDefinitionId);
    }
  }

  private static final class JobKindSelector implements JobSelector {

    private final JobKind jobKind;

    private JobKindSelector(final JobKind jobKind) {
      this.jobKind = jobKind;
    }

    @Override
    public boolean test(final Job job) {
      return jobKind.equals(job.getKind());
    }

    @Override
    public String describe() {
      return "jobKind: " + jobKind;
    }

    @Override
    public void applyFilter(final JobFilter filter) {
      filter.kind(jobKind);
    }
  }

  private static final class JobProcessInstanceKeySelector implements JobSelector {

    private final Long processInstanceKey;

    private JobProcessInstanceKeySelector(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final Job job) {
      return processInstanceKey.equals(job.getProcessInstanceKey());
    }

    @Override
    public String describe() {
      return "processInstanceKey: " + processInstanceKey;
    }

    @Override
    public void applyFilter(final JobFilter filter) {
      filter.processInstanceKey(processInstanceKey);
    }
  }
}
