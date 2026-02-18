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
package io.camunda.client.spring.properties;

import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_POLL_INTERVAL;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_TIMEOUT;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_NAME_VAR;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_TENANT_FILTER;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_TENANT_IDS;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_MAX_JOBS_ACTIVE;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_REQUEST_TIMEOUT;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_STREAM_ENABLED;
import static io.camunda.client.impl.worker.JobWorkerBuilderImpl.DEFAULT_STREAMING_TIMEOUT;

import io.camunda.client.api.command.enums.TenantFilter;
import java.time.Duration;
import java.util.List;

public class CamundaClientJobWorkerProperties {
  public static final boolean DEFAULT_AUTO_COMPLETE = true;
  public static final boolean DEFAULT_ENABLED = true;
  public static final boolean DEFAULT_FORCE_FETCH_ALL_VARIABLES = false;
  public static final int DEFAULT_MAX_RETRIES = 0;

  /** The type of jobs to work on. */
  private String type;

  /**
   * The name of the worker owner. If set to default, it is generated as
   * `${beanName}#${methodName}`.
   */
  private String name;

  /** The time a job remains exclusively assigned to the worker. */
  private Duration timeout;

  /** The maximum number of jobs exclusively activated for this worker at the same time. */
  private Integer maxJobsActive;

  /** The request timeout for the activate job request used to poll for new jobs. */
  private Duration requestTimeout;

  /** The maximal interval between polls for new jobs. */
  private Duration pollInterval;

  /** Enable or disable automatic job completion after method invocation. */
  private Boolean autoComplete;

  /**
   * List of variable names to fetch on job activation. When set in defaults, it extends the list of
   * variables to fetch from the annotation. When set in an override, it replaces the list of
   * variables to fetch.
   */
  private List<String> fetchVariables;

  /** Enable or disable the job worker. */
  private Boolean enabled;

  /**
   * Sets the tenants for which the job worker is registered. When set in defaults, it extends the
   * list of tenant IDs from the annotation. When set in override, it replaces the list of tenant
   * IDs.
   */
  private List<String> tenantIds;

  /**
   * Sets the tenant filter for the job worker, which determines how the worker considers tenant IDs
   * when activating jobs.
   */
  private TenantFilter tenantFilter;

  /** Sets whether all variables are fetched. Overrides `fetch-variables`. */
  private Boolean forceFetchAllVariables;

  /**
   * Opt-in feature flag that enables job streaming. When enabled, the job worker uses both
   * streaming and polling to activate jobs. A long-lived stream eagerly pushes new jobs, and
   * polling retrieves jobs created <em>before</em> any streams were opened.
   */
  private Boolean streamEnabled;

  /**
   * If streaming is enabled, sets the maximum lifetime for a stream. When this timeout is reached,
   * the stream closes, and no more jobs are activated or received. If the worker is still open, a
   * new stream opens immediately.
   */
  private Duration streamTimeout;

  /**
   * The maximum number of retries before automatic responses (complete, fail, bpmn error) for jobs
   * are no longer attempted.
   */
  private Integer maxRetries;

  /** The backoff before a retry of a failed job is possible. */
  private Duration retryBackoff;

  /**
   * This instantiates the properties without any defaults. Intended to be used by {@link
   * CamundaClientWorkerProperties#getOverride()}.
   */
  public CamundaClientJobWorkerProperties() {
    this(false);
  }

  /**
   * This instantiates the properties optionally with defaults if set to <code>true</code>. Intended
   * to be used by {@link CamundaClientWorkerProperties#getDefaults()}.
   *
   * @param initWithDefaults whether default properties should be set on creation
   */
  public CamundaClientJobWorkerProperties(final boolean initWithDefaults) {
    if (initWithDefaults) {
      tenantIds = DEFAULT_JOB_WORKER_TENANT_IDS;
      tenantFilter = DEFAULT_JOB_WORKER_TENANT_FILTER;
      timeout = DEFAULT_JOB_TIMEOUT;
      maxJobsActive = DEFAULT_MAX_JOBS_ACTIVE;
      pollInterval = DEFAULT_JOB_POLL_INTERVAL;
      name = DEFAULT_JOB_WORKER_NAME_VAR;
      streamEnabled = DEFAULT_STREAM_ENABLED;
      enabled = DEFAULT_ENABLED;
      autoComplete = DEFAULT_AUTO_COMPLETE;
      requestTimeout = DEFAULT_REQUEST_TIMEOUT;
      retryBackoff = Duration.ZERO;
      forceFetchAllVariables = DEFAULT_FORCE_FETCH_ALL_VARIABLES;
      maxRetries = DEFAULT_MAX_RETRIES;
      streamTimeout = DEFAULT_STREAMING_TIMEOUT;
    }
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

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public TenantFilter getTenantFilter() {
    return tenantFilter;
  }

  public void setTenantFilter(final TenantFilter tenantFilter) {
    this.tenantFilter = tenantFilter;
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
}
