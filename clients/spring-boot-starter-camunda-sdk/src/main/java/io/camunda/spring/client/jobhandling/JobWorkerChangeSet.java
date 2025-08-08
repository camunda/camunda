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

public sealed interface JobWorkerChangeSet {
  void applyChanges(JobWorkerValue jobWorkerValue);

  record NoopChangeSet() implements JobWorkerChangeSet {

    @Override
    public void applyChanges(final JobWorkerValue jobWorkerValue) {
      // do nothing
    }
  }

  record ResetChangeSet(JobWorkerValue original) implements JobWorkerChangeSet {
    @Override
    public void applyChanges(final JobWorkerValue jobWorkerValue) {}
  }

  record EnabledChangeSet(boolean enabled) implements JobWorkerChangeSet {
    @Override
    public void applyChanges(final JobWorkerValue jobWorkerValue) {
      jobWorkerValue.setEnabled(enabled);
    }
  }

  record NameChangeSet(String name) implements JobWorkerChangeSet {
    @Override
    public void applyChanges(final JobWorkerValue jobWorkerValue) {
      jobWorkerValue.setName(name);
    }
  }

  record TypeChangeSet(String type) implements JobWorkerChangeSet {

    @Override
    public void applyChanges(final JobWorkerValue jobWorkerValue) {
      jobWorkerValue.setType(type);
    }
  }

  record ComposedChangeSet(List<JobWorkerChangeSet> changeSets) implements JobWorkerChangeSet {
    @Override
    public void applyChanges(final JobWorkerValue jobWorkerValue) {
      changeSets.forEach(changeSet -> changeSet.applyChanges(jobWorkerValue));
    }
  }
}
