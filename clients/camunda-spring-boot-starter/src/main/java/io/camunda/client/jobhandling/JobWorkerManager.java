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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerManager.class);

  private final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers;
  private final JobWorkerFactory jobWorkerFactory;

  private final Map<String, InternalManagedJobWorker> managedJobWorkers = new ConcurrentHashMap<>();

  public JobWorkerManager(
      final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers,
      final JobWorkerFactory jobWorkerFactory) {
    this.jobWorkerValueCustomizers = jobWorkerValueCustomizers;
    this.jobWorkerFactory = jobWorkerFactory;
  }

  /**
   * Creates a composite key for tracking job workers per client. This allows the same job type to
   * be registered on multiple clients.
   */
  private String createWorkerKey(final CamundaClient client, final String type) {
    // Use client's identity hash code to distinguish between different client instances
    return System.identityHashCode(client) + ":" + type;
  }

  public void createJobWorker(
      final CamundaClient client,
      final ManagedJobWorker managedJobWorker,
      final Object source,
      final String clientName) {
    final JobWorkerValue jobWorkerValue = managedJobWorker.jobWorkerValue();
    jobWorkerValueCustomizers.forEach(customizer -> customizer.customize(jobWorkerValue));
    final String type = jobWorkerValue.getType().value();
    final String workerKey = createWorkerKey(client, type);
    final InternalManagedJobWorker internalManagedJobWorker;
    if (managedJobWorkers.containsKey(workerKey)) {
      internalManagedJobWorker = managedJobWorkers.get(workerKey);
    } else {
      internalManagedJobWorker = new InternalManagedJobWorker();
      internalManagedJobWorker.setSource(source);
      internalManagedJobWorker.setCurrent(jobWorkerValue);
      internalManagedJobWorker.setCamundaClient(client);
      internalManagedJobWorker.setJobHandlerFactory(managedJobWorker.jobHandlerFactory());
      internalManagedJobWorker.setType(type);
      internalManagedJobWorker.setClientName(clientName);
      managedJobWorkers.put(workerKey, internalManagedJobWorker);

      LOGGER.info(
          "Registering job worker '{}' with type '{}' for engine '{}'",
          jobWorkerValue.getName().value(),
          type,
          clientName != null ? clientName : "default");
    }
    upsertWorker(internalManagedJobWorker, new CreateChangeSet());
  }

  public Map<String, JobWorkerValue> getJobWorkers() {
    // Return workers keyed by their type (not the composite key)
    // Note: If the same type is registered on multiple clients, this returns one of them
    return managedJobWorkers.values().stream()
        .collect(
            Collectors.toMap(
                InternalManagedJobWorker::getType,
                InternalManagedJobWorker::getCurrent,
                (existing, replacement) -> existing));
  }

  public JobWorkerValue getJobWorker(final String type) {
    final InternalManagedJobWorker internalManagedJobWorker = findManagedJobWorkerByType(type);
    return internalManagedJobWorker.getCurrent();
  }

  public void updateJobWorker(final String type, final JobWorkerChangeSet changeSet) {
    // Update all workers of this type (across all clients)
    findAllManagedJobWorkersByType(type).forEach(worker -> upsertWorker(worker, changeSet));
  }

  public void updateJobWorkers(final JobWorkerChangeSet changeSet) {
    managedJobWorkers.values().forEach(worker -> upsertWorker(worker, changeSet));
  }

  public void resetJobWorker(final String type) {
    // Reset all workers of this type (across all clients)
    findAllManagedJobWorkersByType(type)
        .forEach(worker -> upsertWorker(worker, new ResetChangeSet()));
  }

  public void resetJobWorkers() {
    managedJobWorkers.values().forEach(worker -> upsertWorker(worker, new ResetChangeSet()));
  }

  public void closeJobWorker(final String type) {
    // Close all workers of this type (across all clients)
    final List<String> keysToRemove =
        managedJobWorkers.entrySet().stream()
            .filter(e -> e.getValue().getType().equals(type))
            .map(Map.Entry::getKey)
            .toList();
    keysToRemove.forEach(
        key -> {
          upsertWorker(managedJobWorkers.get(key), new CloseChangeSet());
          managedJobWorkers.remove(key);
        });
  }

  private InternalManagedJobWorker findManagedJobWorkerByType(final String type) {
    return managedJobWorkers.values().stream()
        .filter(worker -> worker.getType().equals(type))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown job worker type: " + type));
  }

  private List<InternalManagedJobWorker> findAllManagedJobWorkersByType(final String type) {
    final List<InternalManagedJobWorker> workers =
        managedJobWorkers.values().stream()
            .filter(worker -> worker.getType().equals(type))
            .toList();
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

  public void closeAllJobWorkers(final Object source) {
    final List<String> keysToRemove =
        managedJobWorkers.entrySet().stream()
            .filter(e -> Objects.equals(e.getValue().getSource(), source))
            .map(Map.Entry::getKey)
            .toList();
    keysToRemove.forEach(
        key -> {
          upsertWorker(managedJobWorkers.get(key), new CloseChangeSet());
          managedJobWorkers.remove(key);
        });
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
