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
package io.camunda.zeebe.spring.client.annotation.value;

import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.*;
import io.camunda.client.bean.MethodInfo;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * @deprecated use {@link JobWorkerValue} instead
 */
@Deprecated
public class ZeebeWorkerValue {
  private final JobWorkerValue jobWorkerValue;

  public ZeebeWorkerValue(final JobWorkerValue jobWorkerValue) {
    this.jobWorkerValue = jobWorkerValue;
  }

  public ZeebeWorkerValue() {
    this(new JobWorkerValue());
  }

  public ZeebeWorkerValue(
      final String type,
      final String name,
      final Duration timeout,
      final Integer maxJobsActive,
      final Duration requestTimeout,
      final Duration pollInterval,
      final Boolean autoComplete,
      final List<String> fetchVariables,
      final Boolean enabled,
      final MethodInfo methodInfo,
      final List<String> tenantIds,
      final Boolean forceFetchAllVariables,
      final Boolean streamEnabled,
      final Duration streamTimeout,
      final int maxRetries) {
    this();
    jobWorkerValue.setType(new FromLegacy<>(type));
    jobWorkerValue.setName(new FromLegacy<>(name));
    jobWorkerValue.setTimeout(new FromLegacy<>(timeout));
    jobWorkerValue.setMaxJobsActive(new FromLegacy<>(maxJobsActive));
    jobWorkerValue.setRequestTimeout(new FromLegacy<>(requestTimeout));
    jobWorkerValue.setPollInterval(new FromLegacy<>(pollInterval));
    jobWorkerValue.setAutoComplete(new FromLegacy<>(autoComplete));
    jobWorkerValue.setFetchVariables(
        fetchVariables.stream()
            .map(fetchVariable -> (SourceAware<String>) new FromLegacy<>(fetchVariable))
            .toList());
    jobWorkerValue.setEnabled(new FromLegacy<>(enabled));
    jobWorkerValue.setMethodInfo(methodInfo);
    jobWorkerValue.setTenantIds(
        tenantIds.stream()
            .map(tenantId -> (SourceAware<String>) new FromLegacy<>(tenantId))
            .toList());
    jobWorkerValue.setForceFetchAllVariables(new FromLegacy<>(forceFetchAllVariables));
    jobWorkerValue.setStreamEnabled(new FromLegacy<>(streamEnabled));
    jobWorkerValue.setStreamTimeout(new FromLegacy<>(streamTimeout));
    jobWorkerValue.setMaxRetries(new FromLegacy<>(maxRetries));
  }

  public JobWorkerValue getJobWorkerValue() {
    return jobWorkerValue;
  }

  public String getType() {
    return jobWorkerValue.getType().value();
  }

  public void setType(final String type) {
    jobWorkerValue.setType(new FromLegacy<>(type));
  }

  public String getName() {
    return jobWorkerValue.getName().value();
  }

  public void setName(final String name) {
    jobWorkerValue.setName(new FromLegacy<>(name));
  }

  public Duration getTimeout() {
    return jobWorkerValue.getTimeout().value();
  }

  public void setTimeout(final Duration timeout) {
    jobWorkerValue.setTimeout(new FromLegacy<>(timeout));
  }

  public Integer getMaxJobsActive() {
    return jobWorkerValue.getMaxJobsActive().value();
  }

  public void setMaxJobsActive(final Integer maxJobsActive) {
    jobWorkerValue.setMaxJobsActive(new FromLegacy<>(maxJobsActive));
  }

  public Duration getRequestTimeout() {
    return jobWorkerValue.getRequestTimeout().value();
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    jobWorkerValue.setRequestTimeout(new FromLegacy<>(requestTimeout));
  }

  public Duration getPollInterval() {
    return jobWorkerValue.getPollInterval().value();
  }

  public void setPollInterval(final Duration pollInterval) {
    jobWorkerValue.setPollInterval(new FromLegacy<>(pollInterval));
  }

  public Boolean getAutoComplete() {
    return jobWorkerValue.getAutoComplete().value();
  }

  public void setAutoComplete(final Boolean autoComplete) {
    jobWorkerValue.setAutoComplete(new FromLegacy<>(autoComplete));
  }

  public List<String> getFetchVariables() {
    return jobWorkerValue.getFetchVariables().stream().map(SourceAware::value).toList();
  }

  public void setFetchVariables(final List<String> fetchVariables) {
    jobWorkerValue.setFetchVariables(
        fetchVariables.stream()
            .map(fetchVariable -> (SourceAware<String>) new FromLegacy<>(fetchVariable))
            .toList());
  }

  public Boolean getEnabled() {
    return jobWorkerValue.getEnabled().value();
  }

  public void setEnabled(final Boolean enabled) {
    jobWorkerValue.setEnabled(new FromLegacy<>(enabled));
  }

  @Deprecated
  public MethodInfo getMethodInfo() {
    return jobWorkerValue.getMethodInfo();
  }

  @Deprecated
  public void setMethodInfo(final MethodInfo methodInfo) {
    jobWorkerValue.setMethodInfo(methodInfo);
  }

  public List<String> getTenantIds() {
    return jobWorkerValue.getTenantIds().stream().map(SourceAware::value).toList();
  }

  public void setTenantIds(final List<String> tenantIds) {
    jobWorkerValue.setTenantIds(
        tenantIds.stream()
            .map(tenantId -> (SourceAware<String>) new FromLegacy<>(tenantId))
            .toList());
  }

  public Boolean getForceFetchAllVariables() {
    return jobWorkerValue.getForceFetchAllVariables().value();
  }

  public void setForceFetchAllVariables(final Boolean forceFetchAllVariables) {
    jobWorkerValue.setForceFetchAllVariables(new FromLegacy<>(forceFetchAllVariables));
  }

  public Boolean getStreamEnabled() {
    return jobWorkerValue.getStreamEnabled().value();
  }

  public void setStreamEnabled(final Boolean streamEnabled) {
    jobWorkerValue.setStreamEnabled(new FromLegacy<>(streamEnabled));
  }

  public Duration getStreamTimeout() {
    return jobWorkerValue.getStreamTimeout().value();
  }

  public void setStreamTimeout(final Duration streamTimeout) {
    jobWorkerValue.setStreamTimeout(new FromLegacy<>(streamTimeout));
  }

  public int getMaxRetries() {
    return jobWorkerValue.getMaxRetries().value();
  }

  public void setMaxRetries(final int maxRetries) {
    jobWorkerValue.setMaxRetries(new FromLegacy<>(maxRetries));
  }

  @Deprecated
  public MethodInfo getBeanInfo() {
    return jobWorkerValue.getMethodInfo();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(jobWorkerValue);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeWorkerValue that = (ZeebeWorkerValue) o;
    return Objects.equals(jobWorkerValue, that.jobWorkerValue);
  }

  @Override
  public String toString() {
    return "ZeebeWorkerValue{" + "jobWorkerValue=" + jobWorkerValue + '}';
  }
}
