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

import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_NAME_VAR;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_TENANT_IDS;
import static org.apache.commons.lang3.StringUtils.*;

import io.camunda.spring.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyBasedJobWorkerValueCustomizer implements JobWorkerValueCustomizer {
  private static final Logger LOG =
      LoggerFactory.getLogger(PropertyBasedJobWorkerValueCustomizer.class);

  private final CamundaClientProperties camundaClientProperties;

  public PropertyBasedJobWorkerValueCustomizer(
      final CamundaClientProperties camundaClientProperties) {
    this.camundaClientProperties = camundaClientProperties;
  }

  @Override
  public void customize(final JobWorkerValue jobWorkerValue) {
    applyDefaultWorkerName(jobWorkerValue);
    applyDefaultJobWorkerType(jobWorkerValue);
    applyDefaultJobWorkerTenantIds(jobWorkerValue);
    applyFetchVariables(jobWorkerValue);
    applyOverrides(jobWorkerValue);
  }

  private void applyFetchVariables(final JobWorkerValue jobWorkerValue) {
    if (hasActivatedJobInjected(jobWorkerValue)) {
      LOG.debug(
          "Worker '{}': ActivatedJob is injected, no variable filtering possible",
          jobWorkerValue.getName());
    } else if (jobWorkerValue.getForceFetchAllVariables() != null
        && jobWorkerValue.getForceFetchAllVariables()) {
      LOG.debug("Worker '{}': Force fetch all variables is enabled", jobWorkerValue.getName());
      jobWorkerValue.setFetchVariables(List.of());
    } else {
      final List<String> variables = new ArrayList<>();
      if (jobWorkerValue.getFetchVariables() != null) {
        variables.addAll(jobWorkerValue.getFetchVariables());
      }
      if (camundaClientProperties.getWorker().getDefaults().getFetchVariables() != null) {
        variables.addAll(camundaClientProperties.getWorker().getDefaults().getFetchVariables());
      }
      variables.addAll(jobWorkerValue.getJobWorkerFactory().getUsedVariableNames());
      jobWorkerValue.setFetchVariables(variables.stream().distinct().toList());
      LOG.debug(
          "Worker '{}': Fetching only required variables {}", jobWorkerValue.getName(), variables);
    }
  }

  private boolean hasActivatedJobInjected(final JobWorkerValue jobWorkerValue) {
    return jobWorkerValue.getJobWorkerFactory().usesActivatedJob();
  }

  private void applyOverrides(final JobWorkerValue editedJobWorkerValue) {
    final CamundaClientJobWorkerProperties defaults =
        camundaClientProperties.getWorker().getDefaults();
    if (defaults != null) {
      copyProperties(defaults, editedJobWorkerValue, OverrideSource.defaults);
    }
    final String workerType = editedJobWorkerValue.getType();
    findWorkerOverride(workerType)
        .ifPresent(
            jobWorkerValue -> {
              LOG.debug("Worker '{}': Applying overrides {}", workerType, jobWorkerValue);
              copyProperties(jobWorkerValue, editedJobWorkerValue, OverrideSource.worker);
            });
  }

  private Optional<CamundaClientJobWorkerProperties> findWorkerOverride(final String type) {
    return Optional.ofNullable(camundaClientProperties.getWorker().getOverride().get(type));
  }

  private void copyProperties(
      final CamundaClientJobWorkerProperties source,
      final JobWorkerValue target,
      final OverrideSource overrideSource) {
    if (overrideSource == OverrideSource.worker) {
      copyProperty(
          "fetchVariables", overrideSource, source::getFetchVariables, target::setFetchVariables);
      copyProperty("type", overrideSource, source::getType, target::setType);
      copyProperty("name", overrideSource, source::getName, target::setName);
      copyProperty("tenantIds", overrideSource, source::getTenantIds, target::setTenantIds);
    }
    copyProperty("timeout", overrideSource, source::getTimeout, target::setTimeout);
    copyProperty(
        "maxJobsActive", overrideSource, source::getMaxJobsActive, target::setMaxJobsActive);
    copyProperty(
        "requestTimeout", overrideSource, source::getRequestTimeout, target::setRequestTimeout);
    copyProperty("pollInterval", overrideSource, source::getPollInterval, target::setPollInterval);
    copyProperty("autoComplete", overrideSource, source::getAutoComplete, target::setAutoComplete);
    copyProperty("enabled", overrideSource, source::getEnabled, target::setEnabled);
    copyProperty(
        "streamEnabled", overrideSource, source::getStreamEnabled, target::setStreamEnabled);
    copyProperty(
        "streamTimeout", overrideSource, source::getStreamTimeout, target::setStreamTimeout);
    copyProperty(
        "forceFetchAllVariables",
        overrideSource,
        source::getForceFetchAllVariables,
        target::setForceFetchAllVariables);
    copyProperty("maxRetries", overrideSource, source::getMaxRetries, target::setMaxRetries);
  }

  private <T> void copyProperty(
      final String propertyName,
      final OverrideSource overrideSource,
      final Supplier<T> getter,
      final Consumer<T> setter) {
    final T value = getter.get();
    if (value != null) {
      LOG.debug("Overriding property '{}' from source {}", propertyName, overrideSource);
      setter.accept(value);
    }
  }

  private void applyDefaultWorkerName(final JobWorkerValue jobWorkerValue) {
    final String defaultJobWorkerName = camundaClientProperties.getWorker().getDefaults().getName();
    if (isBlank(jobWorkerValue.getName())) {
      if (isNotBlank(defaultJobWorkerName)
          && !DEFAULT_JOB_WORKER_NAME_VAR.equals(defaultJobWorkerName)) {
        LOG.debug(
            "Worker '{}': Setting name to default {}",
            jobWorkerValue.getName(),
            defaultJobWorkerName);
        jobWorkerValue.setName(defaultJobWorkerName);
      } else {
        final String generatedJobWorkerName =
            jobWorkerValue.getJobWorkerFactory().getGeneratedJobWorkerName();
        LOG.debug(
            "Worker '{}': Setting name to generated {}",
            jobWorkerValue.getName(),
            generatedJobWorkerName);
        jobWorkerValue.setName(generatedJobWorkerName);
      }
    }
  }

  private void applyDefaultJobWorkerType(final JobWorkerValue jobWorkerValue) {
    final String defaultJobWorkerType = camundaClientProperties.getWorker().getDefaults().getType();
    if (isBlank(jobWorkerValue.getType())) {
      if (isNotBlank(defaultJobWorkerType)) {
        LOG.debug(
            "Worker '{}': Setting type to default {}",
            jobWorkerValue.getName(),
            defaultJobWorkerType);
        jobWorkerValue.setType(defaultJobWorkerType);
      } else {

        final String generatedJobWorkerType =
            jobWorkerValue.getJobWorkerFactory().getGeneratedJobWorkerType();
        LOG.debug(
            "Worker '{}': Setting type to generated {}",
            jobWorkerValue.getName(),
            generatedJobWorkerType);
        jobWorkerValue.setType(generatedJobWorkerType);
      }
    }
  }

  private void applyDefaultJobWorkerTenantIds(final JobWorkerValue jobWorkerValue) {
    final Set<String> tenantIds = new HashSet<>();

    // we consider default worker tenant ids configurations first
    if (!DEFAULT_JOB_WORKER_TENANT_IDS.equals(
        camundaClientProperties.getWorker().getDefaults().getTenantIds())) {
      tenantIds.addAll(camundaClientProperties.getWorker().getDefaults().getTenantIds());
    } else {
      // the default tenant set on the client is included in the default if no other default is set
      tenantIds.add(camundaClientProperties.getTenantId());
    }

    // if set, worker annotation defaults get included as well
    if (jobWorkerValue.getTenantIds() != null) {
      tenantIds.addAll(jobWorkerValue.getTenantIds());
    }

    if (!tenantIds.isEmpty()) {
      LOG.debug("Worker '{}': Setting tenantIds to {}", jobWorkerValue.getName(), tenantIds);
      jobWorkerValue.setTenantIds(new ArrayList<>(tenantIds));
    }
  }

  private enum OverrideSource {
    defaults,
    worker
  }
}
