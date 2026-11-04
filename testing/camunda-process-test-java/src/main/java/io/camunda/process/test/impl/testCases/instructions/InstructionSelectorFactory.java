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
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.assertions.IncidentSelectors;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.assertions.JobSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.impl.testCases.TestRunContext;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

/** Factory for creating selector instances from DSL selectors. */
final class InstructionSelectorFactory {

  // Impossible process instance key, applied when the run's instance is not visible yet so the
  // job/user-task/incident search matches nothing and its surrounding await retries.
  private static final long NO_MATCH_KEY = -1L;

  private InstructionSelectorFactory() {
    // Utility class
  }

  /**
   * Builds a process instance selector, scoping it to the current test run when isolation is
   * enabled. The run scope is added as a tag filter on the instance search, so it narrows the
   * search in the backend rather than post-filtering a page of results.
   *
   * @param dslSelector the DSL process instance selector
   * @param runContext the current test run context
   * @return the process instance selector
   */
  static ProcessInstanceSelector buildProcessInstanceSelector(
      final io.camunda.process.test.api.testCases.ProcessInstanceSelector dslSelector,
      final TestRunContext runContext) {
    final ProcessInstanceSelector selector = buildProcessInstanceSelector(dslSelector);
    if (runContext != null && runContext.isEnabled()) {
      return selector.and(byRunId(runContext.getRunId()));
    }
    return selector;
  }

  private static ProcessInstanceSelector byRunId(final String runId) {
    final String runScopeTag = TestRunContext.RUN_SCOPE_TAG_PREFIX + runId;
    return new ProcessInstanceSelector() {
      @Override
      public boolean test(final ProcessInstance processInstance) {
        return true;
      }

      @Override
      public void applyFilter(final ProcessInstanceFilter filter) {
        filter.tags(runScopeTag);
      }

      @Override
      public String describe() {
        return runScopeDescription(runId);
      }
    };
  }

  /**
   * Builds a process instance selector from a DSL process instance selector.
   *
   * @param dslSelector the DSL process instance selector
   * @return the process instance selector
   * @throws IllegalArgumentException if the process definition ID is not set
   */
  static ProcessInstanceSelector buildProcessInstanceSelector(
      final io.camunda.process.test.api.testCases.ProcessInstanceSelector dslSelector) {
    return dslSelector
        .getProcessDefinitionId()
        .map(ProcessInstanceSelectors::byProcessId)
        .orElseThrow(
            () -> new IllegalArgumentException("Missing required property: processDefinitionId"));
  }

  /**
   * Builds a user task selector from a DSL user task selector.
   *
   * @param dslSelector the DSL user task selector
   * @return the user task selector
   * @throws IllegalArgumentException if no selector property is set
   */
  static UserTaskSelector buildUserTaskSelector(
      final io.camunda.process.test.api.testCases.UserTaskSelector dslSelector) {
    UserTaskSelector selector = null;

    if (dslSelector.getElementId().isPresent()) {
      selector = UserTaskSelectors.byElementId(dslSelector.getElementId().get());
    }

    if (dslSelector.getTaskName().isPresent()) {
      final UserTaskSelector taskNameSelector =
          UserTaskSelectors.byTaskName(dslSelector.getTaskName().get());
      selector = selector != null ? selector.and(taskNameSelector) : taskNameSelector;
    }

    if (dslSelector.getProcessDefinitionId().isPresent()) {
      final UserTaskSelector processDefSelector =
          UserTaskSelectors.byProcessDefinitionId(dslSelector.getProcessDefinitionId().get());
      selector = selector != null ? selector.and(processDefSelector) : processDefSelector;
    }

    if (selector == null) {
      throw new IllegalArgumentException(
          "Missing required property: at least one of elementId, taskName, or processDefinitionId must be set");
    }

    return selector;
  }

