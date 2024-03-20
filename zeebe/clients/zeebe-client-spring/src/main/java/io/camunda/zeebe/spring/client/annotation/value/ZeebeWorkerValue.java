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

import io.camunda.zeebe.spring.client.bean.MethodInfo;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ZeebeWorkerValue implements ZeebeAnnotationValue<MethodInfo> {

  private String type;

  private String name;

  private Long timeout;

  private Integer maxJobsActive;

  private Long requestTimeout;

  private Long pollInterval;

  private Boolean autoComplete;

  private String[] fetchVariables;

  private Boolean enabled;

  private MethodInfo methodInfo;
  private List<String> tenantIds;
  private boolean forceFetchAllVariables;

  public ZeebeWorkerValue() {}

  public ZeebeWorkerValue(
      final String type,
      final String name,
      final Long timeout,
      final Integer maxJobsActive,
      final Long requestTimeout,
      final Long pollInterval,
      final Boolean autoComplete,
      final String[] fetchVariables,
      final Boolean enabled,
      final MethodInfo methodInfo,
      final List<String> tenantIds,
      final boolean forceFetchAllVariables) {
    this.type = type;
    this.name = name;
    this.timeout = timeout;
    this.maxJobsActive = maxJobsActive;
    this.requestTimeout = requestTimeout;
    this.pollInterval = pollInterval;
    this.autoComplete = autoComplete;
    this.fetchVariables = fetchVariables;
    this.enabled = enabled;
    this.methodInfo = methodInfo;
    this.tenantIds = tenantIds;
    this.forceFetchAllVariables = forceFetchAllVariables;
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

  public Long getTimeout() {
    return timeout;
  }

  public void setTimeout(final Long timeout) {
    this.timeout = timeout;
  }

  public Integer getMaxJobsActive() {
    return maxJobsActive;
  }

  public void setMaxJobsActive(final Integer maxJobsActive) {
    this.maxJobsActive = maxJobsActive;
  }

  public Long getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Long requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Long getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(final Long pollInterval) {
    this.pollInterval = pollInterval;
  }

  public Boolean getAutoComplete() {
    return autoComplete;
  }

  public void setAutoComplete(final Boolean autoComplete) {
    this.autoComplete = autoComplete;
  }

  public String[] getFetchVariables() {
    return fetchVariables;
  }

  public void setFetchVariables(final String[] fetchVariables) {
    this.fetchVariables = fetchVariables;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  public MethodInfo getMethodInfo() {
    return methodInfo;
  }

  public void setMethodInfo(final MethodInfo methodInfo) {
    this.methodInfo = methodInfo;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public boolean isForceFetchAllVariables() {
    return forceFetchAllVariables;
  }

  public void setForceFetchAllVariables(final boolean forceFetchAllVariables) {
    this.forceFetchAllVariables = forceFetchAllVariables;
  }

  @Override
  public MethodInfo getBeanInfo() {
    return methodInfo;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            type,
            name,
            timeout,
            maxJobsActive,
            requestTimeout,
            pollInterval,
            autoComplete,
            enabled,
            methodInfo,
            tenantIds,
            forceFetchAllVariables);
    result = 31 * result + Arrays.hashCode(fetchVariables);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeWorkerValue that = (ZeebeWorkerValue) o;
    return forceFetchAllVariables == that.forceFetchAllVariables
        && Objects.equals(type, that.type)
        && Objects.equals(name, that.name)
        && Objects.equals(timeout, that.timeout)
        && Objects.equals(maxJobsActive, that.maxJobsActive)
        && Objects.equals(requestTimeout, that.requestTimeout)
        && Objects.equals(pollInterval, that.pollInterval)
        && Objects.equals(autoComplete, that.autoComplete)
        && Arrays.equals(fetchVariables, that.fetchVariables)
        && Objects.equals(enabled, that.enabled)
        && Objects.equals(methodInfo, that.methodInfo)
        && Objects.equals(tenantIds, that.tenantIds);
  }

  @Override
  public String toString() {
    return "ZeebeWorkerValue{"
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
        + Arrays.toString(fetchVariables)
        + ", enabled="
        + enabled
        + ", methodInfo="
        + methodInfo
        + ", tenantIds="
        + tenantIds
        + ", forceFetchAllVariables="
        + forceFetchAllVariables
        + '}';
  }
}
