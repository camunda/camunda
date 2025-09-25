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
package io.camunda.client.annotation.value;

import static io.camunda.client.annotation.value.JobWorkerValue.FieldSource.EMPTY_FIELD;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.bean.MethodInfo;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class JobWorkerValue implements Cloneable {
  private Type type = new Type(null, EMPTY_FIELD);
  private Name name = new Name(null, EMPTY_FIELD);
  private Duration timeout;
  private Integer maxJobsActive;
  private Duration requestTimeout;
  private Duration pollInterval;
  private List<FetchVariable> fetchVariables;
  private Boolean enabled;
  private List<String> tenantIds;
  private Boolean forceFetchAllVariables;
  private Boolean streamEnabled;
  private Duration streamTimeout;
  private Integer maxRetries = 0;
  private Duration retryBackoff;
  // cannot be changed from change set
  private Boolean autoComplete;

  @Deprecated(forRemoval = true)
  @JsonIgnore
  private MethodInfo methodInfo;
  private Duration retryBackoff;

  public JobWorkerValue() {}

  public JobWorkerValue(
      final Type type,
      final Name name,
      final Duration timeout,
      final Integer maxJobsActive,
      final Duration requestTimeout,
      final Duration pollInterval,
      final Boolean autoComplete,
      final List<FetchVariable> fetchVariables,
      final Boolean enabled,
      final List<String> tenantIds,
      final Boolean forceFetchAllVariables,
      final Boolean streamEnabled,
      final Duration streamTimeout,
      final Integer maxRetries,
      final Duration retryBackoff) {
    this.type = type;
    this.name = name;
    this.timeout = timeout;
    this.maxJobsActive = maxJobsActive;
    this.requestTimeout = requestTimeout;
    this.pollInterval = pollInterval;
    this.autoComplete = autoComplete;
    this.fetchVariables = Collections.unmodifiableList(fetchVariables);
    this.enabled = enabled;
    this.tenantIds = Collections.unmodifiableList(tenantIds);
    this.forceFetchAllVariables = forceFetchAllVariables;
    this.streamEnabled = streamEnabled;
    this.streamTimeout = streamTimeout;
    this.maxRetries = maxRetries;
    this.retryBackoff = retryBackoff;
  }

  public List<FetchVariable> getFetchVariables() {
    return fetchVariables;
  }

  public void setFetchVariables(final List<FetchVariable> fetchVariables) {
    this.fetchVariables = fetchVariables;
  }

  public Type getType() {
    return type;
  }

  public void setType(final Type type) {
    this.type = type;
  }

  public Name getName() {
    return name;
  }

  public void setName(final Name name) {
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

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<String> tenantIds) {
    if (tenantIds != null) {
      this.tenantIds = Collections.unmodifiableList(tenantIds);
    }
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

  public Duration getRetryBackoff() {
    return retryBackoff;
  }

  public void setRetryBackoff(final Duration retryBackoff) {
    this.retryBackoff = retryBackoff;
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
        fetchVariables,
        enabled,
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
        && Objects.equals(fetchVariables, that.fetchVariables)
        && Objects.equals(enabled, that.enabled)
        && Objects.equals(tenantIds, that.tenantIds)
        && Objects.equals(forceFetchAllVariables, that.forceFetchAllVariables)
        && Objects.equals(streamEnabled, that.streamEnabled)
        && Objects.equals(streamTimeout, that.streamTimeout)
        && Objects.equals(maxRetries, that.maxRetries)
        && Objects.equals(retryBackoff, that.retryBackoff)
        && Objects.equals(autoComplete, that.autoComplete);
  }

  @Override
  public JobWorkerValue clone() {
    try {
      final ObjectMapper objectMapper =
          JsonMapper.builder().addModule(new JavaTimeModule()).build();
      final JobWorkerValue clone =
          objectMapper.readValue(objectMapper.writeValueAsString(this), JobWorkerValue.class);
      clone.setMethodInfo(getMethodInfo());
      return clone;
    } catch (final JsonProcessingException e) {
      throw new RuntimeException("Error while cloning " + this, e);
    }
  }

  @Override
  public String toString() {
    return "JobWorkerValue{"
        + "autoComplete="
        + autoComplete
        + ", maxRetries="
        + maxRetries
        + ", streamTimeout="
        + streamTimeout
        + ", streamEnabled="
        + streamEnabled
        + ", forceFetchAllVariables="
        + forceFetchAllVariables
        + ", tenantIds="
        + tenantIds
        + ", enabled="
        + enabled
        + ", fetchVariables="
        + fetchVariables
        + ", pollInterval="
        + pollInterval
        + ", requestTimeout="
        + requestTimeout
        + ", maxJobsActive="
        + maxJobsActive
        + ", timeout="
        + timeout
        + ", name='"
        + name
        + '\''
        + ", type='"
        + type
        + '\''
        + '}';
  }

  public record Name(String value, FieldSource source) {}

  public record Type(String value, FieldSource source) {}

  public record FetchVariable(String value, FieldSource source) {}

  public enum FieldSource {
    EMPTY_FIELD(true),
    GENERATED_FROM_METHOD_INFO(true),
    FROM_ANNOTATION(false),
    FROM_DEFAULT_PROPERTIES(false),
    FROM_OVERRIDE_PROPERTIES(false),
    FROM_ACTUATOR(false),
    @Deprecated(forRemoval = true)
    LEGACY(false);

    private final boolean generated;

    FieldSource(final boolean generated) {
      this.generated = generated;
    }

    public boolean isGenerated() {
      return generated;
    }
  }
}
