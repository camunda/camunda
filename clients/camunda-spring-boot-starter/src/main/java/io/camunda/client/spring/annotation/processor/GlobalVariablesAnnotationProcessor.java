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
package io.camunda.client.spring.annotation.processor;

import static io.camunda.client.annotation.AnnotationUtil.isGlobalVariables;
import static org.springframework.util.ReflectionUtils.doWithMethods;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.AnnotationUtil;
import io.camunda.client.annotation.value.GlobalVariablesValue;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.spring.properties.CamundaClientGlobalVariablesProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ReflectionUtils;

public class GlobalVariablesAnnotationProcessor extends AbstractCamundaAnnotationProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GlobalVariablesAnnotationProcessor.class);

  private final List<GlobalVariablesValue> globalVariablesValues = new ArrayList<>();
  private final JsonMapper jsonMapper;
  private final ResourcePatternResolver resourcePatternResolver;
  private final CamundaClientGlobalVariablesProperties properties;

  public GlobalVariablesAnnotationProcessor(
      final JsonMapper jsonMapper,
      final ResourcePatternResolver resourcePatternResolver,
      final CamundaClientGlobalVariablesProperties properties) {
    this.jsonMapper = jsonMapper;
    this.resourcePatternResolver = resourcePatternResolver;
    this.properties = properties;
  }

  public GlobalVariablesAnnotationProcessor(
      final JsonMapper jsonMapper, final CamundaClientGlobalVariablesProperties properties) {
    this(jsonMapper, new PathMatchingResourcePatternResolver(), properties);
  }

  @Override
  public boolean isApplicableFor(final BeanInfo beanInfo) {
    return isGlobalVariables(beanInfo);
  }

  @Override
  public void configureFor(final BeanInfo beanInfo) {
    final List<GlobalVariablesValue> classValues =
        AnnotationUtil.getGlobalVariablesValuesFromClass(beanInfo);
    if (!classValues.isEmpty()) {
      LOGGER.debug("Configuring global variables from class annotations: {}", classValues);
      globalVariablesValues.addAll(classValues);
    }

    final List<GlobalVariablesValue> methodValues = new ArrayList<>();
    doWithMethods(
        beanInfo.getTargetClass(),
        method ->
            methodValues.addAll(
                AnnotationUtil.getGlobalVariablesValuesFromMethods(beanInfo.toMethodInfo(method))),
        ReflectionUtils.USER_DECLARED_METHODS);

    if (!methodValues.isEmpty()) {
      LOGGER.debug("Configuring global variables from method annotations: {}", methodValues);
      globalVariablesValues.addAll(methodValues);
    }
  }

  @Override
  public void start(final CamundaClient client) {
    // Process variables from properties
    final Map<String, Object> propertyVariables = properties.getVariables();
    if (propertyVariables != null && !propertyVariables.isEmpty()) {
      LOGGER.debug("Creating {} global variable(s) from properties", propertyVariables.size());
      createClusterVariables(client, propertyVariables, null);
    }

    // Process variables from annotations
    for (final GlobalVariablesValue value : globalVariablesValues) {
      final Map<String, Object> variables;
      if (value.isResourceBased()) {
        variables = loadVariablesFromResources(value.getResources());
      } else {
        variables = loadVariablesFromMethod(value.getMethodInfo());
      }
      createClusterVariables(client, variables, value.getTenantId());
    }
  }

  @Override
  public void stop(final CamundaClient client) {
    globalVariablesValues.clear();
  }

  private Map<String, Object> loadVariablesFromResources(final List<String> resourcePatterns) {
    final Map<String, Object> variables = new LinkedHashMap<>();
    final List<Resource> allResources =
        resourcePatterns.stream()
            .flatMap(pattern -> Arrays.stream(getResources(pattern)))
            .distinct()
            .toList();
    if (allResources.isEmpty()) {
      throw new IllegalArgumentException(
          "No resources found for global variables patterns: " + resourcePatterns);
    }
    for (final Resource resource : allResources) {
      try (final InputStream inputStream = resource.getInputStream()) {
        final String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        final Map<String, Object> loaded = jsonMapper.fromJsonAsMap(json);
        LOGGER.debug(
            "Loaded {} variable(s) from resource '{}'", loaded.size(), resource.getFilename());
        variables.putAll(loaded);
      } catch (final Exception e) {
        throw new RuntimeException(
            "Error reading global variables from resource: " + resource.getFilename(), e);
      }
    }
    return variables;
  }

  private Map<String, Object> loadVariablesFromMethod(final MethodInfo methodInfo) {
    try {
      final Object result = methodInfo.invoke();
      if (result == null) {
        throw new IllegalStateException(
            String.format(
                "@GlobalVariables method '%s' must not return null", methodInfo.getMethodName()));
      }
      return jsonMapper.fromJsonAsMap(jsonMapper.toJson(result));
    } catch (final IllegalStateException e) {
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException(
          "Error invoking @GlobalVariables method: " + methodInfo.getMethodName(), e);
    }
  }

  private void createClusterVariables(
      final CamundaClient client, final Map<String, Object> variables, final String tenantId) {
    for (final Map.Entry<String, Object> entry : variables.entrySet()) {
      final String name = entry.getKey();
      final Object value = entry.getValue();
      if (tenantId != null) {
        client.newTenantScopedClusterVariableCreateRequest(tenantId).create(name, value).execute();
        LOGGER.info("Created tenant-scoped cluster variable '{}' for tenant '{}'", name, tenantId);
      } else {
        client.newGloballyScopedClusterVariableCreateRequest().create(name, value).execute();
        LOGGER.info("Created globally-scoped cluster variable '{}'", name);
      }
    }
  }

  private Resource[] getResources(final String resourcePattern) {
    try {
      return resourcePatternResolver.getResources(resourcePattern);
    } catch (final IOException e) {
      return new Resource[0];
    }
  }
}
