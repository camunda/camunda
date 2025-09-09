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
package io.camunda.client.jobhandling;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.jobhandling.JobWorkerChangeSet.CreateChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.EnabledChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.ResetChangeSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerManager.class);

  private final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers;
  private final JobWorkerFactory jobWorkerFactory;

  private final Map<String, InternalManagedJobWorker> managedJobWorkers = new HashMap<>();

  public JobWorkerManager(
      final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers,
      final JobWorkerFactory jobWorkerFactory) {
    this.jobWorkerValueCustomizers = jobWorkerValueCustomizers;
    this.jobWorkerFactory = jobWorkerFactory;
  }

  public void createJobWorker(
      final CamundaClient client, final ManagedJobWorker managedJobWorker, final Object source) {
    final JobWorkerValue jobWorkerValue = managedJobWorker.jobWorkerValue();
    jobWorkerValueCustomizers.forEach(customizer -> customizer.customize(jobWorkerValue));
    final String type = jobWorkerValue.getType().value();
    final InternalManagedJobWorker internalManagedJobWorker;
    if (managedJobWorkers.containsKey(type)) {
      internalManagedJobWorker = managedJobWorkers.get(type);
    } else {
      internalManagedJobWorker = new InternalManagedJobWorker();
      internalManagedJobWorker.setSource(source);
      internalManagedJobWorker.setOriginal(jobWorkerValue);
      internalManagedJobWorker.setCurrent(jobWorkerValue.clone());
      internalManagedJobWorker.setCamundaClient(client);
      internalManagedJobWorker.setJobHandlerFactory(managedJobWorker.jobHandlerFactory());
      managedJobWorkers.put(type, internalManagedJobWorker);
    }
    upsertWorker(internalManagedJobWorker, new CreateChangeSet(), false);
  }

  public Map<String, JobWorkerValue> getJobWorkers() {
    return managedJobWorkers.entrySet().stream()
        .map(e -> Map.entry(e.getKey(), e.getValue().getCurrent()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public JobWorkerValue getJobWorker(final String type) {
    final InternalManagedJobWorker internalManagedJobWorker = findManagedJobWorker(type);
    return internalManagedJobWorker.getCurrent();
  }

  public void updateJobWorker(
      final String type, final JobWorkerChangeSet changeSet, final boolean applyCustomizers) {
    final InternalManagedJobWorker internalManagedJobWorker = findManagedJobWorker(type);
    upsertWorker(internalManagedJobWorker, changeSet, applyCustomizers);
  }

  public void updateJobWorkers(final JobWorkerChangeSet changeSet, final boolean applyCustomizers) {
    managedJobWorkers.keySet().forEach(type -> updateJobWorker(type, changeSet, applyCustomizers));
  }

  public void resetJobWorker(final String type) {
    final InternalManagedJobWorker internalManagedJobWorker = findManagedJobWorker(type);
    upsertWorker(
        internalManagedJobWorker,
        new ResetChangeSet(internalManagedJobWorker.getOriginal()),
        false);
  }

  public void resetJobWorkers() {
    managedJobWorkers.keySet().forEach(this::resetJobWorker);
  }

  public void closeJobWorker(final String type) {
    final InternalManagedJobWorker internalManagedJobWorker = findManagedJobWorker(type);
    upsertWorker(internalManagedJobWorker, new EnabledChangeSet(false), false);
  }

  private InternalManagedJobWorker findManagedJobWorker(final String type) {
    if (!managedJobWorkers.containsKey(type)) {
      throw new IllegalArgumentException("Unknown job worker type: " + type);
    }
    return managedJobWorkers.get(type);
  }

  private void upsertWorker(
      final InternalManagedJobWorker internalManagedJobWorker,
      final JobWorkerChangeSet changeSet,
      final boolean applyCustomizers) {
    // apply changes and check whether there was an actual change
    final boolean changed = changeSet.applyChanges(internalManagedJobWorker.getCurrent());
    if (!changed) {
      return;
    }
    // try to find the currently running worker and stop it
    final JobWorker jobWorker = internalManagedJobWorker.getJobWorker();
    if (jobWorker != null && !jobWorker.isClosed()) {
      jobWorker.close();
      LOGGER.info(
          "Stopping job worker: {}",
          LOGGER.isDebugEnabled()
              ? internalManagedJobWorker.getCurrent()
              : String.format(
                  "%s with type %s",
                  internalManagedJobWorker.getCurrent().getName().value(),
                  internalManagedJobWorker.getCurrent().getType().value()));
    }
    // apply customizers if required
    if (applyCustomizers) {
      jobWorkerValueCustomizers.forEach(
          customizer -> customizer.customize(internalManagedJobWorker.getCurrent()));
    }
    final boolean enabled =
        internalManagedJobWorker.getCurrent().getEnabled() == null
            || internalManagedJobWorker.getCurrent().getEnabled();
    if (enabled) {
      internalManagedJobWorker.setJobWorker(
          jobWorkerFactory.createJobWorker(
              internalManagedJobWorker.getCamundaClient(),
              internalManagedJobWorker.getCurrent(),
              internalManagedJobWorker.getJobHandlerFactory()));
      LOGGER.info(
          "Starting job worker: {}",
          LOGGER.isDebugEnabled()
              ? internalManagedJobWorker.getCurrent()
              : String.format(
                  "%s with type %s",
                  internalManagedJobWorker.getCurrent().getName().value(),
                  internalManagedJobWorker.getCurrent().getType().value()));
    }
  }

  public void closeAllJobWorkers(final Object source) {
    managedJobWorkers.entrySet().stream()
        .filter(e -> Objects.equals(e.getValue().getSource(), source))
        .map(Map.Entry::getKey)
        .forEach(this::closeJobWorker);
  }

  private static final class InternalManagedJobWorker {
    private JobHandlerFactory jobHandlerFactory;
    private JobWorker jobWorker;
    private JobWorkerValue original;
    private JobWorkerValue current;
    private CamundaClient camundaClient;
    private Object source;

    public JobWorker getJobWorker() {
      return jobWorker;
    }

    public void setJobWorker(final JobWorker jobWorker) {
      this.jobWorker = jobWorker;
    }

    public JobWorkerValue getOriginal() {
      return original;
    }

    public void setOriginal(final JobWorkerValue original) {
      this.original = original;
    }

    public JobWorkerValue getCurrent() {
      return current;
    }

    public void setCurrent(final JobWorkerValue current) {
      this.current = current;
    }

    public CamundaClient getCamundaClient() {
      return camundaClient;
    }

    public void setCamundaClient(final CamundaClient camundaClient) {
      this.camundaClient = camundaClient;
    }

    public JobHandlerFactory getJobHandlerFactory() {
      return jobHandlerFactory;
    }

    public void setJobHandlerFactory(final JobHandlerFactory jobHandlerFactory) {
      this.jobHandlerFactory = jobHandlerFactory;
    }

    public Object getSource() {
      return source;
    }

    public void setSource(final Object source) {
      this.source = source;
    }
  }
}
