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
package io.camunda.client.spring.actuator;

import static java.util.Optional.ofNullable;

import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.jobhandling.JobWorkerChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.ComposedChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.EnabledChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.FetchVariablesChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.ForceFetchAllVariablesChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.MaxJobsActiveChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.MaxRetriesChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.NameChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.NoopChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.PollIntervalChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.RequestTimeoutChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.StreamEnabledChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.StreamTimeoutChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.TenantIdsChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.TimeoutChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.TypeChangeSet;
import io.camunda.client.jobhandling.JobWorkerManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.Nullable;

@WebEndpoint(id = "jobWorkers")
public class JobWorkerController {
  private final JobWorkerManager jobWorkerManager;

  public JobWorkerController(final JobWorkerManager jobWorkerManager) {
    this.jobWorkerManager = jobWorkerManager;
  }

  @ReadOperation
  public List<JobWorkerDto> getJobWorkers() {
    return jobWorkerManager.getJobWorkers().entrySet().stream()
        .map(e -> fromJobWorkerValue(e.getKey(), e.getValue()))
        .toList();
  }

  @ReadOperation
  public JobWorkerDto getJobWorker(@Selector final String type) {
    return fromJobWorkerValue(type, jobWorkerManager.getJobWorker(type));
  }

  @WriteOperation
  public void updateJobWorkers(
      @Nullable final String name,
      @Nullable final Boolean enabled,
      @Nullable final String tenantIds,
      @Nullable final Duration timeout,
      @Nullable final Integer maxJobsActive,
      @Nullable final Duration requestTimeout,
      @Nullable final Duration pollInterval,
      @Nullable final String fetchVariables,
      @Nullable final Boolean forceFetchAllVariables,
      @Nullable final Boolean streamEnabled,
      @Nullable final Duration streamTimeout,
      @Nullable final Integer maxRetries,
      @Nullable final Boolean reset,
      @Nullable final Boolean applyCustomizers) {
    if (reset != null && reset) {
      jobWorkerManager.resetJobWorkers();
    } else {
      jobWorkerManager.updateJobWorkers(
          createChangeSet(
              null,
              name,
              enabled,
              tenantIds,
              timeout,
              maxJobsActive,
              requestTimeout,
              pollInterval,
              fetchVariables,
              forceFetchAllVariables,
              streamEnabled,
              streamTimeout,
              maxRetries),
          ofNullable(applyCustomizers).orElse(false));
    }
  }

  @WriteOperation
  public void updateJobWorker(
      @Selector final String finalType,
      @Nullable final String type,
      @Nullable final String name,
      @Nullable final Boolean enabled,
      @Nullable final String tenantIds,
      @Nullable final Duration timeout,
      @Nullable final Integer maxJobsActive,
      @Nullable final Duration requestTimeout,
      @Nullable final Duration pollInterval,
      @Nullable final String fetchVariables,
      @Nullable final Boolean forceFetchAllVariables,
      @Nullable final Boolean streamEnabled,
      @Nullable final Duration streamTimeout,
      @Nullable final Integer maxRetries,
      @Nullable final Boolean reset,
      @Nullable final Boolean applyCustomizers) {
    if (reset != null && reset) {
      jobWorkerManager.resetJobWorker(finalType);
    } else {
      jobWorkerManager.updateJobWorker(
          finalType,
          createChangeSet(
              type,
              name,
              enabled,
              tenantIds,
              timeout,
              maxJobsActive,
              requestTimeout,
              pollInterval,
              fetchVariables,
              forceFetchAllVariables,
              streamEnabled,
              streamTimeout,
              maxRetries),
          ofNullable(applyCustomizers).orElse(false));
    }
  }

  private JobWorkerChangeSet createChangeSet(
      final String type,
      final String name,
      final Boolean enabled,
      final String tenantIds,
      final Duration timeout,
      final Integer maxJobsActive,
      final Duration requestTimeout,
      final Duration pollInterval,
      final String fetchVariables,
      final Boolean forceFetchAllVariables,
      final Boolean streamEnabled,
      final Duration streamTimeout,
      final Integer maxRetries) {
    final List<JobWorkerChangeSet> changeSets = new ArrayList<>();
    if (enabled != null) {
      changeSets.add(new EnabledChangeSet(enabled));
    }
    if (name != null) {
      changeSets.add(new NameChangeSet(name));
    }
    if (type != null) {
      changeSets.add(new TypeChangeSet(type));
    }
    if (tenantIds != null) {
      changeSets.add(
          new TenantIdsChangeSet(Arrays.stream(tenantIds.split(",")).map(String::trim).toList()));
    }
    if (timeout != null) {
      changeSets.add(new TimeoutChangeSet(timeout));
    }
    if (maxJobsActive != null) {
      changeSets.add(new MaxJobsActiveChangeSet(maxJobsActive));
    }
    if (requestTimeout != null) {
      changeSets.add(new RequestTimeoutChangeSet(requestTimeout));
    }
    if (pollInterval != null) {
      changeSets.add(new PollIntervalChangeSet(pollInterval));
    }
    if (fetchVariables != null) {
      changeSets.add(
          new FetchVariablesChangeSet(
              Arrays.stream(fetchVariables.split(",")).map(String::trim).toList()));
    }
    if (forceFetchAllVariables != null) {
      changeSets.add(new ForceFetchAllVariablesChangeSet(forceFetchAllVariables));
    }
    if (streamEnabled != null) {
      changeSets.add(new StreamEnabledChangeSet(streamEnabled));
    }
    if (streamTimeout != null) {
      changeSets.add(new StreamTimeoutChangeSet(streamTimeout));
    }
    if (maxRetries != null) {
      changeSets.add(new MaxRetriesChangeSet(maxRetries));
    }
    if (changeSets.isEmpty()) {
      return new NoopChangeSet();
    } else if (changeSets.size() == 1) {
      return changeSets.get(0);
    } else {
      return new ComposedChangeSet(changeSets);
    }
  }

  private static JobWorkerDto fromJobWorkerValue(
      final String type, final JobWorkerValue jobWorkerValue) {
    return new JobWorkerDto(
        type,
        jobWorkerValue.getType().value(),
        jobWorkerValue.getName().value(),
        ofNullable(jobWorkerValue.getEnabled()).orElse(true),
        jobWorkerValue.getTenantIds());
  }

  public record JobWorkerDto(
      String type, String currentType, String name, boolean enabled, List<String> tenantIds) {}
}
