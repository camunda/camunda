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
package io.camunda.runner.internal;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.runner.Job;
import java.util.Map;

/**
 * Default {@link Job} implementation that delegates to {@link ActivatedJob} for data access and to
 * a {@link JobClient} for the control verbs. Tracks a single-shot {@code resolved} flag so a job
 * cannot be completed/failed twice.
 */
public final class DefaultJob implements Job {

  private final JobClient client;
  private final ActivatedJob activatedJob;
  private volatile boolean resolved;

  public DefaultJob(final JobClient client, final ActivatedJob activatedJob) {
    this.client = client;
    this.activatedJob = activatedJob;
  }

  @Override
  public long getKey() {
    return activatedJob.getKey();
  }

  @Override
  public String getType() {
    return activatedJob.getType();
  }

  @Override
  public long getProcessInstanceKey() {
    return activatedJob.getProcessInstanceKey();
  }

  @Override
  public String getElementId() {
    return activatedJob.getElementId();
  }

  @Override
  public int getRetries() {
    return activatedJob.getRetries();
  }

  @Override
  public Map<String, Object> variables() {
    return activatedJob.getVariablesAsMap();
  }

  @Override
  public <T> T variable(final String name, final Class<T> type) {
    return type.cast(activatedJob.getVariable(name));
  }

  @Override
  public String variablesAsJson() {
    return activatedJob.getVariables();
  }

  @Override
  public ActivatedJob raw() {
    return activatedJob;
  }

  @Override
  public void complete() {
    markResolved();
    client.newCompleteCommand(activatedJob).send().join();
  }

  @Override
  public void complete(final Map<String, Object> variables) {
    markResolved();
    client.newCompleteCommand(activatedJob).variables(variables).send().join();
  }

  @Override
  public void complete(final String key, final Object value) {
    markResolved();
    client.newCompleteCommand(activatedJob).variable(key, value).send().join();
  }

  @Override
  public void fail(final String reason) {
    fail(reason, Math.max(0, activatedJob.getRetries() - 1));
  }

  @Override
  public void fail(final String reason, final int retries) {
    markResolved();
    client.newFailCommand(activatedJob).retries(retries).errorMessage(reason).send().join();
  }

  /** Package-private hook used by {@link JobHandlerAdapter} to detect explicit resolution. */
  boolean isResolved() {
    return resolved;
  }

  private void markResolved() {
    if (resolved) {
      throw new IllegalStateException("job already resolved");
    }
    resolved = true;
  }
}
