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
import io.camunda.client.api.command.enums.TenantFilter;
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
      final SourceAware<T> originalToSet =
          original instanceof final FromRuntimeOverride<T> fromRuntimeOverride
              ? fromRuntimeOverride.original()
              : original;
      setter.accept(new FromRuntimeOverride<>(updated, originalToSet));
      return true;
    }
    return false;
  }

  static <T> boolean updateIfChangedList(
      final List<SourceAware<T>> original,
      final List<T> updated,
      final Consumer<List<SourceAware<T>>> setter) {
    final List<T> updatedModifiable = new ArrayList<>(updated);
    final List<SourceAware<T>> originalModifiable = new ArrayList<>(original);
    if (!Objects.equals(
        originalModifiable.stream().map(SourceAware::value).toList(), updatedModifiable)) {
      final int listSize = Math.max(originalModifiable.size(), updatedModifiable.size());
      while (originalModifiable.size() < listSize || updatedModifiable.size() < listSize) {
        if (originalModifiable.size() < listSize) {
          originalModifiable.add(new Empty<>());
        }
        if (updatedModifiable.size() < listSize) {
          updatedModifiable.add(null);
        }
      }
      setter.accept(
          IntStream.range(0, listSize)
              .mapToObj(
                  i -> {
                    final SourceAware<T> originalElement = originalModifiable.get(i);
                    final SourceAware<T> originalToSet =
                        originalElement instanceof final FromRuntimeOverride<T> fromRuntimeOverride
                            ? fromRuntimeOverride.original()
                            : originalElement;
                    return (SourceAware<T>)
                        new FromRuntimeOverride<>(updatedModifiable.get(i), originalToSet);
                  })
              .filter(
                  sa ->
                      sa.value() != null
                          || ((FromRuntimeOverride<T>) sa).original().value() != null)
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
      final Boolean forceFetchAllVariables = jobWorkerValue.getForceFetchAllVariables().value();
      if (forceFetchAllVariables != null && forceFetchAllVariables) {
        // ignore changes to fetchVariables as long as force fetch variables is true
        return false;
      }
      return updateIfChangedList(
          jobWorkerValue.getFetchVariables(), fetchVariables, jobWorkerValue::setFetchVariables);
    }
  }

  record ForceFetchAllVariablesChangeSet(Boolean forceFetchAllVariables)
      implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      final boolean changed =
          updateIfChanged(
              jobWorkerValue.getForceFetchAllVariables(),
              forceFetchAllVariables,
              jobWorkerValue::setForceFetchAllVariables);
      if (changed) {
        if (forceFetchAllVariables) {
          updateIfChangedList(
              jobWorkerValue.getFetchVariables(), List.of(), jobWorkerValue::setFetchVariables);
        } else {
          resetList(jobWorkerValue.getFetchVariables(), jobWorkerValue::setFetchVariables);
        }
      }
      return changed;
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

  record RetryBackoffChangeSet(Duration retryBackoff) implements JobWorkerChangeSet {
    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getRetryBackoff(), retryBackoff, jobWorkerValue::setRetryBackoff);
    }
  }

  record TenantFilterChangeSet(TenantFilter tenantFilter) implements JobWorkerChangeSet {

    @Override
    public boolean applyChanges(final JobWorkerValue jobWorkerValue) {
      return updateIfChanged(
          jobWorkerValue.getTenantFilter(), tenantFilter, jobWorkerValue::setTenantFilter);
    }
  }
}