  /**
   * Builds a job selector from a DSL job selector.
   *
   * @param dslSelector the DSL job selector
   * @return the job selector
   * @throws IllegalArgumentException if no selector property is set
   */
  static JobSelector buildJobSelector(
      final io.camunda.process.test.api.testCases.JobSelector dslSelector) {
    JobSelector selector = null;

    if (dslSelector.getJobType().isPresent()) {
      selector = JobSelectors.byJobType(dslSelector.getJobType().get());
    }

    if (dslSelector.getJobKind().isPresent()) {
      final JobSelector jobKindSelector =
          JobSelectors.byJobKind(JobKind.valueOf(dslSelector.getJobKind().get()));
      selector = selector != null ? selector.and(jobKindSelector) : jobKindSelector;
    }

    if (dslSelector.getElementId().isPresent()) {
      final JobSelector elementIdSelector =
          JobSelectors.byElementId(dslSelector.getElementId().get());
      selector = selector != null ? selector.and(elementIdSelector) : elementIdSelector;
    }

    if (dslSelector.getProcessDefinitionId().isPresent()) {
      final JobSelector processDefSelector =
          JobSelectors.byProcessDefinitionId(dslSelector.getProcessDefinitionId().get());
      selector = selector != null ? selector.and(processDefSelector) : processDefSelector;
    }

    if (selector == null) {
      throw new IllegalArgumentException(
          "Missing required property: at least one of jobType, jobKind, elementId, or processDefinitionId must be set");
    }

    return selector;
  }

  /**
   * Builds a job selector that identifies the job a stubbed call activity waits on, from a DSL
   * element selector.
   *
   * @param dslSelector the DSL element selector identifying the call activity
   * @return the job selector
   * @throws IllegalArgumentException if the call activity is not selected by elementId
   */
  static JobSelector buildCallActivityStubJobSelector(
      final io.camunda.process.test.api.testCases.ElementSelector dslSelector) {
    if (dslSelector.getElementId().isPresent()) {
      return JobSelectors.byJobKind(JobKind.CALL_ACTIVITY_STUB)
          .and(JobSelectors.byElementId(dslSelector.getElementId().get()));
    }
    throw new IllegalArgumentException(
        "The call activity must be selected by elementId; a stub job is keyed by its element id.");
  }

