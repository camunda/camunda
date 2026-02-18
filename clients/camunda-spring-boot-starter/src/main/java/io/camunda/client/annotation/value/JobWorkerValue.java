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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.*;
import io.camunda.client.api.command.enums.TenantFilterMode;
import io.camunda.client.bean.MethodInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class JobWorkerValue {
  private SourceAware<String> type = new Empty<>();
  private SourceAware<String> name = new Empty<>();
  private SourceAware<Duration> timeout = new Empty<>();
  private SourceAware<Integer> maxJobsActive = new Empty<>();
  private SourceAware<Duration> requestTimeout = new Empty<>();
  private SourceAware<Duration> pollInterval = new Empty<>();
  private List<SourceAware<String>> fetchVariables = new ArrayList<>();
  private SourceAware<Boolean> enabled = new Empty<>();
  private List<SourceAware<String>> tenantIds = new ArrayList<>();
  private SourceAware<Boolean> forceFetchAllVariables = new Empty<>();
  private SourceAware<Boolean> streamEnabled = new Empty<>();
  private SourceAware<Duration> streamTimeout = new Empty<>();
  private SourceAware<Integer> maxRetries = new Empty<>();
  private SourceAware<Duration> retryBackoff = new Empty<>();
  private SourceAware<TenantFilterMode> tenantFilterMode = new Empty<>();
  // cannot be changed from change set
  private SourceAware<Boolean> autoComplete = new Empty<>();

  @Deprecated(forRemoval = true)
  @JsonIgnore
  private MethodInfo methodInfo;

  public JobWorkerValue() {}

  public JobWorkerValue(
      final SourceAware<String> type,
      final SourceAware<String> name,
      final SourceAware<Duration> timeout,
      final SourceAware<Integer> maxJobsActive,
      final SourceAware<Duration> requestTimeout,
      final SourceAware<Duration> pollInterval,
      final SourceAware<Boolean> autoComplete,
      final List<SourceAware<String>> fetchVariables,
      final SourceAware<Boolean> enabled,
      final List<SourceAware<String>> tenantIds,
      final SourceAware<Boolean> forceFetchAllVariables,
      final SourceAware<Boolean> streamEnabled,
      final SourceAware<Duration> streamTimeout,
      final SourceAware<Integer> maxRetries,
      final SourceAware<Duration> retryBackoff,
      final SourceAware<TenantFilterMode> tenantFilterMode) {
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
    this.tenantFilterMode = tenantFilterMode;
  }

  public SourceAware<String> getType() {
    return type;
  }

  public void setType(final SourceAware<String> type) {
    this.type = type;
  }

  public SourceAware<String> getName() {
    return name;
  }

  public void setName(final SourceAware<String> name) {
    this.name = name;
  }

  public SourceAware<Duration> getTimeout() {
    return timeout;
  }

  public void setTimeout(final SourceAware<Duration> timeout) {
    this.timeout = timeout;
  }

  public SourceAware<Integer> getMaxJobsActive() {
    return maxJobsActive;
  }

  public void setMaxJobsActive(final SourceAware<Integer> maxJobsActive) {
    this.maxJobsActive = maxJobsActive;
  }

  public SourceAware<Duration> getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final SourceAware<Duration> requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public SourceAware<Duration> getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(final SourceAware<Duration> pollInterval) {
    this.pollInterval = pollInterval;
  }

  public List<SourceAware<String>> getFetchVariables() {
    return fetchVariables;
  }

  public void setFetchVariables(final List<SourceAware<String>> fetchVariables) {
    this.fetchVariables = Collections.unmodifiableList(fetchVariables);
  }

  public SourceAware<Boolean> getEnabled() {
    return enabled;
  }

  public void setEnabled(final SourceAware<Boolean> enabled) {
    this.enabled = enabled;
  }

  public List<SourceAware<String>> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<SourceAware<String>> tenantIds) {
    this.tenantIds = Collections.unmodifiableList(tenantIds);
  }

  public SourceAware<Boolean> getForceFetchAllVariables() {
    return forceFetchAllVariables;
  }

  public void setForceFetchAllVariables(final SourceAware<Boolean> forceFetchAllVariables) {
    this.forceFetchAllVariables = forceFetchAllVariables;
  }

  public SourceAware<Boolean> getStreamEnabled() {
    return streamEnabled;
  }

  public void setStreamEnabled(final SourceAware<Boolean> streamEnabled) {
    this.streamEnabled = streamEnabled;
  }

  public SourceAware<Duration> getStreamTimeout() {
    return streamTimeout;
  }

  public void setStreamTimeout(final SourceAware<Duration> streamTimeout) {
    this.streamTimeout = streamTimeout;
  }

  public SourceAware<Integer> getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(final SourceAware<Integer> maxRetries) {
    this.maxRetries = maxRetries;
  }

  public SourceAware<Boolean> getAutoComplete() {
    return autoComplete;
  }

  public void setAutoComplete(final SourceAware<Boolean> autoComplete) {
    this.autoComplete = autoComplete;
  }

  public SourceAware<Duration> getRetryBackoff() {
    return retryBackoff;
  }

  public void setRetryBackoff(final SourceAware<Duration> retryBackoff) {
    this.retryBackoff = retryBackoff;
  }

  public SourceAware<TenantFilterMode> getTenantFilterMode() {
    return tenantFilterMode;
  }

  public void setTenantFilterMode(final SourceAware<TenantFilterMode> tenantFilterMode) {
    this.tenantFilterMode = tenantFilterMode;
  }

  @Deprecated(forRemoval = true, since = "8.9")
  public MethodInfo getMethodInfo() {
    return methodInfo;
  }

  @Deprecated(forRemoval = true, since = "8.9")
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
        retryBackoff,
        tenantFilterMode,
        autoComplete);
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
        && Objects.equals(tenantFilterMode, that.tenantFilterMode)
        && Objects.equals(autoComplete, that.autoComplete);
  }

  @Override
  public String toString() {
    return "JobWorkerValue{"
        + "autoComplete="
        + autoComplete
        + ", tenantFilterMode="
        + tenantFilterMode
        + ", retryBackoff="
        + retryBackoff
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
        + ", name="
        + name
        + ", type="
        + type
        + '}';
  }

  public sealed interface SourceAware<T> {
    T value();

    int priority();

    boolean generated();

    record Empty<T>() implements SourceAware<T> {

      @Override
      public T value() {
        return null;
      }

      @Override
      public int priority() {
        return 0;
      }

      @Override
      public boolean generated() {
        return true;
      }
    }

    record GeneratedFromMethodInfo<T>(T value) implements SourceAware<T> {

      @Override
      public int priority() {
        return 1;
      }

      @Override
      public boolean generated() {
        return true;
      }
    }

    record FromDefaultProperty<T>(T value) implements SourceAware<T> {
      @Override
      public int priority() {
        return 2;
      }

      @Override
      public boolean generated() {
        return false;
      }
    }

    record FromAnnotation<T>(T value) implements SourceAware<T> {
      @Override
      public int priority() {
        return 3;
      }

      @Override
      public boolean generated() {
        return false;
      }
    }

    record FromOverrideProperty<T>(T value) implements SourceAware<T> {
      @Override
      public int priority() {
        return 4;
      }

      @Override
      public boolean generated() {
        return false;
      }
    }

    record FromRuntimeOverride<T>(T value, SourceAware<T> original) implements SourceAware<T> {
      @Override
      public int priority() {
        return 5;
      }

      @Override
      public boolean generated() {
        return false;
      }
    }

    record FromLegacy<T>(T value) implements SourceAware<T> {
      @Override
      public int priority() {
        return -1;
      }

      @Override
      public boolean generated() {
        return false;
      }
    }
  }
}
