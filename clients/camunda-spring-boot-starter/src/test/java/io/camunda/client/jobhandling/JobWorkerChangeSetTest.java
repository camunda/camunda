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
import io.camunda.client.jobhandling.JobWorkerChangeSet.FetchVariablesChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.ForceFetchAllVariablesChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.MaxJobsActiveChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.ResetChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.TenantIdsChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.TimeoutChangeSet;
import java.time.Duration;
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
  void shouldUpdateTenantIdsToLess() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setTenantIds(List.of(new FromAnnotation<>("abc"), new FromAnnotation<>("def")));
    final TenantIdsChangeSet changeSet = new TenantIdsChangeSet(List.of("ghi"));
    changeSet.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getTenantIds())
        .isEqualTo(
            List.of(
                new FromRuntimeOverride<>("ghi", new FromAnnotation<>("abc")),
                new FromRuntimeOverride<>(null, new FromAnnotation<>("def"))));
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

  @Test
  void shouldApplyForceFetchAllVariables() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setFetchVariables(List.of(new FromAnnotation<>("abc")));
    final ForceFetchAllVariablesChangeSet changeSet = new ForceFetchAllVariablesChangeSet(true);
    changeSet.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getFetchVariables())
        .isEqualTo(List.of(new FromRuntimeOverride<>(null, new FromAnnotation<>("abc"))));
  }

  @Test
  void shouldNotApplyFetchVariablesIfForceFetchAllVariables() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setForceFetchAllVariables(new FromAnnotation<>(true));
    final FetchVariablesChangeSet changeSet = new FetchVariablesChangeSet(List.of("foo", "bar"));
    changeSet.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getFetchVariables()).isEqualTo(List.of());
  }

  @Test
  void shouldWorkOnMultipleChanges() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setTimeout(new FromAnnotation<>(Duration.ofSeconds(10)));
    final TimeoutChangeSet changeSet0 = new TimeoutChangeSet(Duration.ofSeconds(30));
    changeSet0.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getTimeout())
        .isEqualTo(
            new FromRuntimeOverride<>(
                Duration.ofSeconds(30), new FromAnnotation<>(Duration.ofSeconds(10))));
    final TimeoutChangeSet changeSet1 = new TimeoutChangeSet(Duration.ofSeconds(20));
    changeSet1.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getTimeout())
        .isEqualTo(
            new FromRuntimeOverride<>(
                Duration.ofSeconds(20), new FromAnnotation<>(Duration.ofSeconds(10))));
  }

  @Test
  void shouldWorkOnMultipleListChanges() {
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setTenantIds(List.of(new FromAnnotation<>("abc")));
    final TenantIdsChangeSet changeSet0 = new TenantIdsChangeSet(List.of("def", "ghi"));
    changeSet0.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getTenantIds())
        .isEqualTo(
            List.of(
                new FromRuntimeOverride<>("def", new FromAnnotation<>("abc")),
                new FromRuntimeOverride<>("ghi", new Empty<>())));
    final TenantIdsChangeSet changeSet1 = new TenantIdsChangeSet(List.of("xyz"));
    changeSet1.applyChanges(jobWorkerValue);
    assertThat(jobWorkerValue.getTenantIds())
        .isEqualTo(List.of(new FromRuntimeOverride<>("xyz", new FromAnnotation<>("abc"))));
  }
}
