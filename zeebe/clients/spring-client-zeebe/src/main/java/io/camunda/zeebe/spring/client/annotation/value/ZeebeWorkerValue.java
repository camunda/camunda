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
      String type,
      String name,
      Long timeout,
      Integer maxJobsActive,
      Long requestTimeout,
      Long pollInterval,
      Boolean autoComplete,
      String[] fetchVariables,
      Boolean enabled,
      MethodInfo methodInfo,
      List<String> tenantIds,
      boolean forceFetchAllVariables) {
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

  public void setType(String type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getTimeout() {
    return timeout;
  }

  public void setTimeout(Long timeout) {
    this.timeout = timeout;
  }

  public Integer getMaxJobsActive() {
    return maxJobsActive;
  }

  public void setMaxJobsActive(Integer maxJobsActive) {
    this.maxJobsActive = maxJobsActive;
  }

  public Long getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Long requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Long getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(Long pollInterval) {
    this.pollInterval = pollInterval;
  }

  public Boolean getAutoComplete() {
    return autoComplete;
  }

  public void setAutoComplete(Boolean autoComplete) {
    this.autoComplete = autoComplete;
  }

  public String[] getFetchVariables() {
    return fetchVariables;
  }

  public void setFetchVariables(String[] fetchVariables) {
    this.fetchVariables = fetchVariables;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public MethodInfo getMethodInfo() {
    return methodInfo;
  }

  public void setMethodInfo(MethodInfo methodInfo) {
    this.methodInfo = methodInfo;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public boolean isForceFetchAllVariables() {
    return forceFetchAllVariables;
  }

  public void setForceFetchAllVariables(boolean forceFetchAllVariables) {
    this.forceFetchAllVariables = forceFetchAllVariables;
  }

  @Override
  public MethodInfo getBeanInfo() {
    return methodInfo;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ZeebeWorkerValue that = (ZeebeWorkerValue) o;
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
}
