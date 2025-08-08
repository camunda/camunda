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

import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_POLL_INTERVAL;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_TIMEOUT;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_NAME_VAR;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_TENANT_IDS;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_MAX_JOBS_ACTIVE;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_STREAM_ENABLED;

import java.time.Duration;
import java.util.List;

public class CamundaClientJobWorkerProperties {

  /** Set the type of jobs to work on. */
  private String type;

  /**
   * Set the name of the worker owner. Will be generated as `${beanName}#${methodName}` as long as
   * set to default.
   */
  private String name;

  /** Set the time for how long a job is exclusively assigned for a worker. */
  private Duration timeout;

  /**
   * Set the maximum number of jobs which will be exclusively activated for this worker at the same
   * time.
   */
  private Integer maxJobsActive;

  /** Set the request timeout for activate job request used to poll for new job. */
  private Duration requestTimeout;

  /** Set the maximal interval between polling for new jobs. */
  private Duration pollInterval;

  /** Set whether a job should automatically be completed after the method invocation. */
  private Boolean autoComplete;

  /**
   * Set a list of variable names which should be fetched on job activation. If set on defaults, it
   * will extend the list of variables to fetch from the annotation. If set on override, it will
   * replace the list of variables to fetch.
   */
  private List<String> fetchVariables;

  /** Set whether the job worker is enabled. */
  private Boolean enabled;

  /**
   * Set for which tenants the jobs worker should be registered. If set on defaults, it will extend
   * the list of tenant ids from the annotation. Of set on override, it will replace the list of
   * tenant ids.
   */
  private List<String> tenantIds;

  /** Set whether all variables should be fetched. Overrides `fetch-variables`. */
  private Boolean forceFetchAllVariables;

  /**
   * Opt-in feature flag to enable job streaming. If set as enabled, the job worker will use a mix
   * of streaming and polling to activate jobs. A long living stream will be opened onto which jobs
   * will be eagerly pushed, and the polling mechanism will be used strictly to fetch jobs created
   * <em>before</em> any streams were opened.
   */
  private Boolean streamEnabled;

  /**
   * If streaming is enabled, sets a maximum lifetime for a given stream. Once this timeout is
   * reached, the stream is closed, such that no more jobs are activated and received. If the worker
   * is still open, then it will immediately open a new stream.
   */
  private Duration streamTimeout;

  /**
   * Set the maximum number of retries before an automatic response (complete, fail, bpmn error) for
   * jobs will not be attempted anymore.
   */
  private Integer maxRetries;

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
      timeout = DEFAULT_JOB_TIMEOUT;
      maxJobsActive = DEFAULT_MAX_JOBS_ACTIVE;
      pollInterval = DEFAULT_JOB_POLL_INTERVAL;
      name = DEFAULT_JOB_WORKER_NAME_VAR;
      streamEnabled = DEFAULT_STREAM_ENABLED;
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
