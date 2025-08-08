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
package io.camunda.spring.client.jobhandling;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.spring.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet.EnabledChangeSet;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet.NoopChangeSet;
import io.camunda.spring.client.jobhandling.JobWorkerChangeSet.ResetChangeSet;
import io.camunda.spring.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.spring.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerManager.class);

  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final ParameterResolverStrategy parameterResolverStrategy;
  private final ResultProcessorStrategy resultProcessorStrategy;
  private final JobExceptionHandlingStrategy jobExceptionHandlingStrategy;
  private final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers;
  private final JobWorkerFactory jobWorkerFactory;

  private final Map<String, ManagedJobWorker> managedJobWorkers = new HashMap<>();

  public JobWorkerManager(
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final ParameterResolverStrategy parameterResolverStrategy,
      final ResultProcessorStrategy resultProcessorStrategy,
      final JobExceptionHandlingStrategy jobExceptionHandlingStrategy,
      final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers,
      final JobWorkerFactory jobWorkerFactory) {
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
    this.parameterResolverStrategy = parameterResolverStrategy;
    this.resultProcessorStrategy = resultProcessorStrategy;
    this.jobExceptionHandlingStrategy = jobExceptionHandlingStrategy;
    this.jobWorkerValueCustomizers = jobWorkerValueCustomizers;
    this.jobWorkerFactory = jobWorkerFactory;
  }

  public void createJobWorker(
      final CamundaClient client, final JobWorkerValue jobWorkerValue, final Object source) {
    jobWorkerValueCustomizers.forEach(customizer -> customizer.customize(jobWorkerValue));
    final String type = jobWorkerValue.getType();
    final ManagedJobWorker managedJobWorker;
    if (managedJobWorkers.containsKey(type)) {
      managedJobWorker = managedJobWorkers.get(type);
    } else {
      managedJobWorker = new ManagedJobWorker();
      managedJobWorker.setSource(source);
      managedJobWorker.setOriginal(jobWorkerValue);
      managedJobWorker.setCurrent(jobWorkerValue.clone());
      managedJobWorker.setCamundaClient(client);
      managedJobWorker.setJobHandler(
          jobWorkerValue
              .getJobHandlerFactory()
              .getJobHandler(
                  new JobHandlerFactoryContext(
                      commandExceptionHandlingStrategy,
                      metricsRecorder,
                      parameterResolverStrategy,
                      resultProcessorStrategy,
                      jobExceptionHandlingStrategy,
                      jobWorkerValue)));
      managedJobWorkers.put(type, managedJobWorker);
    }
    upsertWorker(managedJobWorker, new NoopChangeSet(), false);
  }

  public Map<String, JobWorkerValue> getJobWorkers() {
    return managedJobWorkers.entrySet().stream()
        .map(e -> Map.entry(e.getKey(), e.getValue().getCurrent()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public JobWorkerValue getJobWorker(final String type) {
    final ManagedJobWorker managedJobWorker = findManagedJobWorker(type);
    return managedJobWorker.getCurrent();
  }

  public void updateJobWorker(
      final String type, final JobWorkerChangeSet changeSet, final boolean applyCustomizers) {
    final ManagedJobWorker managedJobWorker = findManagedJobWorker(type);
    upsertWorker(managedJobWorker, changeSet, applyCustomizers);
  }

  public void updateJobWorkers(final JobWorkerChangeSet changeSet, final boolean applyCustomizers) {
    managedJobWorkers.keySet().forEach(type -> updateJobWorker(type, changeSet, applyCustomizers));
  }

  public void resetJobWorker(final String type) {
    final ManagedJobWorker managedJobWorker = findManagedJobWorker(type);
    upsertWorker(managedJobWorker, new ResetChangeSet(managedJobWorker.getOriginal()), false);
  }

  public void resetJobWorkers() {
    managedJobWorkers.keySet().forEach(this::resetJobWorker);
  }

  public void closeJobWorker(final String type) {
    final ManagedJobWorker managedJobWorker = findManagedJobWorker(type);
    upsertWorker(managedJobWorker, new EnabledChangeSet(false), false);
  }

  private ManagedJobWorker findManagedJobWorker(final String type) {
    if (!managedJobWorkers.containsKey(type)) {
      throw new IllegalArgumentException("Unknown job worker type: " + type);
    }
    return managedJobWorkers.get(type);
  }

  private void upsertWorker(
      final ManagedJobWorker managedJobWorker,
      final JobWorkerChangeSet changeSet,
      final boolean applyCustomizers) {
    // apply changes and check whether there was an actual change
    final boolean changed = changeSet.applyChanges(managedJobWorker.getCurrent());
    if (!changed) {
      return;
    }
    // try to find the currently running worker and stop it
    final JobWorker jobWorker = managedJobWorker.getJobWorker();
    if (jobWorker != null && !jobWorker.isClosed()) {
      jobWorker.close();
      LOGGER.info(". Stopping job worker: {}", managedJobWorker.getCurrent());
    }
    // apply customizers if required
    if (applyCustomizers) {
      jobWorkerValueCustomizers.forEach(
          customizer -> customizer.customize(managedJobWorker.getCurrent()));
    }
    final boolean enabled =
        managedJobWorker.getCurrent().getEnabled() == null
            || managedJobWorker.getCurrent().getEnabled();
    if (enabled) {
      managedJobWorker.setJobWorker(
          jobWorkerFactory.createJobWorker(
              managedJobWorker.getCamundaClient(),
              managedJobWorker.getCurrent(),
              managedJobWorker.getJobHandler()));
      LOGGER.info(". Starting job worker: {}", managedJobWorker.getCurrent());
    }
  }

  public void closeAllJobWorkers(final Object source) {
    managedJobWorkers.entrySet().stream()
        .filter(e -> Objects.equals(e.getValue().getSource(), source))
        .map(Map.Entry::getKey)
        .forEach(this::closeJobWorker);
  }

  private static final class ManagedJobWorker {
    private JobHandler jobHandler;
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

    public JobHandler getJobHandler() {
      return jobHandler;
    }

    public void setJobHandler(final JobHandler jobHandler) {
      this.jobHandler = jobHandler;
    }

    public Object getSource() {
      return source;
    }

    public void setSource(final Object source) {
      this.source = source;
    }
  }
}
