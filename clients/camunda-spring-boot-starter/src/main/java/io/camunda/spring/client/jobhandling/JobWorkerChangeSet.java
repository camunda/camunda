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
package io.camunda.spring.client.jobhandling;

import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.annotation.value.JobWorkerValue.FetchVariable;
import io.camunda.spring.client.annotation.value.JobWorkerValue.FieldSource;
import io.camunda.spring.client.annotation.value.JobWorkerValue.Name;
import io.camunda.spring.client.annotation.value.JobWorkerValue.Type;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public sealed interface JobWorkerChangeSet {

  /**
   * Applies the changes to the provided the {@link JobWorkerValue}
   *
   * @param jobWorkerValue the {@link JobWorkerValue} to change
   * @return true if there has been an actual change, false if not
   */
  boolean applyChanges(JobWorkerValue jobWorkerValue);

  static <T> boolean updateIfChanged(final T original, final T updated, final Consumer<T> setter) {
    if (!Objects.equals(original, updated)) {
      setter.accept(updated);
      return true;
    }
    return false;
  }

  record NoopChangeSet() implements JobWorkerChangeSet {

    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      // do nothing
      return false;
    }
  }

  record ResetChangeSet(JobWorkerValue original) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      // both are equals, no reset required
      if (Objects.equals(original, jobWorkerValue)) {
        return false;
      }
      // reset name
      boolean changed =
          updateIfChanged(original.getName(), jobWorkerValue.getName(), jobWorkerValue::setName);
      // reset type
      changed =
          updateIfChanged(original.getType(), jobWorkerValue.getType(), jobWorkerValue::setType)
              || changed;
      // reset enabled
      changed =
          updateIfChanged(
                  original.getEnabled(), jobWorkerValue.getEnabled(), jobWorkerValue::setEnabled)
              || changed;
      // reset tenant ids
      changed =
          updateIfChanged(
                  original.getTenantIds(),
                  jobWorkerValue.getTenantIds(),
                  jobWorkerValue::setTenantIds)
              || changed;
      return changed;
    }
  }

  record CreateChangeSet() implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return true;
    }
  }

  record EnabledChangeSet(boolean enabled) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(jobWorkerValue.getEnabled(), enabled, jobWorkerValue::setEnabled);
    }
  }

  record NameChangeSet(String name) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getName().value(),
          name,
          n -> jobWorkerValue.setName(new Name(n, FieldSource.FROM_ACTUATOR)));
    }
  }

  record TypeChangeSet(String type) implements JobWorkerChangeSet {

    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getType().value(),
          type,
          t -> jobWorkerValue.setType(new Type(t, FieldSource.FROM_ACTUATOR)));
    }
  }

  record ComposedChangeSet(List<JobWorkerChangeSet> changeSets) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return changeSets.stream().anyMatch(changeSet -> changeSet.applyChanges(jobWorkerValue));
    }
  }

  record TenantIdsChangeSet(List<String> tenantIds) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getTenantIds(), tenantIds, jobWorkerValue::setTenantIds);
    }
  }

  record TimeoutChangeSet(Duration timeout) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(jobWorkerValue.getTimeout(), timeout, jobWorkerValue::setTimeout);
    }
  }

  record MaxJobsActiveChangeSet(Integer maxJobsActive) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getMaxJobsActive(), maxJobsActive, jobWorkerValue::setMaxJobsActive);
    }
  }

  record RequestTimeoutChangeSet(Duration requestTimeout) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getRequestTimeout(), requestTimeout, jobWorkerValue::setRequestTimeout);
    }
  }

  record PollIntervalChangeSet(Duration pollInterval) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getPollInterval(), pollInterval, jobWorkerValue::setPollInterval);
    }
  }

  record FetchVariablesChangeSet(List<String> fetchVariables) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getFetchVariables().stream().map(FetchVariable::value).toList(),
          fetchVariables,
          fvs ->
              jobWorkerValue.setFetchVariables(
                  fvs.stream()
                      .map(fv -> new FetchVariable(fv, FieldSource.FROM_ACTUATOR))
                      .toList()));
    }
  }

  record ForceFetchAllVariablesChangeSet(Boolean forceFetchAllVariables)
      implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getForceFetchAllVariables(),
          forceFetchAllVariables,
          jobWorkerValue::setForceFetchAllVariables);
    }
  }

  record StreamEnabledChangeSet(Boolean streamEnabled) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getStreamEnabled(), streamEnabled, jobWorkerValue::setStreamEnabled);
    }
  }

  record StreamTimeoutChangeSet(Duration streamTimeout) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getStreamTimeout(), streamTimeout, jobWorkerValue::setStreamTimeout);
    }
  }

  record MaxRetriesChangeSet(Integer maxRetries) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getMaxRetries(), maxRetries, jobWorkerValue::setMaxRetries);
    }
  }
}
