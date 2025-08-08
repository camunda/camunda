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
package io.camunda.spring.client.actuator;

import static java.util.Optional.ofNullable;

import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet.ComposedChangeSet;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet.EnabledChangeSet;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet.NameChangeSet;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet.NoopChangeSet;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet.TenantIdsChangeSet;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet.TypeChangeSet;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
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
      @Nullable final Boolean reset,
      @Nullable final Boolean applyCustomizers) {
    if (reset != null && reset) {
      jobWorkerManager.resetJobWorkers();
    } else {
      jobWorkerManager.updateJobWorkers(
          createChangeSet(null, name, enabled, tenantIds),
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
      @Nullable final Boolean reset,
      @Nullable final Boolean applyCustomizers) {
    if (reset != null && reset) {
      jobWorkerManager.resetJobWorker(finalType);
    } else {
      jobWorkerManager.updateJobWorker(
          finalType,
          createChangeSet(type, name, enabled, tenantIds),
          ofNullable(applyCustomizers).orElse(false));
    }
  }

  private JobWorkerChangeSet createChangeSet(
      final String type, final String name, final Boolean enabled, final String tenantIds) {
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
        jobWorkerValue.getType(),
        jobWorkerValue.getName(),
        ofNullable(jobWorkerValue.getEnabled()).orElse(true));
  }

  public record JobWorkerDto(String type, String currentType, String name, boolean enabled) {}
}