  /**
   * Reads the BPMN element id of a timer catch event from a DSL element selector.
   *
   * @param dslSelector the DSL element selector identifying the timer catch event
   * @return the element id
   * @throws IllegalArgumentException if the element is not selected by elementId
   */
  static String buildTimerElementId(
      final io.camunda.process.test.api.testCases.ElementSelector dslSelector) {
    return dslSelector
        .getElementId()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "The timer catch event must be selected by elementId; a timer is addressed by"
                        + " its element id, which for a boundary timer is the boundary event's own"
                        + " id."));
  }

  /**
   * Builds an element selector from a DSL element selector.
   *
   * @param dslSelector the DSL element selector
   * @return the element selector
   * @throws IllegalArgumentException if neither elementId nor elementName is set
   */
  static ElementSelector buildElementSelector(
      final io.camunda.process.test.api.testCases.ElementSelector dslSelector) {
    if (dslSelector.getElementId().isPresent()) {
      return ElementSelectors.byId(dslSelector.getElementId().get());
    } else if (dslSelector.getElementName().isPresent()) {
      return ElementSelectors.byName(dslSelector.getElementName().get());
    } else {
      throw new IllegalArgumentException(
          "Element selector must have either elementId or elementName");
    }
  }

  /**
   * Builds an incident selector from a DSL incident selector.
   *
   * @param dslSelector the DSL incident selector
   * @return the incident selector
   * @throws IllegalArgumentException if no selector property is set
   */
  static IncidentSelector buildIncidentSelector(
      final io.camunda.process.test.api.testCases.IncidentSelector dslSelector) {
    IncidentSelector selector = null;

    if (dslSelector.getElementId().isPresent()) {
      selector = IncidentSelectors.byElementId(dslSelector.getElementId().get());
    }

    if (dslSelector.getProcessDefinitionId().isPresent()) {
      final IncidentSelector processDefSelector =
          IncidentSelectors.byProcessDefinitionId(dslSelector.getProcessDefinitionId().get());
      selector = selector != null ? selector.and(processDefSelector) : processDefSelector;
    }

    if (selector == null) {
      throw new IllegalArgumentException(
          "Missing required property: at least one of elementId or processDefinitionId must be set");
    }

    return selector;
  }

  /**
   * Builds a decision selector from a DSL decision selector.
   *
   * @param dslSelector the DSL decision selector
   * @return the decision selector
   * @throws IllegalArgumentException if neither decisionDefinitionId nor decisionDefinitionName is
   *     set
   */
  static DecisionSelector buildDecisionSelector(
      final io.camunda.process.test.api.testCases.DecisionSelector dslSelector) {
    DecisionSelector selector = null;

    if (dslSelector.getDecisionDefinitionId().isPresent()) {
      selector = DecisionSelectors.byId(dslSelector.getDecisionDefinitionId().get());
    }

    if (dslSelector.getDecisionDefinitionName().isPresent()) {
      final DecisionSelector nameSelector =
          DecisionSelectors.byName(dslSelector.getDecisionDefinitionName().get());
      selector = selector != null ? selector.and(nameSelector) : nameSelector;
    }

    if (selector == null) {
      throw new IllegalArgumentException(
          "Decision selector must have either decisionDefinitionId or decisionDefinitionName");
    }

    return selector;
  }

  // --- Run isolation -------------------------------------------------------------------------
  // User tasks inherit their instance's tags and expose a tag filter, so they are scoped directly
  // by
  // the reserved run-scope tag. Jobs and incidents have no tag filter on their search, so for those
  // the run's instances are resolved by tag first and the search is scoped to their process
  // instance
  // keys; the selector's own job type / element id still narrows within those instances. Instances
  // the run did not tag (external starts, non-propagating child processes) will not match.

  static JobSelector buildJobSelector(
      final io.camunda.process.test.api.testCases.JobSelector dslSelector,
      final TestRunContext runContext,
      final CamundaClient client) {
    final JobSelector selector = buildJobSelector(dslSelector);
    if (!isScoped(runContext)) {
      return selector;
    }
    return selector.and(
        new JobSelector() {
          @Override
          public boolean test(final Job job) {
            return true;
          }

          @Override
          public void applyFilter(final JobFilter filter) {
            applyRunInstanceKeys(
                client,
                runContext.getRunId(),
                filter::processInstanceKey,
                filter::processInstanceKey);
          }

          @Override
          public String describe() {
            return runScopeDescription(runContext.getRunId());
          }
        });
  }

  static UserTaskSelector buildUserTaskSelector(
      final io.camunda.process.test.api.testCases.UserTaskSelector dslSelector,
      final TestRunContext runContext,
      final CamundaClient client) {
    final UserTaskSelector selector = buildUserTaskSelector(dslSelector);
    if (!isScoped(runContext)) {
      return selector;
    }
    final String runScopeTag = TestRunContext.RUN_SCOPE_TAG_PREFIX + runContext.getRunId();
    return selector.and(
        new UserTaskSelector() {
          @Override
          public boolean test(final UserTask userTask) {
            return true;
          }

          @Override
          public void applyFilter(final UserTaskFilter filter) {
            filter.tags(runScopeTag);
          }

          @Override
          public String describe() {
            return runScopeDescription(runContext.getRunId());
          }
        });
  }

  static IncidentSelector buildIncidentSelector(
      final io.camunda.process.test.api.testCases.IncidentSelector dslSelector,
      final TestRunContext runContext,
      final CamundaClient client) {
    final IncidentSelector selector = buildIncidentSelector(dslSelector);
    if (!isScoped(runContext)) {
      return selector;
    }
    return selector.and(
        new IncidentSelector() {
          @Override
          public boolean test(final Incident incident) {
            return true;
          }

          @Override
          public void applyFilter(final IncidentFilter filter) {
            applyRunInstanceKeys(
                client,
                runContext.getRunId(),
                filter::processInstanceKey,
                filter::processInstanceKey);
          }

          @Override
          public String describe() {
            return runScopeDescription(runContext.getRunId());
          }
        });
  }

  private static boolean isScoped(final TestRunContext runContext) {
    return runContext != null && runContext.isEnabled();
  }

  private static void applyRunInstanceKeys(
      final CamundaClient client,
      final String runId,
      final LongConsumer single,
      final Consumer<Consumer<BasicLongProperty>> multi) {
    final List<Long> keys = resolveRunInstanceKeys(client, runId);
    if (keys.isEmpty()) {
      // The run's instance is not visible yet; match nothing so the surrounding await retries.
      single.accept(NO_MATCH_KEY);
    } else if (keys.size() == 1) {
      single.accept(keys.get(0));
    } else {
      multi.accept(property -> property.in(keys));
    }
  }

  private static List<Long> resolveRunInstanceKeys(final CamundaClient client, final String runId) {
    final String runScopeTag = TestRunContext.RUN_SCOPE_TAG_PREFIX + runId;
    return client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.tags(runScopeTag))
        .send()
        .join()
        .items()
        .stream()
        .map(ProcessInstance::getProcessInstanceKey)
        .collect(Collectors.toList());
  }

  private static String runScopeDescription(final String runId) {
    return String.format("%s%s", TestRunContext.RUN_SCOPE_TAG_PREFIX, runId);
  }
}
