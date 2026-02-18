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

import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_NAME_VAR;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_TENANT_IDS;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.*;

import io.camunda.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.Empty;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.FromDefaultProperty;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.FromOverrideProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
    applyForceFetchAll(jobWorkerValue);
  }

  private void applyForceFetchAll(final JobWorkerValue jobWorkerValue) {
    if (!(jobWorkerValue.getForceFetchAllVariables() instanceof Empty)
        && jobWorkerValue.getForceFetchAllVariables().value()) {
      LOG.debug(
          "Worker '{}': Force fetch all variables is enabled", jobWorkerValue.getName().value());
      jobWorkerValue.setFetchVariables(List.of());
    }
  }

  private void applyFetchVariables(final JobWorkerValue jobWorkerValue) {
    final List<SourceAware<String>> variables = new ArrayList<>();
    if (jobWorkerValue.getFetchVariables() != null) {
      variables.addAll(jobWorkerValue.getFetchVariables());
    }
    if (camundaClientProperties.getWorker().getDefaults().getFetchVariables() != null) {
      variables.addAll(
          camundaClientProperties.getWorker().getDefaults().getFetchVariables().stream()
              .map(FromDefaultProperty::new)
              .toList());
    }
    jobWorkerValue.setFetchVariables(variables.stream().distinct().toList());
    LOG.debug(
        "Worker '{}': Fetching only required variables {}",
        jobWorkerValue.getName().value(),
        variables);
  }

  private void applyOverrides(final JobWorkerValue editedJobWorkerValue) {
    final CamundaClientJobWorkerProperties defaults =
        camundaClientProperties.getWorker().getDefaults();
    if (defaults != null) {
      copyProperties(defaults, editedJobWorkerValue, OverrideSource.defaults);
    }
    final String workerType = editedJobWorkerValue.getType().value();
    findWorkerOverride(workerType)
        .ifPresent(
            jobWorkerValue -> {
              LOG.debug("Worker '{}': Applying overrides {}", workerType, jobWorkerValue);
              copyProperties(jobWorkerValue, editedJobWorkerValue, OverrideSource.worker);
            });
  }

  private Optional<CamundaClientJobWorkerProperties> findWorkerOverride(final String type) {
    return ofNullable(camundaClientProperties.getWorker().getOverride().get(type));
  }

  private Supplier<List<String>> fetchVariablesSuppliers(
      final CamundaClientJobWorkerProperties source) {
    return source::getFetchVariables;
  }

  private Consumer<List<SourceAware<String>>> fetchVariablesConsumer(final JobWorkerValue target) {
    return target::setFetchVariables;
  }

  private Supplier<List<String>> tenantIdsSupplier(final CamundaClientJobWorkerProperties source) {
    return source::getTenantIds;
  }

  private Consumer<List<SourceAware<String>>> tenantIdsConsumer(final JobWorkerValue target) {
    return target::setTenantIds;
  }

  private void copyProperties(
      final CamundaClientJobWorkerProperties source,
      final JobWorkerValue target,
      final OverrideSource overrideSource) {
    if (overrideSource == OverrideSource.worker) {
      copyPropertyList(
          "fetchVariables",
          overrideSource,
          fetchVariablesSuppliers(source),
          fetchVariablesConsumer(target));
      copyProperty("type", overrideSource, source::getType, target::setType, target.getType());
      copyProperty("name", overrideSource, source::getName, target::setName, target.getName());
      copyPropertyList(
          "tenantIds", overrideSource, tenantIdsSupplier(source), tenantIdsConsumer(target));
    }
    copyProperty(
        "timeout", overrideSource, source::getTimeout, target::setTimeout, target.getTimeout());
    copyProperty(
        "maxJobsActive",
        overrideSource,
        source::getMaxJobsActive,
        target::setMaxJobsActive,
        target.getMaxJobsActive());
    copyProperty(
        "requestTimeout",
        overrideSource,
        source::getRequestTimeout,
        target::setRequestTimeout,
        target.getRequestTimeout());
    copyProperty(
        "pollInterval",
        overrideSource,
        source::getPollInterval,
        target::setPollInterval,
        target.getPollInterval());
    copyProperty(
        "autoComplete",
        overrideSource,
        source::getAutoComplete,
        target::setAutoComplete,
        target.getAutoComplete());
    copyProperty(
        "enabled", overrideSource, source::getEnabled, target::setEnabled, target.getEnabled());
    copyProperty(
        "streamEnabled",
        overrideSource,
        source::getStreamEnabled,
        target::setStreamEnabled,
        target.getStreamEnabled());
    copyProperty(
        "streamTimeout",
        overrideSource,
        source::getStreamTimeout,
        target::setStreamTimeout,
        target.getStreamTimeout());
    copyProperty(
        "forceFetchAllVariables",
        overrideSource,
        source::getForceFetchAllVariables,
        target::setForceFetchAllVariables,
        target.getForceFetchAllVariables());
    copyProperty(
        "maxRetries",
        overrideSource,
        source::getMaxRetries,
        target::setMaxRetries,
        target.getMaxRetries());
    copyProperty(
        "retryBackoff",
        overrideSource,
        source::getRetryBackoff,
        target::setRetryBackoff,
        target.getRetryBackoff());
    copyProperty(
        "tenantFilterMode",
        overrideSource,
        source::getTenantFilterMode,
        target::setTenantFilterMode,
        target.getTenantFilterMode());
  }

  private <T> void copyPropertyList(
      final String propertyName,
      final OverrideSource overrideSource,
      final Supplier<List<T>> getter,
      final Consumer<List<SourceAware<T>>> setter) {
    final List<T> values = getter.get();
    if (values != null) {
      LOG.debug("Overriding property '{}' from source {}", propertyName, overrideSource);
      final List<SourceAware<T>> sourceAwares =
          values.stream()
              .map(
                  value ->
                      switch (overrideSource) {
                        case worker -> new FromOverrideProperty<>(value);
                        case defaults ->
                            throw new IllegalStateException(
                                "List overrides are only supported for workers");
                      })
              .map(value -> (SourceAware<T>) value)
              .toList();
      setter.accept(sourceAwares);
    }
  }

  private <T> void copyProperty(
      final String propertyName,
      final OverrideSource overrideSource,
      final Supplier<T> getter,
      final Consumer<SourceAware<T>> setter,
      final SourceAware<T> currentValue) {
    final T value = getter.get();
    if (value != null) {
      final SourceAware<T> sourceAware =
          switch (overrideSource) {
            case worker -> new FromOverrideProperty<>(value);
            case defaults -> new FromDefaultProperty<>(value);
          };
      if (sourceAware.priority() > currentValue.priority()) {
        LOG.debug("Overriding property '{}' from source {}", propertyName, overrideSource);
        setter.accept(sourceAware);
      }
    }
  }

  private void applyDefaultWorkerName(final JobWorkerValue jobWorkerValue) {
    final String defaultJobWorkerName = camundaClientProperties.getWorker().getDefaults().getName();
    final SourceAware<String> jobWorkerName = jobWorkerValue.getName();
    if (jobWorkerName.generated()) {
      if (isNotBlank(defaultJobWorkerName)
          && !DEFAULT_JOB_WORKER_NAME_VAR.equals(defaultJobWorkerName)) {
        LOG.debug(
            "Worker '{}': Setting name to default {}",
            jobWorkerValue.getName().value(),
            defaultJobWorkerName);
        jobWorkerValue.setName(new FromDefaultProperty<>(defaultJobWorkerName));
      } else {
        LOG.debug("Worker '{}': Using generated name", jobWorkerValue.getName().value());
      }
    }
  }

  private void applyDefaultJobWorkerType(final JobWorkerValue jobWorkerValue) {
    final String defaultJobWorkerType = camundaClientProperties.getWorker().getDefaults().getType();
    final SourceAware<String> jobWorkerType = jobWorkerValue.getType();
    if (jobWorkerType.generated()) {
      if (isNotBlank(defaultJobWorkerType)) {
        LOG.debug(
            "Worker '{}': Setting type to default {}",
            jobWorkerValue.getName().value(),
            defaultJobWorkerType);
        jobWorkerValue.setType(new FromDefaultProperty<>(defaultJobWorkerType));
      } else {
        LOG.debug("Worker '{}': Using generated type", jobWorkerValue.getName().value());
      }
    }
  }

  private void applyDefaultJobWorkerTenantIds(final JobWorkerValue jobWorkerValue) {
    final List<SourceAware<String>> tenantIds = new ArrayList<>();

    // we consider default worker tenant ids configurations first
    if (!DEFAULT_JOB_WORKER_TENANT_IDS.equals(
        camundaClientProperties.getWorker().getDefaults().getTenantIds())) {
      tenantIds.addAll(
          camundaClientProperties.getWorker().getDefaults().getTenantIds().stream()
              .map(FromDefaultProperty::new)
              .toList());
    } else {
      // the default tenant set on the client is included in the default if no other default is set
      tenantIds.add(new FromDefaultProperty<>(camundaClientProperties.getTenantId()));
    }

    // if set, worker annotation defaults get included as well
    if (jobWorkerValue.getTenantIds() != null) {
      tenantIds.addAll(jobWorkerValue.getTenantIds());
    }

    if (!tenantIds.isEmpty()) {
      LOG.debug(
          "Worker '{}': Setting tenantIds to {}", jobWorkerValue.getName().value(), tenantIds);
      jobWorkerValue.setTenantIds(new ArrayList<>(tenantIds));
    }
  }

  private enum OverrideSource {
    defaults,
    worker
  }
}
