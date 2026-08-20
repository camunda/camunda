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

import static io.camunda.client.annotation.AnnotationUtil.isClusterVariables;
import static org.springframework.util.ReflectionUtils.doWithMethods;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.AnnotationUtil;
import io.camunda.client.annotation.value.ClusterVariablesValue;
import io.camunda.client.annotation.value.MethodClusterVariablesValue;
import io.camunda.client.annotation.value.ResourceClusterVariablesValue;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.GloballyScopedClusterVariableCreationCommandStep1;
import io.camunda.client.api.command.GloballyScopedClusterVariableUpdateCommandStep1;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.command.TenantScopedClusterVariableCreationCommandStep1;
import io.camunda.client.api.command.TenantScopedClusterVariableUpdateCommandStep1;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.spring.properties.CamundaClientClusterVariablesProperties;
import io.camunda.client.spring.properties.ClusterVariableEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ReflectionUtils;

public class ClusterVariablesAnnotationProcessor extends AbstractCamundaAnnotationProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ClusterVariablesAnnotationProcessor.class);

  private final List<ClusterVariablesValue> clusterVariablesValues = new ArrayList<>();
  private final JsonMapper jsonMapper;
  private final ResourcePatternResolver resourcePatternResolver;
  private final CamundaClientClusterVariablesProperties properties;

  public ClusterVariablesAnnotationProcessor(
      final JsonMapper jsonMapper,
      final ResourcePatternResolver resourcePatternResolver,
      final CamundaClientClusterVariablesProperties properties) {
    this.jsonMapper = jsonMapper;
    this.resourcePatternResolver = resourcePatternResolver;
    this.properties = properties;
  }

  @Override
  public boolean isApplicableFor(final BeanInfo beanInfo) {
    return isClusterVariables(beanInfo);
  }

  @Override
  public void configureFor(final BeanInfo beanInfo) {
    final List<? extends ClusterVariablesValue> classValues =
        AnnotationUtil.getClusterVariablesValuesFromClass(beanInfo);
    if (!classValues.isEmpty()) {
      LOGGER.debug("Configuring cluster variables from class annotations: {}", classValues);
      clusterVariablesValues.addAll(classValues);
    }

    final List<ClusterVariablesValue> methodValues = new ArrayList<>();
    doWithMethods(
        beanInfo.getTargetClass(),
        method ->
            methodValues.addAll(
                AnnotationUtil.getClusterVariablesValuesFromMethods(beanInfo.toMethodInfo(method))),
        ReflectionUtils.USER_DECLARED_METHODS);

    if (!methodValues.isEmpty()) {
      LOGGER.debug("Configuring cluster variables from method annotations: {}", methodValues);
      clusterVariablesValues.addAll(methodValues);
    }
  }

  @Override
  protected void clearDiscovered() {
    clusterVariablesValues.clear();
  }

  @Override
  public void start(final CamundaClient client) {
    // Process variables from properties
    final List<ClusterVariableEntry> propertyVariables = properties.resolveVariables();
    if (!propertyVariables.isEmpty()) {
      LOGGER.debug("Upserting {} cluster variable(s) from properties", propertyVariables.size());
      upsertClusterVariables(client, propertyVariables);
    }

    // Process variables from annotations
    for (final ClusterVariablesValue value : clusterVariablesValues) {
      final List<ClusterVariableEntry> variables;
      if (value instanceof ResourceClusterVariablesValue resourceValue) {
        variables = loadVariablesFromResources(resourceValue.getResources());
      } else if (value instanceof MethodClusterVariablesValue methodValue) {
        variables = loadVariablesFromSupplier(methodValue.getVariableSupplier());
      } else {
        continue;
      }
      variables.forEach(
          entry -> {
            if (entry.getTenantId() != null) {
              throw new IllegalArgumentException(
                  "Cluster variable '"
                      + entry.getName()
                      + "' must not define a tenantId; use @ClusterVariables(tenantId = ...) instead");
            }
            entry.setTenantId(value.getTenantId());
          });
      upsertClusterVariables(client, variables);
    }
  }

  @Override
  public void stop(final CamundaClient client) {
    clusterVariablesValues.clear();
  }

  private List<ClusterVariableEntry> loadVariablesFromResources(
      final List<String> resourcePatterns) {
    final Map<String, ClusterVariableEntry> variables = new LinkedHashMap<>();
    final List<Resource> allResources =
        resourcePatterns.stream()
            .flatMap(pattern -> Arrays.stream(getResources(pattern)))
            .distinct()
            .toList();
    if (allResources.isEmpty()) {
      throw new IllegalArgumentException(
          "No resources found for cluster variables patterns: " + resourcePatterns);
    }
    for (final Resource resource : allResources) {
      try (final InputStream inputStream = resource.getInputStream()) {
        final String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        final List<ClusterVariableEntry> loaded = parseVariables(json);
        LOGGER.debug(
            "Loaded {} variable(s) from resource '{}'", loaded.size(), resource.getFilename());
        loaded.forEach(entry -> variables.put(entry.getName(), entry));
      } catch (final Exception e) {
        throw new RuntimeException(
            "Error reading cluster variables from resource: " + resource.getFilename(), e);
      }
    }
    return new ArrayList<>(variables.values());
  }

  private List<ClusterVariableEntry> loadVariablesFromSupplier(
      final Supplier<Object> variableSupplier) {
    final Object result = variableSupplier.get();
    if (result == null) {
      throw new IllegalStateException("@ClusterVariables method must not return null");
    }
    return parseVariables(jsonMapper.toJson(result));
  }

  /**
   * Parses either a JSON array of {@link ClusterVariableEntry} objects or a flat JSON object where
   * each key becomes a variable name and the corresponding value becomes the variable value.
   */
  private List<ClusterVariableEntry> parseVariables(final String json) {
    final JsonNode root = jsonMapper.fromJson(json, JsonNode.class);
    if (root.isArray()) {
      return new ArrayList<>(
          Arrays.asList(jsonMapper.transform(root, ClusterVariableEntry[].class)));
    }
    final List<ClusterVariableEntry> variables = new ArrayList<>();
    jsonMapper
        .fromJsonAsMap(json)
        .forEach(
            (name, value) -> {
              final ClusterVariableEntry entry = new ClusterVariableEntry();
              entry.setName(name);
              entry.setValue(value);
              variables.add(entry);
            });
    return variables;
  }

  private void upsertClusterVariables(
      final CamundaClient client, final List<ClusterVariableEntry> variables) {
    for (final ClusterVariableEntry variable : variables) {
      if (variable.getName() == null || variable.getName().isBlank()) {
        throw new IllegalArgumentException("Cluster variable name must not be null or blank");
      }
      try {
        createClusterVariable(client, variable);
      } catch (final ProblemException e) {
        if (e.code() == 409) {
          updateClusterVariable(client, variable);
        } else {
          throw e;
        }
      }
    }
  }

  private void createClusterVariable(
      final CamundaClient client, final ClusterVariableEntry variable) {
    final String name = variable.getName();
    final String tenantId = tenantIdOf(variable);
    if (tenantId != null) {
      TenantScopedClusterVariableCreationCommandStep1 command =
          client
              .newTenantScopedClusterVariableCreateRequest(tenantId)
              .create(name, variable.getValue());
      if (hasMetadata(variable)) {
        command = command.metadata(variable.getMetadata());
      }
      if (variable.getKind() != null) {
        command = command.kind(variable.getKind());
      }
      command.execute();
      LOGGER.debug("Created tenant-scoped cluster variable '{}' for tenant '{}'", name, tenantId);
    } else {
      GloballyScopedClusterVariableCreationCommandStep1 command =
          client.newGloballyScopedClusterVariableCreateRequest().create(name, variable.getValue());
      if (hasMetadata(variable)) {
        command = command.metadata(variable.getMetadata());
      }
      if (variable.getKind() != null) {
        command = command.kind(variable.getKind());
      }
      command.execute();
      LOGGER.debug("Created globally-scoped cluster variable '{}'", name);
    }
  }

  private void updateClusterVariable(
      final CamundaClient client, final ClusterVariableEntry variable) {
    final String name = variable.getName();
    final String tenantId = tenantIdOf(variable);
    if (variable.getKind() != null) {
      LOGGER.warn(
          "Ignoring kind '{}' for cluster variable '{}': the kind of an existing cluster variable cannot be changed",
          variable.getKind(),
          name);
    }
    if (tenantId != null) {
      TenantScopedClusterVariableUpdateCommandStep1 command =
          client
              .newTenantScopedClusterVariableUpdateRequest(tenantId)
              .update(name, variable.getValue());
      if (hasMetadata(variable)) {
        command = command.metadata(variable.getMetadata());
      }
      command.execute();
      LOGGER.debug("Updated tenant-scoped cluster variable '{}' for tenant '{}'", name, tenantId);
    } else {
      GloballyScopedClusterVariableUpdateCommandStep1 command =
          client.newGloballyScopedClusterVariableUpdateRequest().update(name, variable.getValue());
      if (hasMetadata(variable)) {
        command = command.metadata(variable.getMetadata());
      }
      command.execute();
      LOGGER.debug("Updated globally-scoped cluster variable '{}'", name);
    }
  }

  /** A blank tenant ID means the variable is globally scoped. */
  private static String tenantIdOf(final ClusterVariableEntry variable) {
    final String tenantId = variable.getTenantId();
    return tenantId == null || tenantId.isBlank() ? null : tenantId;
  }

  private static boolean hasMetadata(final ClusterVariableEntry variable) {
    return variable.getMetadata() != null && !variable.getMetadata().isEmpty();
  }

  private Resource[] getResources(final String resourcePattern) {
    try {
      return resourcePatternResolver.getResources(resourcePattern);
    } catch (final IOException e) {
      throw new RuntimeException(
          "Error resolving cluster variables resources for pattern: " + resourcePattern, e);
    }
  }
}
