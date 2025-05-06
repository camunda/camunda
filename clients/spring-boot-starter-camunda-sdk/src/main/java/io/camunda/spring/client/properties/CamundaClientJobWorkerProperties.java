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
package io.camunda.spring.client.properties;

import java.time.Duration;
import java.util.List;

public class CamundaClientJobWorkerProperties {
  private String type;
  private String name;
  private Duration timeout;
  private Integer maxJobsActive;
  private Duration requestTimeout;
  private Duration pollInterval;
  private Boolean autoComplete;
  private List<String> fetchVariables;
  private Boolean enabled;
  private List<String> tenantIds;
  private Boolean forceFetchAllVariables;
  private Boolean streamEnabled;
  private Duration streamTimeout;
  private Integer maxRetries;

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
}
