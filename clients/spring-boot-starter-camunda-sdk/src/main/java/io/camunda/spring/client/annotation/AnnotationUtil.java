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
package io.camunda.spring.client.annotation;

import static java.util.Optional.ofNullable;

import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.spring.client.annotation.value.DeploymentValue;
import io.camunda.spring.client.annotation.value.DocumentValue;
import io.camunda.spring.client.annotation.value.DocumentValue.ParameterType;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.annotation.value.VariableValue;
import io.camunda.spring.client.bean.BeanInfo;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.bean.MethodInfo;
import io.camunda.spring.client.bean.ParameterInfo;
import io.camunda.spring.client.jobhandling.DocumentContext;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnotationUtil {
  private static final Logger LOG = LoggerFactory.getLogger(AnnotationUtil.class);
  private static final Map<String, ParameterType> DOCUMENT_PARAMETER_TYPES;

  static {
    DOCUMENT_PARAMETER_TYPES = new HashMap<>();
    DOCUMENT_PARAMETER_TYPES.put(
        List.class.getName() + "<" + DocumentReferenceResponse.class.getName() + ">",
        ParameterType.LIST);
    DOCUMENT_PARAMETER_TYPES.put(DocumentReferenceResponse.class.getName(), ParameterType.SINGLE);
    DOCUMENT_PARAMETER_TYPES.put(DocumentContext.class.getName(), ParameterType.CONTEXT);
  }

  public static boolean isVariable(final ParameterInfo parameterInfo) {
    return parameterInfo.getParameterInfo().isAnnotationPresent(Variable.class)
        || isVariableLegacy(parameterInfo);
  }

  public static boolean isDocument(final ParameterInfo parameterInfo) {
    return parameterInfo.getParameterInfo().isAnnotationPresent(Document.class)
        && (DOCUMENT_PARAMETER_TYPES.containsKey(
            parameterInfo.getParameterInfo().getParameterizedType().getTypeName()));
  }

  public static List<ParameterInfo> getVariableParameters(final MethodInfo methodInfo) {
    final List<ParameterInfo> result = new ArrayList<>();
    result.addAll(methodInfo.getParametersFilteredByAnnotation(Variable.class));
    result.addAll(
        methodInfo.getParametersFilteredByAnnotation(
            io.camunda.zeebe.spring.client.annotation.Variable.class));
    return result;
  }

  public static List<ParameterInfo> getVariablesAsTypeParameters(final MethodInfo methodInfo) {
    final List<ParameterInfo> result = new ArrayList<>();
    result.addAll(methodInfo.getParametersFilteredByAnnotation(VariablesAsType.class));
    result.addAll(
        methodInfo.getParametersFilteredByAnnotation(
            io.camunda.zeebe.spring.client.annotation.VariablesAsType.class));
    return result;
  }

  public static boolean isVariablesAsType(final ParameterInfo parameterInfo) {
    return parameterInfo.getParameterInfo().isAnnotationPresent(VariablesAsType.class)
        || isVariablesAsTypeLegacy(parameterInfo);
  }

  public static boolean isCustomHeaders(final ParameterInfo parameterInfo) {
    return parameterInfo.getParameterInfo().isAnnotationPresent(CustomHeaders.class)
        || isCustomHeadersLegacy(parameterInfo);
  }

  public static boolean isDeployment(final ClassInfo classInfo) {
    return classInfo.hasClassAnnotation(Deployment.class) || isDeploymentLegacy(classInfo);
  }

  public static boolean isJobWorker(final BeanInfo beanInfo) {
    return beanInfo.hasMethodAnnotation(JobWorker.class) || isJobWorkerLegacy(beanInfo);
  }

  private static boolean isJobWorkerLegacy(final BeanInfo beanInfo) {
    return beanInfo.hasMethodAnnotation(io.camunda.zeebe.spring.client.annotation.JobWorker.class);
  }

  public static Optional<JobWorkerValue> getJobWorkerValue(final MethodInfo methodInfo) {
    return getJobWorkerValueInternal(methodInfo)
        .or(() -> getJobWorkerValueLegacyInternal(methodInfo));
  }

  private static Optional<JobWorkerValue> getJobWorkerValueInternal(final MethodInfo methodInfo) {
    final Optional<JobWorker> methodAnnotation = methodInfo.getAnnotation(JobWorker.class);
    if (methodAnnotation.isPresent()) {
      final JobWorker annotation = methodAnnotation.get();
      return Optional.of(
          new JobWorkerValue(
              annotation.type(),
              annotation.name(),
              Duration.of(annotation.timeout(), ChronoUnit.MILLIS),
              annotation.maxJobsActive(),
              Duration.of(annotation.requestTimeout(), ChronoUnit.SECONDS),
              Duration.of(annotation.pollInterval(), ChronoUnit.MILLIS),
              annotation.autoComplete(),
              Arrays.asList(annotation.fetchVariables()),
              annotation.enabled(),
              methodInfo,
              Arrays.asList(annotation.tenantIds()),
              annotation.fetchAllVariables(),
              annotation.streamEnabled(),
              Duration.of(annotation.streamTimeout(), ChronoUnit.MILLIS),
              annotation.maxRetries()));
    }
    return Optional.empty();
  }

  private static Optional<JobWorkerValue> getJobWorkerValueLegacyInternal(
      final MethodInfo methodInfo) {
    final Optional<io.camunda.zeebe.spring.client.annotation.JobWorker> methodAnnotation =
        methodInfo.getAnnotation(io.camunda.zeebe.spring.client.annotation.JobWorker.class);
    if (methodAnnotation.isPresent()) {
      final io.camunda.zeebe.spring.client.annotation.JobWorker annotation = methodAnnotation.get();
      return Optional.of(
          new JobWorkerValue(
              annotation.type(),
              annotation.name(),
              Duration.of(annotation.timeout(), ChronoUnit.MILLIS),
              annotation.maxJobsActive(),
              Duration.of(annotation.requestTimeout(), ChronoUnit.SECONDS),
              Duration.of(annotation.pollInterval(), ChronoUnit.MILLIS),
              annotation.autoComplete(),
              Arrays.asList(annotation.fetchVariables()),
              annotation.enabled(),
              methodInfo,
              Arrays.asList(annotation.tenantIds()),
              annotation.fetchAllVariables(),
              annotation.streamEnabled(),
              Duration.of(annotation.streamTimeout(), ChronoUnit.MILLIS),
              annotation.maxRetries()));
    }
    return Optional.empty();
  }

  public static Optional<VariableValue> getVariableValue(final ParameterInfo parameterInfo) {
    if (isVariable(parameterInfo)) {
      if (!isVariableLegacy(parameterInfo)) {
        return Optional.of(
            new VariableValue(
                getVariableName(parameterInfo), parameterInfo, getVariableOptional(parameterInfo)));
      } else {
        return Optional.of(
            new VariableValue(getVariableNameLegacy(parameterInfo), parameterInfo, true));
      }
    }
    return Optional.empty();
  }

  public static Optional<DocumentValue> getDocumentValue(final ParameterInfo parameterInfo) {
    if (isDocument(parameterInfo)) {
      return Optional.of(
          new DocumentValue(
              getDocumentName(parameterInfo),
              parameterInfo,
              getDocumentOptional(parameterInfo),
              getDocumentParameterType(parameterInfo)));
    }
    return Optional.empty();
  }

  private static ParameterType getDocumentParameterType(final ParameterInfo parameterInfo) {
    return ofNullable(
            DOCUMENT_PARAMETER_TYPES.get(
                parameterInfo.getParameterInfo().getParameterizedType().getTypeName()))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported parameter type for document: "
                        + parameterInfo.getParameterInfo().getParameterizedType().getTypeName()));
  }

  public static Optional<DeploymentValue> getDeploymentValue(final ClassInfo beanInfo) {
    if (isDeployment(beanInfo)) {
      final List<String> resources = new ArrayList<>();
      resources.addAll(getDeploymentResources(beanInfo));
      resources.addAll(getDeploymentResourcesLegacy(beanInfo));
      return Optional.of(DeploymentValue.builder().beanInfo(beanInfo).resources(resources).build());
    }
    return Optional.empty();
  }

  private static List<String> getDeploymentResources(final ClassInfo beanInfo) {
    return beanInfo
        .getAnnotation(Deployment.class)
        .map(Deployment::resources)
        .map(Arrays::asList)
        .orElseGet(List::of);
  }

  private static List<String> getDeploymentResourcesLegacy(final ClassInfo beanInfo) {
    return beanInfo
        .getAnnotation(io.camunda.zeebe.spring.client.annotation.Deployment.class)
        .map(io.camunda.zeebe.spring.client.annotation.Deployment::resources)
        .map(Arrays::asList)
        .orElseGet(List::of);
  }

  private static boolean isVariableLegacy(final ParameterInfo parameterInfo) {
    return parameterInfo
        .getParameterInfo()
        .isAnnotationPresent(io.camunda.zeebe.spring.client.annotation.Variable.class);
  }

  private static boolean isVariablesAsTypeLegacy(final ParameterInfo parameterInfo) {
    return parameterInfo
        .getParameterInfo()
        .isAnnotationPresent(io.camunda.zeebe.spring.client.annotation.VariablesAsType.class);
  }

  private static boolean isCustomHeadersLegacy(final ParameterInfo parameterInfo) {
    return parameterInfo
        .getParameterInfo()
        .isAnnotationPresent(io.camunda.zeebe.spring.client.annotation.CustomHeaders.class);
  }

  public static boolean isDeploymentLegacy(final ClassInfo classInfo) {
    return classInfo.hasClassAnnotation(io.camunda.zeebe.spring.client.annotation.Deployment.class);
  }

  private static String getVariableName(final ParameterInfo param) {
    if (param.getParameterInfo().isAnnotationPresent(Variable.class)) {
      final String nameFromAnnotation =
          param.getParameterInfo().getAnnotation(Variable.class).name();
      if (StringUtils.isNotBlank(nameFromAnnotation)) {
        LOG.trace("Extracting name {} from Variable.name", nameFromAnnotation);
        return nameFromAnnotation;
      }
      final String valueFromAnnotation =
          param.getParameterInfo().getAnnotation(Variable.class).value();
      if (StringUtils.isNotBlank(valueFromAnnotation)) {
        LOG.trace("Extracting name {} from Variable.value", valueFromAnnotation);
        return valueFromAnnotation;
      }
    }
    LOG.trace("Extracting variable name from parameter name");
    return param.getParameterName();
  }

  private static String getDocumentName(final ParameterInfo param) {
    if (param.getParameterInfo().isAnnotationPresent(Document.class)) {
      final String nameFromAnnotation =
          param.getParameterInfo().getAnnotation(Document.class).name();
      if (StringUtils.isNotBlank(nameFromAnnotation)) {
        LOG.trace("Extracting name {} from Document.name", nameFromAnnotation);
        return nameFromAnnotation;
      }
      final String valueFromAnnotation =
          param.getParameterInfo().getAnnotation(Document.class).value();
      if (StringUtils.isNotBlank(valueFromAnnotation)) {
        LOG.trace("Extracting name {} from Document.value", valueFromAnnotation);
        return valueFromAnnotation;
      }
    }
    LOG.trace("Extracting document name from parameter name");
    return param.getParameterName();
  }

  private static boolean getVariableOptional(final ParameterInfo param) {
    if (param.getParameterInfo().isAnnotationPresent(Variable.class)) {
      final boolean optional = param.getParameterInfo().getAnnotation(Variable.class).optional();
      LOG.trace("Extracting optional flag from Variable");
      return optional;
    }
    LOG.trace("No variable annotation found, defaulting to true");
    return true;
  }

  private static boolean getDocumentOptional(final ParameterInfo param) {
    if (param.getParameterInfo().isAnnotationPresent(Document.class)) {
      final boolean optional = param.getParameterInfo().getAnnotation(Document.class).optional();
      LOG.trace("Extracting optional flag from Document");
      return optional;
    }
    LOG.trace("No document annotation found, defaulting to true");
    return true;
  }

  private static String getVariableNameLegacy(final ParameterInfo param) {
    if (param
        .getParameterInfo()
        .isAnnotationPresent(io.camunda.zeebe.spring.client.annotation.Variable.class)) {
      final String nameFromAnnotation =
          param
              .getParameterInfo()
              .getAnnotation(io.camunda.zeebe.spring.client.annotation.Variable.class)
              .name();
      if (!Objects.equals(
          nameFromAnnotation, io.camunda.zeebe.spring.client.annotation.Variable.DEFAULT_NAME)) {
        LOG.trace("Extracting name {} from Variable.name", nameFromAnnotation);
        return nameFromAnnotation;
      }
      final String valueFromAnnotation =
          param
              .getParameterInfo()
              .getAnnotation(io.camunda.zeebe.spring.client.annotation.Variable.class)
              .value();
      if (!Objects.equals(
          valueFromAnnotation, io.camunda.zeebe.spring.client.annotation.Variable.DEFAULT_NAME)) {
        LOG.trace("Extracting name {} from Variable.value", valueFromAnnotation);
        return valueFromAnnotation;
      }
    }
    LOG.trace("Extracting variable name from parameter name");
    return param.getParameterName();
  }
}
