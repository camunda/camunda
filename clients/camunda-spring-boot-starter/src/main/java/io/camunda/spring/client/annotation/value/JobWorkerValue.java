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
package io.camunda.spring.client.annotation.value;

import io.camunda.spring.client.bean.MethodInfo;
import io.camunda.spring.client.jobhandling.JobHandlerFactory;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.util.function.ThrowingFunction;

public class JobWorkerValue implements Cloneable {
  private String type;
  private String name;
  private Duration timeout;
  private Integer maxJobsActive;
  private Duration requestTimeout;
  private Duration pollInterval;
  private Boolean autoComplete;
  private List<String> fetchVariables;
  private Boolean enabled;
  private ThrowingFunction<Object[], Object> method;
  private List<String> tenantIds;
  private Boolean forceFetchAllVariables;
  private Boolean streamEnabled;
  private Duration streamTimeout;
  private Integer maxRetries;
  private JobHandlerFactory jobHandlerFactory;

  @Deprecated(forRemoval = true)
  private MethodInfo methodInfo;

  public JobWorkerValue() {}

  public JobWorkerValue(
      final String type,
      final String name,
      final Duration timeout,
      final Integer maxJobsActive,
      final Duration requestTimeout,
      final Duration pollInterval,
      final Boolean autoComplete,
      final List<String> fetchVariables,
      final Boolean enabled,
      final ThrowingFunction<Object[], Object> method,
      final List<String> tenantIds,
      final Boolean forceFetchAllVariables,
      final Boolean streamEnabled,
      final Duration streamTimeout,
      final Integer maxRetries,
      final JobHandlerFactory jobHandlerFactory) {
    this.type = type;
    this.name = name;
    this.timeout = timeout;
    this.maxJobsActive = maxJobsActive;
    this.requestTimeout = requestTimeout;
    this.pollInterval = pollInterval;
    this.autoComplete = autoComplete;
    this.fetchVariables = fetchVariables;
    this.enabled = enabled;
    this.method = method;
    this.tenantIds = tenantIds;
    this.forceFetchAllVariables = forceFetchAllVariables;
    this.streamEnabled = streamEnabled;
    this.streamTimeout = streamTimeout;
    this.maxRetries = maxRetries;
    this.jobHandlerFactory = jobHandlerFactory;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(final Duration timeout) {
    this.timeout = timeout;
  }

  public Integer getMaxJobsActive() {
    return maxJobsActive;
  }

  public void setMaxJobsActive(final Integer maxJobsActive) {
    this.maxJobsActive = maxJobsActive;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Duration getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(final Duration pollInterval) {
    this.pollInterval = pollInterval;
  }

  public Boolean getAutoComplete() {
    return autoComplete;
  }

  public void setAutoComplete(final Boolean autoComplete) {
    this.autoComplete = autoComplete;
  }

  public List<String> getFetchVariables() {
    return fetchVariables;
  }

  public void setFetchVariables(final List<String> fetchVariables) {
    this.fetchVariables = fetchVariables;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  public ThrowingFunction<Object[], Object> getMethod() {
    return method;
  }

  public void setMethod(final ThrowingFunction<Object[], Object> method) {
    this.method = method;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public Boolean getForceFetchAllVariables() {
    return forceFetchAllVariables;
  }

  public void setForceFetchAllVariables(final Boolean forceFetchAllVariables) {
    this.forceFetchAllVariables = forceFetchAllVariables;
  }

  public Boolean getStreamEnabled() {
    return streamEnabled;
  }

  public void setStreamEnabled(final Boolean streamEnabled) {
    this.streamEnabled = streamEnabled;
  }

  public Duration getStreamTimeout() {
    return streamTimeout;
  }

  public void setStreamTimeout(final Duration streamTimeout) {
    this.streamTimeout = streamTimeout;
  }

  public Integer getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(final Integer maxRetries) {
    this.maxRetries = maxRetries;
  }

  public JobHandlerFactory getJobWorkerFactory() {
    return jobHandlerFactory;
  }

  public void setJobWorkerFactory(final JobHandlerFactory jobHandlerFactory) {
    this.jobHandlerFactory = jobHandlerFactory;
  }

  @Deprecated(forRemoval = true)
  public MethodInfo getMethodInfo() {
    return methodInfo;
  }

  @Deprecated(forRemoval = true)
  public void setMethodInfo(final MethodInfo methodInfo) {
    this.methodInfo = methodInfo;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        type,
        name,
        timeout,
        maxJobsActive,
        requestTimeout,
        pollInterval,
        autoComplete,
        fetchVariables,
        enabled,
        method,
        tenantIds,
        forceFetchAllVariables,
        streamEnabled,
        streamTimeout,
        maxRetries,
        jobHandlerFactory);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobWorkerValue that = (JobWorkerValue) o;
    return Objects.equals(type, that.type)
        && Objects.equals(name, that.name)
        && Objects.equals(timeout, that.timeout)
        && Objects.equals(maxJobsActive, that.maxJobsActive)
        && Objects.equals(requestTimeout, that.requestTimeout)
        && Objects.equals(pollInterval, that.pollInterval)
        && Objects.equals(autoComplete, that.autoComplete)
        && Objects.equals(fetchVariables, that.fetchVariables)
        && Objects.equals(enabled, that.enabled)
        && Objects.equals(method, that.method)
        && Objects.equals(tenantIds, that.tenantIds)
        && Objects.equals(forceFetchAllVariables, that.forceFetchAllVariables)
        && Objects.equals(streamEnabled, that.streamEnabled)
        && Objects.equals(streamTimeout, that.streamTimeout)
        && Objects.equals(maxRetries, that.maxRetries)
        && Objects.equals(jobHandlerFactory, that.jobHandlerFactory);
  }

  @Override
  public JobWorkerValue clone() {
    try {
      final JobWorkerValue clone = (JobWorkerValue) super.clone();
      // TODO: copy mutable state here, so the clone can't change the internals of the original
      return clone;
    } catch (final CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  @Override
  public String toString() {
    return "JobWorkerValue{"
        + "type='"
        + type
        + '\''
        + ", name='"
        + name
        + '\''
        + ", timeout="
        + timeout
        + ", maxJobsActive="
        + maxJobsActive
        + ", requestTimeout="
        + requestTimeout
        + ", pollInterval="
        + pollInterval
        + ", autoComplete="
        + autoComplete
        + ", fetchVariables="
        + fetchVariables
        + ", enabled="
        + enabled
        + ", method="
        + method
        + ", tenantIds="
        + tenantIds
        + ", forceFetchAllVariables="
        + forceFetchAllVariables
        + ", streamEnabled="
        + streamEnabled
        + ", streamTimeout="
        + streamTimeout
        + ", maxRetries="
        + maxRetries
        + ", jobWorkerFactory="
        + jobHandlerFactory
        + '}';
  }
}
