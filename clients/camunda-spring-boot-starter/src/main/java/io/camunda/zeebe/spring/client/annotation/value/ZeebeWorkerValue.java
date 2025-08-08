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

import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.bean.MethodInfo;
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
    jobWorkerValue.setType(type);
    jobWorkerValue.setName(name);
    jobWorkerValue.setTimeout(timeout);
    jobWorkerValue.setMaxJobsActive(maxJobsActive);
    jobWorkerValue.setRequestTimeout(requestTimeout);
    jobWorkerValue.setPollInterval(pollInterval);
    jobWorkerValue.setAutoComplete(autoComplete);
    jobWorkerValue.setFetchVariables(fetchVariables);
    jobWorkerValue.setEnabled(enabled);
    jobWorkerValue.setMethodInfo(methodInfo);
    jobWorkerValue.setTenantIds(tenantIds);
    jobWorkerValue.setForceFetchAllVariables(forceFetchAllVariables);
    jobWorkerValue.setStreamEnabled(streamEnabled);
    jobWorkerValue.setStreamTimeout(streamTimeout);
    jobWorkerValue.setMaxRetries(maxRetries);
  }

  public JobWorkerValue getJobWorkerValue() {
    return jobWorkerValue;
  }

  public String getType() {
    return jobWorkerValue.getType();
  }

  public void setType(final String type) {
    jobWorkerValue.setType(type);
  }

  public String getName() {
    return jobWorkerValue.getName();
  }

  public void setName(final String name) {
    jobWorkerValue.setName(name);
  }

  public Duration getTimeout() {
    return jobWorkerValue.getTimeout();
  }

  public void setTimeout(final Duration timeout) {
    jobWorkerValue.setTimeout(timeout);
  }

  public Integer getMaxJobsActive() {
    return jobWorkerValue.getMaxJobsActive();
  }

  public void setMaxJobsActive(final Integer maxJobsActive) {
    jobWorkerValue.setMaxJobsActive(maxJobsActive);
  }

  public Duration getRequestTimeout() {
    return jobWorkerValue.getRequestTimeout();
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    jobWorkerValue.setRequestTimeout(requestTimeout);
  }

  public Duration getPollInterval() {
    return jobWorkerValue.getPollInterval();
  }

  public void setPollInterval(final Duration pollInterval) {
    jobWorkerValue.setPollInterval(pollInterval);
  }

  public Boolean getAutoComplete() {
    return jobWorkerValue.getAutoComplete();
  }

  public void setAutoComplete(final Boolean autoComplete) {
    jobWorkerValue.setAutoComplete(autoComplete);
  }

  public List<String> getFetchVariables() {
    return jobWorkerValue.getFetchVariables();
  }

  public void setFetchVariables(final List<String> fetchVariables) {
    jobWorkerValue.setFetchVariables(fetchVariables);
  }

  public Boolean getEnabled() {
    return jobWorkerValue.getEnabled();
  }

  public void setEnabled(final Boolean enabled) {
    jobWorkerValue.setEnabled(enabled);
  }

  public MethodInfo getMethodInfo() {
    return jobWorkerValue.getMethodInfo();
  }

  public void setMethodInfo(final MethodInfo methodInfo) {
    jobWorkerValue.setMethodInfo(methodInfo);
  }

  public List<String> getTenantIds() {
    return jobWorkerValue.getTenantIds();
  }

  public void setTenantIds(final List<String> tenantIds) {
    jobWorkerValue.setTenantIds(tenantIds);
  }

  public Boolean getForceFetchAllVariables() {
    return jobWorkerValue.getForceFetchAllVariables();
  }

  public void setForceFetchAllVariables(final Boolean forceFetchAllVariables) {
    jobWorkerValue.setForceFetchAllVariables(forceFetchAllVariables);
  }

  public Boolean getStreamEnabled() {
    return jobWorkerValue.getStreamEnabled();
  }

  public void setStreamEnabled(final Boolean streamEnabled) {
    jobWorkerValue.setStreamEnabled(streamEnabled);
  }

  public Duration getStreamTimeout() {
    return jobWorkerValue.getStreamTimeout();
  }

  public void setStreamTimeout(final Duration streamTimeout) {
    jobWorkerValue.setStreamTimeout(streamTimeout);
  }

  public int getMaxRetries() {
    return jobWorkerValue.getMaxRetries();
  }

  public void setMaxRetries(final int maxRetries) {
    jobWorkerValue.setMaxRetries(maxRetries);
  }

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
