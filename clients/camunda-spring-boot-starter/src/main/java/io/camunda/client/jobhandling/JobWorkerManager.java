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
import io.camunda.client.jobhandling.JobWorkerChangeSet.CloseChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.CreateChangeSet;
import io.camunda.client.jobhandling.JobWorkerChangeSet.ResetChangeSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerManager.class);

  private final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers;
  private final JobWorkerFactory jobWorkerFactory;

  /**
   * Managed workers keyed by a composite {@code (client identity, type)} key so the same job type
   * can be registered independently on multiple clients (one per configured multi-client). For a
   * single-client application this holds exactly one entry per type.
   */
  private final Map<String, InternalManagedJobWorker> managedJobWorkers = new ConcurrentHashMap<>();

  public JobWorkerManager(
      final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers,
      final JobWorkerFactory jobWorkerFactory) {
    this.jobWorkerValueCustomizers = jobWorkerValueCustomizers;
    this.jobWorkerFactory = jobWorkerFactory;
  }

  /** Distinguishes the same job type registered on different client instances. */
  private static String workerKey(final CamundaClient client, final String type) {
    return System.identityHashCode(client) + ":" + type;
  }

  public void createJobWorker(
      final CamundaClient client, final ManagedJobWorker managedJobWorker, final Object source) {
    createJobWorker(client, managedJobWorker, source, null);
  }

  public void createJobWorker(
      final CamundaClient client,
      final ManagedJobWorker managedJobWorker,
      final Object source,
      final String clientName) {
    final JobWorkerValue jobWorkerValue = managedJobWorker.jobWorkerValue();
    jobWorkerValueCustomizers.forEach(customizer -> customizer.customize(jobWorkerValue));
    final String type = jobWorkerValue.getType().value();
    final String key = workerKey(client, type);
    final InternalManagedJobWorker internalManagedJobWorker;
    if (managedJobWorkers.containsKey(key)) {
      internalManagedJobWorker = managedJobWorkers.get(key);
    } else {
      internalManagedJobWorker = new InternalManagedJobWorker();
      internalManagedJobWorker.setSource(source);
      internalManagedJobWorker.setCurrent(jobWorkerValue);
      internalManagedJobWorker.setCamundaClient(client);
      internalManagedJobWorker.setJobHandlerFactory(managedJobWorker.jobHandlerFactory());
      internalManagedJobWorker.setType(type);
      internalManagedJobWorker.setClientName(clientName);
      managedJobWorkers.put(key, internalManagedJobWorker);
    }
    upsertWorker(internalManagedJobWorker, new CreateChangeSet());
  }

  public Map<String, JobWorkerValue> getJobWorkers() {
    // keyed by type; if the same type runs on multiple clients any one of them is returned
    return managedJobWorkers.values().stream()
        .collect(
            Collectors.toMap(
                InternalManagedJobWorker::getType,
                InternalManagedJobWorker::getCurrent,
                (existing, replacement) -> existing));
  }

  public JobWorkerValue getJobWorker(final String type) {
    return findAnyManagedJobWorker(type).getCurrent();
  }

  public void updateJobWorker(final String type, final JobWorkerChangeSet changeSet) {
    // update every client's worker of this type
    findManagedJobWorkers(type).forEach(worker -> upsertWorker(worker, changeSet));
  }

  public void updateJobWorkers(final JobWorkerChangeSet changeSet) {
    managedJobWorkers.values().forEach(worker -> upsertWorker(worker, changeSet));
  }

  public void resetJobWorker(final String type) {
    findManagedJobWorkers(type).forEach(worker -> upsertWorker(worker, new ResetChangeSet()));
  }

  public void resetJobWorkers() {
    managedJobWorkers.values().forEach(worker -> upsertWorker(worker, new ResetChangeSet()));
  }

  public void closeJobWorker(final String type) {
    // close every client's worker of this type
    removeWorkers(e -> e.getValue().getType().equals(type));
  }

  /** Closes the workers registered by the given source on the given client only. */
  public void closeJobWorkers(final Object source, final CamundaClient client) {
    removeWorkers(
        e ->
            Objects.equals(e.getValue().getSource(), source)
                && e.getValue().getCamundaClient() == client);
  }

  public void closeAllJobWorkers(final Object source) {
    removeWorkers(e -> Objects.equals(e.getValue().getSource(), source));
  }

  private void removeWorkers(final Predicate<Map.Entry<String, InternalManagedJobWorker>> filter) {
    final List<String> keysToRemove =
        managedJobWorkers.entrySet().stream().filter(filter).map(Map.Entry::getKey).toList();
    keysToRemove.forEach(
        key -> {
          upsertWorker(managedJobWorkers.get(key), new CloseChangeSet());
          managedJobWorkers.remove(key);
        });
  }

  private InternalManagedJobWorker findAnyManagedJobWorker(final String type) {
    return managedJobWorkers.values().stream()
        .filter(worker -> worker.getType().equals(type))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown job worker type: " + type));
  }

  private List<InternalManagedJobWorker> findManagedJobWorkers(final String type) {
    final List<InternalManagedJobWorker> workers =
        managedJobWorkers.values().stream()
            .filter(worker -> worker.getType().equals(type))
            .collect(Collectors.toList());
    if (workers.isEmpty()) {
      throw new IllegalArgumentException("Unknown job worker type: " + type);
    }
    return workers;
  }

  private void upsertWorker(
      final InternalManagedJobWorker internalManagedJobWorker, final JobWorkerChangeSet changeSet) {
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
    final boolean enabled = internalManagedJobWorker.getCurrent().getEnabled().value();
    final boolean closed = changeSet instanceof CloseChangeSet;
    if (!closed && enabled) {
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

  private static final class InternalManagedJobWorker {
    private JobHandlerFactory jobHandlerFactory;
    private JobWorker jobWorker;
    private JobWorkerValue current;
    private CamundaClient camundaClient;
    private Object source;
    private String type;
    private String clientName;

    public JobWorker getJobWorker() {
      return jobWorker;
    }

    public void setJobWorker(final JobWorker jobWorker) {
      this.jobWorker = jobWorker;
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

    public String getType() {
      return type;
    }

    public void setType(final String type) {
      this.type = type;
    }

    public String getClientName() {
      return clientName;
    }

    public void setClientName(final String clientName) {
      this.clientName = clientName;
    }
  }
}
