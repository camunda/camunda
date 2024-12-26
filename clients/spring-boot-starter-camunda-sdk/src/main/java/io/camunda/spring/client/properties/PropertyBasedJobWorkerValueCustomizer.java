/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties;

import static io.camunda.spring.client.annotation.AnnotationUtil.getVariableParameters;
import static io.camunda.spring.client.annotation.AnnotationUtil.getVariableValue;
import static io.camunda.spring.client.annotation.AnnotationUtil.getVariablesAsTypeParameters;
import static io.camunda.spring.client.configuration.PropertyUtil.getProperty;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.bean.CopyNotNullBeanUtilsBean;
import io.camunda.spring.client.bean.MethodInfo;
import io.camunda.spring.client.bean.ParameterInfo;
import io.camunda.spring.client.properties.common.ZeebeClientProperties;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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
  private static final CopyNotNullBeanUtilsBean BEAN_UTILS_BEAN = new CopyNotNullBeanUtilsBean();

  private final CamundaClientConfigurationProperties zeebeClientConfigurationProperties;
  private final CamundaClientProperties camundaClientProperties;

  public PropertyBasedJobWorkerValueCustomizer(
      final CamundaClientConfigurationProperties zeebeClientConfigurationProperties,
      final CamundaClientProperties camundaClientProperties) {
    this.zeebeClientConfigurationProperties = zeebeClientConfigurationProperties;
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
    return getVariableValue(parameterInfo).get().name();
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
    try {
      if (ofNullable(camundaClientProperties.getDefaults()).isPresent()) {
        BEAN_UTILS_BEAN.copyProperties(zeebeWorker, camundaClientProperties.getDefaults());
      }
      if (ofNullable(camundaClientProperties.getZeebe())
          .map(ZeebeClientProperties::getDefaults)
          .isPresent()) {
        BEAN_UTILS_BEAN.copyProperties(
            zeebeWorker, camundaClientProperties.getZeebe().getDefaults());
      }
    } catch (final IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(
          "Error while copying properties from "
              + camundaClientProperties.getZeebe().getDefaults()
              + " to "
              + zeebeWorker,
          e);
    }
    final Map<String, JobWorkerValue> workerConfigurationMap = new HashMap<>();
    workerConfigurationMap.putAll(camundaClientProperties.getZeebe().getOverride());
    workerConfigurationMap.putAll(camundaClientProperties.getOverride());
    final String workerType = zeebeWorker.getType();
    if (workerConfigurationMap.containsKey(workerType)) {
      final JobWorkerValue jobWorkerValue = workerConfigurationMap.get(workerType);
      LOG.debug("Worker '{}': Applying overrides {}", workerType, jobWorkerValue);
      try {
        BEAN_UTILS_BEAN.copyProperties(zeebeWorker, jobWorkerValue);
      } catch (final IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(
            "Error while copying properties from " + jobWorkerValue + " to " + zeebeWorker, e);
      }
    }
  }

  private void applyDefaultWorkerName(final JobWorkerValue zeebeWorker) {
    final String defaultJobWorkerName =
        getProperty(
            "DefaultJobWorkerName",
            null,
            null,
            () -> camundaClientProperties.getDefaults().getName(),
            () -> camundaClientProperties.getZeebe().getDefaults().getName(),
            zeebeClientConfigurationProperties::getDefaultJobWorkerName);
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
    final String defaultJobWorkerType =
        getProperty(
            "DefaultJobWorkerType",
            null,
            null,
            () -> camundaClientProperties.getDefaults().getType(),
            () -> camundaClientProperties.getZeebe().getDefaults().getType(),
            zeebeClientConfigurationProperties::getDefaultJobWorkerType);
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
    final List<String> defaultJobWorkerTenantIds =
        getProperty(
            "DefaultJobWorkerTenantIds",
            null,
            null,
            () -> camundaClientProperties.getDefaults().getTenantIds(),
            camundaClientProperties::getTenantIds,
            () -> camundaClientProperties.getZeebe().getDefaults().getTenantIds(),
            zeebeClientConfigurationProperties::getDefaultJobWorkerTenantIds);

    LOG.debug(
        "Worker '{}': Setting tenantIds to default {}",
        zeebeWorker.getTenantIds(),
        defaultJobWorkerTenantIds);
    zeebeWorker.setTenantIds(defaultJobWorkerTenantIds);
  }
}
