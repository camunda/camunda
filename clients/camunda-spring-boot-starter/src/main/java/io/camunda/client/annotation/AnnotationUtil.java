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
package io.camunda.client.annotation;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.annotation.Deployment.Deployments;
import io.camunda.client.annotation.value.DeploymentValue;
import io.camunda.client.annotation.value.DocumentValue;
import io.camunda.client.annotation.value.DocumentValue.ParameterType;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.*;
import io.camunda.client.annotation.value.VariableValue;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.response.UserTaskProperties;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.bean.ParameterInfo;
import io.camunda.client.jobhandling.DocumentContext;
import io.camunda.client.jobhandling.parameter.KeyTargetType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

public class AnnotationUtil {
  private static final Logger LOG = LoggerFactory.getLogger(AnnotationUtil.class);
  private static final Map<String, ParameterType> DOCUMENT_PARAMETER_TYPES =
      Map.of(
          List.class.getName() + "<" + DocumentReferenceResponse.class.getName() + ">",
          ParameterType.LIST,
          DocumentReferenceResponse.class.getName(),
          ParameterType.SINGLE,
          DocumentContext.class.getName(),
          ParameterType.CONTEXT);
  private static final Map<Class<? extends Annotation>, Function<ActivatedJob, Long>>
      KEY_ANNOTATIONS =
          Map.of(
              ProcessInstanceKey.class,
              ActivatedJob::getProcessInstanceKey,
              ElementInstanceKey.class,
              ActivatedJob::getElementInstanceKey,
              JobKey.class,
              ActivatedJob::getKey,
              ProcessDefinitionKey.class,
              ActivatedJob::getProcessDefinitionKey);

  public static boolean isVariable(final ParameterInfo parameterInfo) {
    return parameterInfo.getParameter().isAnnotationPresent(Variable.class)
        || isVariableLegacy(parameterInfo);
  }

  public static boolean isDocument(final ParameterInfo parameterInfo) {
    return parameterInfo.getParameter().isAnnotationPresent(Document.class)
        && (DOCUMENT_PARAMETER_TYPES.containsKey(
            parameterInfo.getParameter().getParameterizedType().getTypeName()));
  }

  public static boolean isKey(final ParameterInfo parameterInfo) {
    return KEY_ANNOTATIONS.keySet().stream()
            .anyMatch(parameterInfo.getParameter()::isAnnotationPresent)
        && KeyTargetType.isValidParameterType(parameterInfo.getParameter().getType());
  }

  public static boolean isUserTaskProperties(final ParameterInfo parameterInfo) {
    return parameterInfo.getParameter().getType().isAssignableFrom(UserTaskProperties.class);
  }

  public static Optional<Function<ActivatedJob, Long>> getKeyResolver(
      final ParameterInfo parameterInfo) {
    if (isKey(parameterInfo)) {
      return Optional.of(
          KEY_ANNOTATIONS.get(
              KEY_ANNOTATIONS.keySet().stream()
                  .filter(parameterInfo.getParameter()::isAnnotationPresent)
                  .findFirst()
                  .get()));
    }
    return Optional.empty();
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
    return parameterInfo.getParameter().isAnnotationPresent(VariablesAsType.class)
        || isVariablesAsTypeLegacy(parameterInfo);
  }

  public static boolean isCustomHeaders(final ParameterInfo parameterInfo) {
    return parameterInfo.getParameter().isAnnotationPresent(CustomHeaders.class)
        || isCustomHeadersLegacy(parameterInfo);
  }

