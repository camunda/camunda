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
package io.camunda.zeebe.spring.client.properties;

import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.DEFAULT;
import static org.apache.commons.lang3.StringUtils.*;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.Variable;
import io.camunda.zeebe.spring.client.annotation.VariablesAsType;
import io.camunda.zeebe.spring.client.annotation.customizer.ZeebeWorkerValueCustomizer;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import io.camunda.zeebe.spring.client.bean.CopyNotNullBeanUtilsBean;
import io.camunda.zeebe.spring.client.bean.MethodInfo;
import io.camunda.zeebe.spring.client.bean.ParameterInfo;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

public class PropertyBasedZeebeWorkerValueCustomizer implements ZeebeWorkerValueCustomizer {
  private static final Logger LOG =
      LoggerFactory.getLogger(PropertyBasedZeebeWorkerValueCustomizer.class);
  private static final CopyNotNullBeanUtilsBean BEAN_UTILS_BEAN = new CopyNotNullBeanUtilsBean();

  private final ZeebeClientConfigurationProperties zeebeClientConfigurationProperties;

  public PropertyBasedZeebeWorkerValueCustomizer(
      final ZeebeClientConfigurationProperties zeebeClientConfigurationProperties) {
    this.zeebeClientConfigurationProperties = zeebeClientConfigurationProperties;
  }

  @Override
  public void customize(final ZeebeWorkerValue zeebeWorker) {
    applyDefaultWorkerName(zeebeWorker);
    applyDefaultJobWorkerType(zeebeWorker);
    applyDefaultJobWorkerTenantIds(zeebeWorker);
    applyFetchVariables(zeebeWorker);
    applyOverrides(zeebeWorker);
  }

  private void applyFetchVariables(final ZeebeWorkerValue zeebeWorkerValue) {
    if (hasActivatedJobInjected(zeebeWorkerValue)) {
      LOG.debug(
          "Worker '{}': ActivatedJob is injected, no variable filtering possible",
          zeebeWorkerValue.getName());
    } else if (zeebeWorkerValue.isForceFetchAllVariables()) {
      LOG.debug("Worker '{}': Force fetch all variables is enabled", zeebeWorkerValue.getName());
      zeebeWorkerValue.setFetchVariables(new String[0]);
    } else {
      final Set<String> variables = new HashSet<>();
      if (zeebeWorkerValue.getFetchVariables() != null) {
        variables.addAll(Arrays.asList(zeebeWorkerValue.getFetchVariables()));
      }
      variables.addAll(
          readZeebeVariableParameters(zeebeWorkerValue.getMethodInfo()).stream()
              .map(this::extractVariableName)
              .collect(Collectors.toList()));
      variables.addAll(readVariablesAsTypeParameters(zeebeWorkerValue.getMethodInfo()));
      zeebeWorkerValue.setFetchVariables(variables.toArray(new String[0]));
      LOG.debug(
          "Worker '{}': Fetching only required variables {}",
          zeebeWorkerValue.getName(),
          variables);
    }
  }

  private boolean hasActivatedJobInjected(final ZeebeWorkerValue zeebeWorkerValue) {
    return zeebeWorkerValue.getMethodInfo().getParameters().stream()
        .anyMatch(p -> p.getParameterInfo().getType().isAssignableFrom(ActivatedJob.class));
  }

  private List<ParameterInfo> readZeebeVariableParameters(final MethodInfo methodInfo) {
    return methodInfo.getParametersFilteredByAnnotation(Variable.class);
  }

  private String extractVariableName(final ParameterInfo parameterInfo) {
    final Variable variableAnnotation =
        parameterInfo.getParameterInfo().getAnnotation(Variable.class);
    if (variableAnnotation != null && !Variable.DEFAULT_NAME.equals(variableAnnotation.name())) {
      return variableAnnotation.name();
    }
    return parameterInfo.getParameterName();
  }

  private List<String> readVariablesAsTypeParameters(final MethodInfo methodInfo) {
    final List<String> result = new ArrayList<>();
    final List<ParameterInfo> parameters =
        methodInfo.getParametersFilteredByAnnotation(VariablesAsType.class);
    parameters.forEach(
        pi ->
            ReflectionUtils.doWithFields(
                pi.getParameterInfo().getType(), f -> result.add(f.getName())));
    return result;
  }

  private void applyOverrides(final ZeebeWorkerValue zeebeWorker) {
    final Map<String, ZeebeWorkerValue> workerConfigurationMap =
        zeebeClientConfigurationProperties.getWorker().getOverride();
    final String workerType = zeebeWorker.getType();
    if (workerConfigurationMap.containsKey(workerType)) {
      final ZeebeWorkerValue zeebeWorkerValue = workerConfigurationMap.get(workerType);
      LOG.debug("Worker '{}': Applying overrides {}", workerType, zeebeWorkerValue);
      try {
        BEAN_UTILS_BEAN.copyProperties(zeebeWorker, zeebeWorkerValue);
      } catch (final IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(
            "Error while copying properties from " + zeebeWorkerValue + " to " + zeebeWorker, e);
      }
    }
  }

  private void applyDefaultWorkerName(final ZeebeWorkerValue zeebeWorker) {
    final String defaultJobWorkerName =
        zeebeClientConfigurationProperties.getDefaultJobWorkerName();
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

  private void applyDefaultJobWorkerType(final ZeebeWorkerValue zeebeWorker) {
    final String defaultJobWorkerType =
        zeebeClientConfigurationProperties.getDefaultJobWorkerType();
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

  private void applyDefaultJobWorkerTenantIds(final ZeebeWorkerValue zeebeWorker) {
    final List<String> defaultJobWorkerTenantIds =
        zeebeClientConfigurationProperties.getDefaultJobWorkerTenantIds();
    if (zeebeWorker.getTenantIds() == null || zeebeWorker.getTenantIds().isEmpty()) {
      if (!defaultJobWorkerTenantIds.isEmpty()) {
        LOG.debug(
            "Worker '{}': Setting tenantIds {}",
            zeebeWorker.getTenantIds(),
            defaultJobWorkerTenantIds);
        zeebeWorker.setTenantIds(defaultJobWorkerTenantIds);
      }
    } else {
      final var defaultTenantIds = DEFAULT.getDefaultJobWorkerTenantIds();
      LOG.debug(
          "Worker '{}': Setting tenantIds to default {}", zeebeWorker.getName(), defaultTenantIds);
      zeebeWorker.setTenantIds(defaultTenantIds);
    }
  }
}
