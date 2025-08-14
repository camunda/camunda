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
}