  public static boolean isDeployment(final BeanInfo beanInfo) {
    return beanInfo.hasClassAnnotation(Deployments.class)
        || beanInfo.hasClassAnnotation(Deployment.class)
        || isDeploymentLegacy(beanInfo);
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
      final SourceAware<String> name =
          "".equals(annotation.name())
              ? new GeneratedFromMethodInfo<>(
                  methodInfo.getBeanInfo().getBeanName() + "#" + methodInfo.getMethodName())
              : new FromAnnotation<>(annotation.name());
      final SourceAware<String> type =
          "".equals(annotation.type())
              ? new GeneratedFromMethodInfo<>(methodInfo.getMethodName())
              : new FromAnnotation<>(annotation.type());
      final List<SourceAware<String>> fetchVariables = new ArrayList<>();
      fetchVariables.addAll(
          Arrays.stream(annotation.fetchVariables()).map(FromAnnotation::new).toList());
      fetchVariables.addAll(
          usedVariableNames(methodInfo).stream().map(GeneratedFromMethodInfo::new).toList());
      return Optional.of(
              new JobWorkerValue(
                  type,
                  name,
                  annotation.timeout() == -1
                      ? new Empty<>()
                      : new FromAnnotation<>(Duration.of(annotation.timeout(), ChronoUnit.MILLIS)),
                  annotation.maxJobsActive() == -1
                      ? new Empty<>()
                      : new FromAnnotation<>(annotation.maxJobsActive()),
                  annotation.requestTimeout() == -1
                      ? new Empty<>()
                      : new FromAnnotation<>(
                          Duration.of(annotation.requestTimeout(), ChronoUnit.SECONDS)),
                  annotation.pollInterval() == -1
                      ? new Empty<>()
                      : new FromAnnotation<>(
                          Duration.of(annotation.pollInterval(), ChronoUnit.MILLIS)),
                  fromSingletonBooleanArray(
                      annotation.autoComplete(), "autoComplete", methodInfo.getMethodName()),
                  fetchVariables,
                  fromSingletonBooleanArray(
                      annotation.enabled(), "enabled", methodInfo.getMethodName()),
                  Arrays.stream(annotation.tenantIds())
                      .map(tenantId -> (SourceAware<String>) new FromAnnotation<>(tenantId))
                      .toList(),
                  fromSingletonBooleanArray(
                      annotation.fetchAllVariables(),
                      () ->
                          usesActivatedJob(methodInfo)
                              ? new GeneratedFromMethodInfo<>(true)
                              : new Empty<>(),
                      "fetchAllVariables",
                      methodInfo.getMethodName()),
                  fromSingletonBooleanArray(
                      annotation.streamEnabled(), "streamEnabled", methodInfo.getMethodName()),
                  annotation.streamTimeout() == -1
                      ? new Empty<>()
                      : new FromAnnotation<>(
                          Duration.of(annotation.streamTimeout(), ChronoUnit.MILLIS)),
                  annotation.maxRetries() == -1
                      ? new Empty<>()
                      : new FromAnnotation<>(annotation.maxRetries()),
                  annotation.retryBackoff() == -1
                      ? new Empty<>()
                      : new FromAnnotation<>(Duration.ofMillis(annotation.retryBackoff())),
                  fromSingletonArray(
                      annotation.TenantFilter(), "tenantFilterName", methodInfo.getMethodName())))
          .map(
              v -> {
                v.setMethodInfo(methodInfo);
                return v;
              });
    }
    return Optional.empty();
  }

  private static <T> SourceAware<T> fromSingletonArray(
      final T[] annotationProperty,
      final Supplier<SourceAware<T>> defaultValue,
      final String propertyName,
      final String methodName) {
    if (annotationProperty.length == 0) {
      return defaultValue.get();
    }
    if (annotationProperty.length == 1) {
      return new FromAnnotation<>(annotationProperty[0]);
    }
    throw new IllegalArgumentException(
        String.format(
            "Illegal configuration of property '%s' on @JobWorker method '%s'",
            propertyName, methodName));
  }

  private static <T> SourceAware<T> fromSingletonArray(
      final T[] annotationProperty, final String propertyName, final String methodName) {
    return fromSingletonArray(annotationProperty, Empty::new, propertyName, methodName);
  }

  private static SourceAware<Boolean> fromSingletonBooleanArray(
      final boolean[] annotationProperty,
      final Supplier<SourceAware<Boolean>> defaultValue,
      final String propertyName,
      final String methodName) {
    if (annotationProperty.length == 0) {
      return defaultValue.get();
    }
    if (annotationProperty.length == 1) {
      return new FromAnnotation<>(annotationProperty[0]);
    }
    throw new IllegalArgumentException(
        String.format(
            "Illegal configuration of boolean property '%s' on @JobWorker method '%s'",
            propertyName, methodName));
  }

  private static SourceAware<Boolean> fromSingletonBooleanArray(
      final boolean[] annotationProperty, final String propertyName, final String methodName) {
    return fromSingletonBooleanArray(annotationProperty, Empty::new, propertyName, methodName);
  }

  private static boolean usesActivatedJob(final MethodInfo methodInfo) {
    return methodInfo.getParameters().stream()
        .anyMatch(
            p ->
                p.getParameter().getType().isAssignableFrom(ActivatedJob.class)
                    || p.getParameter()
                        .getType()
                        .isAssignableFrom(io.camunda.zeebe.client.api.response.ActivatedJob.class));
  }

  public static List<String> usedVariableNames(final MethodInfo methodInfo) {
    final List<String> result = new ArrayList<>();
    final List<ParameterInfo> parameters = getVariablesAsTypeParameters(methodInfo);
    parameters.forEach(
        pi ->
            ReflectionUtils.doWithFields(
                pi.getParameter().getType(), f -> result.add(extractFieldName(f))));
    result.addAll(
        getVariableParameters(methodInfo).stream()
            .map(AnnotationUtil::getVariableValue)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(VariableValue::getName)
            .toList());
    return result;
  }

  private static String extractFieldName(final Field field) {
    if (field.isAnnotationPresent(JsonProperty.class)) {
      final String value = field.getAnnotation(JsonProperty.class).value();
      if (StringUtils.isNotBlank(value)) {
        return value;
      }
    }
    return field.getName();
  }

  private static Optional<JobWorkerValue> getJobWorkerValueLegacyInternal(
      final MethodInfo methodInfo) {
    final Optional<io.camunda.zeebe.spring.client.annotation.JobWorker> methodAnnotation =
        methodInfo.getAnnotation(io.camunda.zeebe.spring.client.annotation.JobWorker.class);
    if (methodAnnotation.isPresent()) {
      final io.camunda.zeebe.spring.client.annotation.JobWorker annotation = methodAnnotation.get();
      final SourceAware<String> name =
          "".equals(annotation.name())
              ? new GeneratedFromMethodInfo<>(
                  methodInfo.getBeanInfo().getBeanName() + "#" + methodInfo.getMethodName())
              : new FromAnnotation<>(annotation.name());
      final SourceAware<String> type =
          "".equals(annotation.type())
              ? new GeneratedFromMethodInfo<>(methodInfo.getMethodName())
              : new FromAnnotation<>(annotation.type());
      final List<SourceAware<String>> fetchVariables = new ArrayList<>();
      fetchVariables.addAll(
          Arrays.stream(annotation.fetchVariables()).map(FromAnnotation::new).toList());
      fetchVariables.addAll(
          usedVariableNames(methodInfo).stream().map(GeneratedFromMethodInfo::new).toList());
      return Optional.of(
              new JobWorkerValue(
                  type,
                  name,
                  annotation.timeout() == -1L
                      ? new Empty<>()
                      : new FromAnnotation<>(Duration.of(annotation.timeout(), ChronoUnit.MILLIS)),
                  annotation.maxJobsActive() == -1
                      ? new Empty<>()
                      : new FromAnnotation<>(annotation.maxJobsActive()),
                  annotation.requestTimeout() == -1L
                      ? new Empty<>()
                      : new FromAnnotation<>(
                          Duration.of(annotation.requestTimeout(), ChronoUnit.SECONDS)),
                  annotation.pollInterval() == -1L
                      ? new Empty<>()
                      : new FromAnnotation<>(
                          Duration.of(annotation.pollInterval(), ChronoUnit.MILLIS)),
                  annotation.autoComplete() ? new Empty<>() : new FromAnnotation<>(false),
                  fetchVariables,
                  annotation.enabled() ? new Empty<>() : new FromAnnotation<>(false),
                  Arrays.stream(annotation.tenantIds())
                      .map(tenantId -> (SourceAware<String>) new FromAnnotation<>(tenantId))
                      .toList(),
                  annotation.fetchAllVariables()
                      ? new FromAnnotation<>(true)
                      : usesActivatedJob(methodInfo)
                          ? new GeneratedFromMethodInfo<>(true)
                          : new Empty<>(),
                  annotation.streamEnabled() ? new FromAnnotation<>(true) : new Empty<>(),
                  annotation.streamTimeout() == -1L
                      ? new Empty<>()
                      : new FromAnnotation<>(
                          Duration.of(annotation.streamTimeout(), ChronoUnit.MILLIS)),
                  annotation.maxRetries() == -1
                      ? new Empty<>()
                      : new FromAnnotation<>(annotation.maxRetries()),
                  new Empty<>(),
                  new Empty<>()))
          .map(
              v -> {
                v.setMethodInfo(methodInfo);
                return v;
              });
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
                parameterInfo.getParameter().getParameterizedType().getTypeName()))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported parameter type for document: "
                        + parameterInfo.getParameter().getParameterizedType().getTypeName()));
  }

  public static List<DeploymentValue> getDeploymentValues(final BeanInfo beanInfo) {
    if (isDeployment(beanInfo)) {
      final List<DeploymentValue> values = new ArrayList<>();
      values.addAll(getDeploymentValuesFromRepeatable(beanInfo));
      values.addAll(getDeploymentValuesInternal(beanInfo));
      values.addAll(getDeploymentResourcesLegacy(beanInfo));
      return values;
    }
    return Collections.emptyList();
  }

  private static List<DeploymentValue> getDeploymentValuesFromRepeatable(final BeanInfo beanInfo) {
    return beanInfo.getAnnotation(Deployments.class).stream()
        .flatMap(d -> Arrays.stream(d.value()))
        .map(AnnotationUtil::fromAnnotation)
        .toList();
  }

  private static List<DeploymentValue> getDeploymentValuesInternal(final BeanInfo beanInfo) {
    return beanInfo
        .getAnnotation(Deployment.class)
        .map(AnnotationUtil::fromAnnotation)
        .map(Arrays::asList)
        .orElseGet(List::of);
  }

  private static DeploymentValue fromAnnotation(final Deployment deploymentAnnotation) {
    return new DeploymentValue(
        Arrays.asList(deploymentAnnotation.resources()),
        StringUtils.isEmpty(deploymentAnnotation.tenantId())
            ? null
            : deploymentAnnotation.tenantId());
  }

  private static List<DeploymentValue> getDeploymentResourcesLegacy(final BeanInfo beanInfo) {
    return beanInfo
        .getAnnotation(io.camunda.zeebe.spring.client.annotation.Deployment.class)
        .map(AnnotationUtil::fromAnnotation)
        .map(Arrays::asList)
        .orElseGet(List::of);
  }

  private static DeploymentValue fromAnnotation(
      final io.camunda.zeebe.spring.client.annotation.Deployment deploymentAnnotation) {
    return new DeploymentValue(
        Arrays.asList(deploymentAnnotation.resources()),
        StringUtils.isEmpty(deploymentAnnotation.tenantId())
            ? null
            : deploymentAnnotation.tenantId());
  }

  private static boolean isVariableLegacy(final ParameterInfo parameterInfo) {
    return parameterInfo
        .getParameter()
        .isAnnotationPresent(io.camunda.zeebe.spring.client.annotation.Variable.class);
  }

  private static boolean isVariablesAsTypeLegacy(final ParameterInfo parameterInfo) {
    return parameterInfo
        .getParameter()
        .isAnnotationPresent(io.camunda.zeebe.spring.client.annotation.VariablesAsType.class);
  }

  private static boolean isCustomHeadersLegacy(final ParameterInfo parameterInfo) {
    return parameterInfo
        .getParameter()
        .isAnnotationPresent(io.camunda.zeebe.spring.client.annotation.CustomHeaders.class);
  }

  public static boolean isDeploymentLegacy(final BeanInfo beanInfo) {
    return beanInfo.hasClassAnnotation(io.camunda.zeebe.spring.client.annotation.Deployment.class);
  }

  private static String getVariableName(final ParameterInfo param) {
    if (param.getParameter().isAnnotationPresent(Variable.class)) {
      final String nameFromAnnotation = param.getParameter().getAnnotation(Variable.class).name();
      if (StringUtils.isNotBlank(nameFromAnnotation)) {
        LOG.trace("Extracting name {} from Variable.name", nameFromAnnotation);
        return nameFromAnnotation;
      }
      final String valueFromAnnotation = param.getParameter().getAnnotation(Variable.class).value();
      if (StringUtils.isNotBlank(valueFromAnnotation)) {
        LOG.trace("Extracting name {} from Variable.value", valueFromAnnotation);
        return valueFromAnnotation;
      }
    }
    LOG.trace("Extracting variable name from parameter name");
    return param.getParameterName();
  }

  private static String getDocumentName(final ParameterInfo param) {
    if (param.getParameter().isAnnotationPresent(Document.class)) {
      final String nameFromAnnotation = param.getParameter().getAnnotation(Document.class).name();
      if (StringUtils.isNotBlank(nameFromAnnotation)) {
        LOG.trace("Extracting name {} from Document.name", nameFromAnnotation);
        return nameFromAnnotation;
      }
      final String valueFromAnnotation = param.getParameter().getAnnotation(Document.class).value();
      if (StringUtils.isNotBlank(valueFromAnnotation)) {
        LOG.trace("Extracting name {} from Document.value", valueFromAnnotation);
        return valueFromAnnotation;
      }
    }
    LOG.trace("Extracting document name from parameter name");
    return param.getParameterName();
  }

  private static boolean getVariableOptional(final ParameterInfo param) {
    if (param.getParameter().isAnnotationPresent(Variable.class)) {
      final boolean optional = param.getParameter().getAnnotation(Variable.class).optional();
      LOG.trace("Extracting optional flag from Variable");
      return optional;
    }
    LOG.trace("No variable annotation found, defaulting to true");
    return true;
  }

  private static boolean getDocumentOptional(final ParameterInfo param) {
    if (param.getParameter().isAnnotationPresent(Document.class)) {
      final boolean optional = param.getParameter().getAnnotation(Document.class).optional();
      LOG.trace("Extracting optional flag from Document");
      return optional;
    }
    LOG.trace("No document annotation found, defaulting to true");
    return true;
  }

  private static String getVariableNameLegacy(final ParameterInfo param) {
    if (param
        .getParameter()
        .isAnnotationPresent(io.camunda.zeebe.spring.client.annotation.Variable.class)) {
      final String nameFromAnnotation =
          param
              .getParameter()
              .getAnnotation(io.camunda.zeebe.spring.client.annotation.Variable.class)
              .name();
      if (!Objects.equals(
          nameFromAnnotation, io.camunda.zeebe.spring.client.annotation.Variable.DEFAULT_NAME)) {
        LOG.trace("Extracting name {} from Variable.name", nameFromAnnotation);
        return nameFromAnnotation;
      }
      final String valueFromAnnotation =
          param
              .getParameter()
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
