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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.Empty;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.FromAnnotation;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.FromOverrideProperty;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.FromRuntimeOverride;
import io.camunda.client.jobhandling.JobWorkerChangeSet.MaxJobsActiveChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.ResetChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.TenantIdsChangeSet;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JobWorkerChangeSetTest {
  @Test
  void shouldUpdateTenantIds() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setTenantIds(List.of(new FromAnnotation<>("abc")));
    final TenantIdsChangeSet changeSet = new TenantIdsChangeSet(List.of("def", "ghi"));
    changeSet.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getTenantIds())
        .isEqualTo(
            List.of(
                new FromRuntimeOverride<>("def", new FromAnnotation<>("abc")),
                new FromRuntimeOverride<>("ghi", new Empty<>())));
  }

  @Test
  void shouldResetTenantIds() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setTenantIds(
        List.of(
            new FromRuntimeOverride<>("abc", new FromAnnotation<>("def")),
            new FromRuntimeOverride<>("ghi", new Empty<>())));
    final ResetChangeSet changeSet = new ResetChangeSet();
    changeSet.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getTenantIds()).isEqualTo(List.of(new FromAnnotation<>("def")));
  }

  @Test
  void shouldResetTenantIdsFromAnnotation() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setTenantIds(List.of(new FromAnnotation<>("def")));
    final ResetChangeSet changeSet = new ResetChangeSet();
    changeSet.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getTenantIds()).isEqualTo(List.of(new FromAnnotation<>("def")));
  }

  @Test
  void shouldUpdateMaxJobsActive() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMaxJobsActive(new FromOverrideProperty<>(10));
    final MaxJobsActiveChangeSet changeSet = new MaxJobsActiveChangeSet(20);
    changeSet.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getMaxJobsActive())
        .isEqualTo(new FromRuntimeOverride<>(20, new FromOverrideProperty<>(10)));
  }

  @Test
  void shouldResetMaxJobsActive() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMaxJobsActive(new FromRuntimeOverride<>(20, new FromOverrideProperty<>(10)));
    final ResetChangeSet changeSet = new ResetChangeSet();
    changeSet.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getMaxJobsActive()).isEqualTo(new FromOverrideProperty<>(10));
  }

  @Test
  void shouldResetMaxJobsActiveFromOverrideProperty() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMaxJobsActive(new FromOverrideProperty<>(10));
    final ResetChangeSet changeSet = new ResetChangeSet();
    changeSet.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getMaxJobsActive()).isEqualTo(new FromOverrideProperty<>(10));
  }
}
