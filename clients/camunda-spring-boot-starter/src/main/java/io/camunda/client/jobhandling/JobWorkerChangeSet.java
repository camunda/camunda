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
package io.camunda.client.jobhandling;

import static java.util.function.Predicate.not;

import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public sealed interface JobWorkerChangeSet {

  /**
   * Applies the changes to the provided the {@link JobWorkerValue}
   *
   * @param jobWorkerValue the {@link JobWorkerValue} to change
   * @return true if there has been an actual change, false if not
   */
  boolean applyChanges(JobWorkerValue jobWorkerValue);

  static <T> boolean updateIfChanged(
      final SourceAware<T> original, final T updated, final Consumer<SourceAware<T>> setter) {
    if (!Objects.equals(original.value(), updated)) {
      setter.accept(new FromRuntimeOverride<>(updated, original));
      return true;
    }
    return false;
  }

  static <T> boolean updateIfChangedList(
      final List<SourceAware<T>> original,
      final List<T> updated,
      final Consumer<List<SourceAware<T>>> setter) {
    final List<SourceAware<T>> originalModifiable = new ArrayList<>(original);
    if (!Objects.equals(originalModifiable.stream().map(SourceAware::value).toList(), updated)) {
      final int listSize = Math.max(originalModifiable.size(), updated.size());
      while (originalModifiable.size() < listSize || updated.size() < listSize) {
        if (originalModifiable.size() < listSize) {
          originalModifiable.add(new Empty<>());
        }
        if (updated.size() < listSize) {
          updated.add(null);
        }
      }
      setter.accept(
          IntStream.range(0, listSize)
              .mapToObj(
                  i ->
                      (SourceAware<T>)
                          new FromRuntimeOverride<>(updated.get(i), originalModifiable.get(i)))
              .toList());
      return true;
    }
    return false;
  }

  private static <T> boolean reset(
      final SourceAware<T> field, final Consumer<SourceAware<T>> setter) {
    if (field instanceof final SourceAware.FromRuntimeOverride<T> fromRuntimeOverride) {
      setter.accept(fromRuntimeOverride.original());
      return true;
    }
    return false;
  }

  private static <T> boolean resetList(
      final List<SourceAware<T>> field, final Consumer<List<SourceAware<T>>> setter) {
    final boolean changed = field.stream().anyMatch(FromRuntimeOverride.class::isInstance);
    setter.accept(
        field.stream()
            .map(
                sourceAware ->
                    sourceAware instanceof final SourceAware.FromRuntimeOverride<T> runtimeOverride
                        ? runtimeOverride.original()
                        : sourceAware)
            .filter(not(Empty.class::isInstance))
            .toList());
    return changed;
  }

  record NoopChangeSet() implements JobWorkerChangeSet {

    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      // do nothing
      return false;
    }
  }

  record CloseChangeSet() implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      new ResetChangeSet().applyChanges(jobWorkerValue);
      return true;
    }
  }

  record ResetChangeSet() implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return Stream.of(
              reset(jobWorkerValue.getName(), jobWorkerValue::setName),
              reset(jobWorkerValue.getType(), jobWorkerValue::setType),
              reset(jobWorkerValue.getTimeout(), jobWorkerValue::setTimeout),
              reset(jobWorkerValue.getMaxJobsActive(), jobWorkerValue::setMaxJobsActive),
              reset(jobWorkerValue.getRequestTimeout(), jobWorkerValue::setRequestTimeout),
              reset(jobWorkerValue.getPollInterval(), jobWorkerValue::setPollInterval),
              resetList(jobWorkerValue.getFetchVariables(), jobWorkerValue::setFetchVariables),
              reset(jobWorkerValue.getEnabled(), jobWorkerValue::setEnabled),
              resetList(jobWorkerValue.getTenantIds(), jobWorkerValue::setTenantIds),
              reset(
                  jobWorkerValue.getForceFetchAllVariables(),
                  jobWorkerValue::setForceFetchAllVariables),
              reset(jobWorkerValue.getStreamEnabled(), jobWorkerValue::setStreamEnabled),
              reset(jobWorkerValue.getStreamTimeout(), jobWorkerValue::setStreamTimeout),
              reset(jobWorkerValue.getMaxRetries(), jobWorkerValue::setMaxRetries))
          .reduce(false, Boolean::logicalOr);
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
      return updateIfChanged(jobWorkerValue.getName(), name, jobWorkerValue::setName);
    }
  }

  record TypeChangeSet(String type) implements JobWorkerChangeSet {

    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(jobWorkerValue.getType(), type, jobWorkerValue::setType);
    }
  }

  record ComposedChangeSet(List<JobWorkerChangeSet> changeSets) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return changeSets.stream()
          .map(changeSet -> changeSet.applyChanges(jobWorkerValue))
          .reduce(false, Boolean::logicalOr);
    }
  }

  record TenantIdsChangeSet(List<String> tenantIds) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChangedList(
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
      return updateIfChangedList(
          jobWorkerValue.getFetchVariables(), fetchVariables, jobWorkerValue::setFetchVariables);
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
