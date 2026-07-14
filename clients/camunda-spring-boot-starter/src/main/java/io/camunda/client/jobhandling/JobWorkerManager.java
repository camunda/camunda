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
   * Managed workers keyed by a composite {@code (client, type)} key so the same job type can be
   * registered independently on multiple clients (one per configured multi-client). For a
   * single-client application this holds exactly one entry per type.
   */
  private final Map<WorkerKey, InternalManagedJobWorker> managedJobWorkers =
      new ConcurrentHashMap<>();

  public JobWorkerManager(
      final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers,
      final JobWorkerFactory jobWorkerFactory) {
    this.jobWorkerValueCustomizers = jobWorkerValueCustomizers;
    this.jobWorkerFactory = jobWorkerFactory;
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
    final InternalManagedJobWorker internalManagedJobWorker =
        managedJobWorkers.computeIfAbsent(
            new WorkerKey(client, type),
            key -> {
              final InternalManagedJobWorker worker = new InternalManagedJobWorker();
              worker.setSource(source);
              worker.setCurrent(jobWorkerValue);
              worker.setCamundaClient(client);
              worker.setJobHandlerFactory(managedJobWorker.jobHandlerFactory());
              worker.setType(type);
              worker.setClientName(clientName);
              return worker;
            });
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

  private void removeWorkers(
      final Predicate<Map.Entry<WorkerKey, InternalManagedJobWorker>> filter) {
    final List<WorkerKey> keysToRemove =
        managedJobWorkers.entrySet().stream().filter(filter).map(Map.Entry::getKey).toList();
    keysToRemove.forEach(
        key -> {
          // remove first so a concurrent removal cannot leave us upserting a null worker
          final InternalManagedJobWorker worker = managedJobWorkers.remove(key);
          if (worker != null) {
            upsertWorker(worker, new CloseChangeSet());
          }
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
              : describe(internalManagedJobWorker));
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
              : describe(internalManagedJobWorker));
    }
  }

  /**
   * Renders a short worker description for the info-level start/stop logs, including the configured
   * client name when the worker is bound to a named client (multi-client mode).
   */
  private static String describe(final InternalManagedJobWorker worker) {
    final String clientSuffix =
        worker.getClientName() == null ? "" : " on client '" + worker.getClientName() + "'";
    return String.format(
        "%s with type %s%s",
        worker.getCurrent().getName().value(), worker.getCurrent().getType().value(), clientSuffix);
  }

  /**
   * Composite key distinguishing the same job type registered on different client instances. The
   * client is compared by reference identity so two distinct client instances never share an entry
   * (unlike encoding {@link System#identityHashCode(Object)} into the key, which can collide).
   */
  private static final class WorkerKey {
    private final CamundaClient client;
    private final String type;

    private WorkerKey(final CamundaClient client, final String type) {
      this.client = client;
      this.type = type;
    }

    @Override
    public int hashCode() {
      return 31 * System.identityHashCode(client) + type.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof final WorkerKey other)) {
        return false;
      }
      return client == other.client && type.equals(other.type);
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
