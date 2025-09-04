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

import static io.camunda.client.annotation.AnnotationUtil.getVariableParameters;
import static io.camunda.client.annotation.AnnotationUtil.getVariableValue;
import static io.camunda.client.annotation.AnnotationUtil.getVariablesAsTypeParameters;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_NAME_VAR;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_JOB_WORKER_TENANT_IDS;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.*;

import io.camunda.spring.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.annotation.value.JobWorkerValue.FetchVariable;
import io.camunda.spring.client.annotation.value.JobWorkerValue.FieldSource;
import io.camunda.spring.client.annotation.value.JobWorkerValue.Name;
import io.camunda.spring.client.annotation.value.JobWorkerValue.Type;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.bean.ParameterInfo;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
  }

  private void applyFetchVariables(final JobWorkerValue jobWorkerValue) {
    if (jobWorkerValue.getForceFetchAllVariables() != null
        && jobWorkerValue.getForceFetchAllVariables()) {
      LOG.debug(
          "Worker '{}': Force fetch all variables is enabled", jobWorkerValue.getName().value());
      jobWorkerValue.setFetchVariables(List.of());
    } else {
      final List<FetchVariable> variables = new ArrayList<>();
      if (jobWorkerValue.getFetchVariables() != null) {
        variables.addAll(jobWorkerValue.getFetchVariables());
      }
      if (camundaClientProperties.getWorker().getDefaults().getFetchVariables() != null) {
        variables.addAll(
            camundaClientProperties.getWorker().getDefaults().getFetchVariables().stream()
                .map(
                    fetchVariable ->
                        new FetchVariable(fetchVariable, FieldSource.FROM_DEFAULT_PROPERTIES))
                .toList());
      }
      jobWorkerValue.setFetchVariables(variables.stream().distinct().toList());
      LOG.debug(
          "Worker '{}': Fetching only required variables {}",
          jobWorkerValue.getName().value(),
          variables);
    }
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

  private void copyProperties(
      final CamundaClientJobWorkerProperties source,
      final JobWorkerValue target,
      final OverrideSource overrideSource) {
    final FieldSource fieldSource =
        switch (overrideSource) {
          case worker -> FieldSource.FROM_OVERRIDE_PROPERTIES;
          case defaults -> FieldSource.FROM_DEFAULT_PROPERTIES;
        };
    if (overrideSource == OverrideSource.worker) {
      copyProperty(
          "fetchVariables",
          overrideSource,
          () ->
              ofNullable(source.getFetchVariables())
                  .map(
                      fetchVariables ->
                          fetchVariables.stream()
                              .map(fetchVariable -> new FetchVariable(fetchVariable, fieldSource))
                              .toList())
                  .orElse(null),
          target::setFetchVariables);
      copyProperty(
          "type", overrideSource, () -> new Type(source.getType(), fieldSource), target::setType);
      copyProperty(
          "name", overrideSource, () -> new Name(source.getName(), fieldSource), target::setName);
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
    copyProperty("retryBackoff", overrideSource, source::getRetryBackoff, target::setRetryBackoff);
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
    final Name jobWorkerName = jobWorkerValue.getName();
    if (jobWorkerName.source().isGenerated()) {
      if (isNotBlank(defaultJobWorkerName)
          && !DEFAULT_JOB_WORKER_NAME_VAR.equals(defaultJobWorkerName)) {
        LOG.debug(
            "Worker '{}': Setting name to default {}",
            jobWorkerValue.getName().value(),
            defaultJobWorkerName);
        jobWorkerValue.setName(new Name(defaultJobWorkerName, FieldSource.FROM_DEFAULT_PROPERTIES));
      } else {
        LOG.debug("Worker '{}': Using generated name", jobWorkerValue.getName().value());
      }
    }
  }

  private void applyDefaultJobWorkerType(final JobWorkerValue jobWorkerValue) {
    final String defaultJobWorkerType = camundaClientProperties.getWorker().getDefaults().getType();
    final Type jobWorkerType = jobWorkerValue.getType();
    if (jobWorkerType.source().isGenerated()) {
      if (isNotBlank(defaultJobWorkerType)) {
        LOG.debug(
            "Worker '{}': Setting type to default {}",
            jobWorkerValue.getName().value(),
            defaultJobWorkerType);
        jobWorkerValue.setType(new Type(defaultJobWorkerType, FieldSource.FROM_DEFAULT_PROPERTIES));
      } else {
        LOG.debug("Worker '{}': Using generated type", jobWorkerValue.getName().value());
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
