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

import static io.camunda.spring.client.annotation.AnnotationUtil.getVariableParameters;
import static io.camunda.spring.client.annotation.AnnotationUtil.getVariableValue;
import static io.camunda.spring.client.annotation.AnnotationUtil.getVariablesAsTypeParameters;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.bean.CopyNotNullBeanUtilsBean;
import io.camunda.spring.client.bean.CopyWithProtectionBeanUtilsBean;
import io.camunda.spring.client.bean.MethodInfo;
import io.camunda.spring.client.bean.ParameterInfo;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

public class PropertyBasedJobWorkerValueCustomizer implements JobWorkerValueCustomizer {
  private static final Logger LOG =
      LoggerFactory.getLogger(PropertyBasedJobWorkerValueCustomizer.class);
  private static final CopyNotNullBeanUtilsBean COPY_NOT_NULL_BEAN_UTILS_BEAN =
      new CopyNotNullBeanUtilsBean();
  private static final CopyWithProtectionBeanUtilsBean COPY_WITH_PROTECTION_BEAN_UTILS_BEAN =
      new CopyWithProtectionBeanUtilsBean(Set.of("name", "type", "fetchVariables"));

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
      final Set<String> variables = new HashSet<>();
      if (jobWorkerValue.getFetchVariables() != null) {
        variables.addAll(jobWorkerValue.getFetchVariables());
      }
      variables.addAll(
          readZeebeVariableParameters(jobWorkerValue.getMethodInfo()).stream()
              .map(this::extractVariableName)
              .toList());
      variables.addAll(readVariablesAsTypeParameters(jobWorkerValue.getMethodInfo()));
      jobWorkerValue.setFetchVariables(variables.stream().toList());
      LOG.debug(
          "Worker '{}': Fetching only required variables {}", jobWorkerValue.getName(), variables);
    }
  }

  private boolean hasActivatedJobInjected(final JobWorkerValue jobWorkerValue) {
    return jobWorkerValue.getMethodInfo().getParameters().stream()
        .anyMatch(p -> p.getParameterInfo().getType().isAssignableFrom(ActivatedJob.class));
  }

  private List<ParameterInfo> readZeebeVariableParameters(final MethodInfo methodInfo) {
    return getVariableParameters(methodInfo);
  }

  private String extractVariableName(final ParameterInfo parameterInfo) {
    // get can be used here as the list is already filtered by readZeebeVariableParameters
    return getVariableValue(parameterInfo).get().getName();
  }

  private List<String> readVariablesAsTypeParameters(final MethodInfo methodInfo) {
    final List<String> result = new ArrayList<>();
    final List<ParameterInfo> parameters = getVariablesAsTypeParameters(methodInfo);
    parameters.forEach(
        pi ->
            ReflectionUtils.doWithFields(
                pi.getParameterInfo().getType(), f -> result.add(extractFieldName(f))));
    return result;
  }

  private String extractFieldName(final Field field) {
    if (field.isAnnotationPresent(JsonProperty.class)) {
      final String value = field.getAnnotation(JsonProperty.class).value();
      if (StringUtils.isNotBlank(value)) {
        return value;
      }
    }
    return field.getName();
  }

  private void applyOverrides(final JobWorkerValue zeebeWorker) {
    final JobWorkerValue defaults = camundaClientProperties.getWorker().getDefaults();
    try {
      if (defaults != null) {
        COPY_WITH_PROTECTION_BEAN_UTILS_BEAN.copyProperties(zeebeWorker, defaults);
      }
    } catch (final IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(
          "Error while copying properties from " + defaults + " to " + zeebeWorker, e);
    }
    final Map<String, JobWorkerValue> workerConfigurationMap = new HashMap<>();
    if (camundaClientProperties.getWorker().getOverride() != null) {
      workerConfigurationMap.putAll(camundaClientProperties.getWorker().getOverride());
    }
    final String workerType = zeebeWorker.getType();
    if (workerConfigurationMap.containsKey(workerType)) {
      final JobWorkerValue jobWorkerValue = workerConfigurationMap.get(workerType);
      LOG.debug("Worker '{}': Applying overrides {}", workerType, jobWorkerValue);
      try {
        COPY_NOT_NULL_BEAN_UTILS_BEAN.copyProperties(zeebeWorker, jobWorkerValue);
      } catch (final IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(
            "Error while copying properties from " + jobWorkerValue + " to " + zeebeWorker, e);
      }
    }
  }

  private void applyDefaultWorkerName(final JobWorkerValue zeebeWorker) {
    final String defaultJobWorkerName = camundaClientProperties.getWorker().getDefaults().getName();
    if (isBlank(zeebeWorker.getName())) {
      if (isNotBlank(defaultJobWorkerName)) {
        LOG.debug(
            "Worker '{}': Setting name to default {}", zeebeWorker.getName(), defaultJobWorkerName);
        zeebeWorker.setName(defaultJobWorkerName);
      } else {
        final String generatedJobWorkerName =
            zeebeWorker.getMethodInfo().getBeanName()
                + "#"
                + zeebeWorker.getMethodInfo().getMethodName();
        LOG.debug(
            "Worker '{}': Setting name to generated {}",
            zeebeWorker.getName(),
            generatedJobWorkerName);
        zeebeWorker.setName(generatedJobWorkerName);
      }
    }
  }

  private void applyDefaultJobWorkerType(final JobWorkerValue zeebeWorker) {
    final String defaultJobWorkerType = camundaClientProperties.getWorker().getDefaults().getType();
    if (isBlank(zeebeWorker.getType())) {
      if (isNotBlank(defaultJobWorkerType)) {
        LOG.debug(
            "Worker '{}': Setting type to default {}", zeebeWorker.getName(), defaultJobWorkerType);
        zeebeWorker.setType(defaultJobWorkerType);
      } else {
        final String generatedJobWorkerType = zeebeWorker.getMethodInfo().getMethodName();
        LOG.debug(
            "Worker '{}': Setting type to generated {}",
            zeebeWorker.getName(),
            generatedJobWorkerType);
        zeebeWorker.setType(generatedJobWorkerType);
      }
    }
  }

  private void applyDefaultJobWorkerTenantIds(final JobWorkerValue zeebeWorker) {
    final Set<String> defaultJobWorkerTenantIds =
        new HashSet<>(
            ofNullable(camundaClientProperties.getWorker().getDefaults().getTenantIds())
                .orElse(Collections.emptyList()));
    if (zeebeWorker.getTenantIds() == null || zeebeWorker.getTenantIds().isEmpty()) {
      if (!defaultJobWorkerTenantIds.isEmpty()) {
        LOG.debug(
            "Worker '{}': Setting tenantIds to default {}",
            zeebeWorker.getTenantIds(),
            defaultJobWorkerTenantIds);
        zeebeWorker.setTenantIds(new ArrayList<>(defaultJobWorkerTenantIds));
      }
    }
  }
}
