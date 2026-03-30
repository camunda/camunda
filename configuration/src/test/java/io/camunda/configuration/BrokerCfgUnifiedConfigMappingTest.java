/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * This test ensures that all properties in {@link BrokerCfg} (and its nested configuration classes)
 * have corresponding mappings from the unified configuration system in {@link
 * io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride}.
 *
 * <p>If a developer adds a new property to BrokerCfg without adding a corresponding mapping from
 * unified configuration, this test will fail with a list of unmapped properties.
 */
class BrokerCfgUnifiedConfigMappingTest {

  private static final Path BROKER_BASED_PROPERTIES_OVERRIDE_PATH =
      Paths.get(
          "src/main/java/io/camunda/configuration/beanoverrides/BrokerBasedPropertiesOverride.java");

  /**
   * Primitive and simple types that are considered leaf properties (not configuration objects to
   * recurse into).
   */
  private static final Set<Class<?>> LEAF_TYPES =
      Set.of(
          // Primitives and their wrappers
          boolean.class,
          Boolean.class,
          byte.class,
          Byte.class,
          short.class,
          Short.class,
          int.class,
          Integer.class,
          long.class,
          Long.class,
          float.class,
          Float.class,
          double.class,
          Double.class,
          char.class,
          Character.class,
          // Common types
          String.class,
          Duration.class,
          DataSize.class,
          // Enum base class check is done separately
          Object.class);

  private static final String LEGACY_PROPERTY_PREFIX = "zeebe.broker.";

  @Test
  void shouldHaveUnifiedConfigMappingForAllBrokerCfgProperties() throws IOException {
    // given
    final Set<String> allBrokerCfgProperties = extractPropertiesFromBrokerCfg();
    final Set<String> mappedProperties = extractMappedPropertiesFromOverride();

    // when
    final Set<String> unmappedProperties = new HashSet<>(allBrokerCfgProperties);
    unmappedProperties.removeAll(mappedProperties);

    // Sort properties by section for easier reading and add prefix for clarity
    final List<String> sortedUnmappedProperties =
        unmappedProperties.stream()
            .sorted(this::compareBySection)
            .map(prop -> LEGACY_PROPERTY_PREFIX + prop)
            .toList();

    // then
    assertThat(sortedUnmappedProperties)
        .describedAs(
            """
            The following BrokerCfg properties do not have a corresponding mapping \
            from unified configuration in BrokerBasedPropertiesOverride.

            To fix: Add mappings in BrokerBasedPropertiesOverride for these properties \
            and corresponding unified configuration properties in the camunda.* namespace.

            Unmapped properties (sorted by section)""")
        .isEmpty();
  }

  /**
   * Compares two property paths by their section (top-level path segment), then alphabetically
   * within each section.
   */
  private int compareBySection(final String path1, final String path2) {
    final String section1 = getTopLevelSection(path1);
    final String section2 = getTopLevelSection(path2);

    final int sectionCompare = section1.compareTo(section2);
    if (sectionCompare != 0) {
      return sectionCompare;
    }
    // Within the same section, sort alphabetically
    return path1.compareTo(path2);
  }

  /** Extracts the top-level section from a property path. */
  private String getTopLevelSection(final String path) {
    final int dotIndex = path.indexOf('.');
    return dotIndex > 0 ? path.substring(0, dotIndex) : path;
  }

  /**
   * Extracts all property paths from {@link BrokerCfg} and its nested configuration classes using
   * reflection.
   *
   * @return Set of property paths like "network.host", "cluster.nodeId", etc.
   */
  private Set<String> extractPropertiesFromBrokerCfg() {
    final Set<String> properties = new HashSet<>();
    extractPropertiesRecursively(BrokerCfg.class, "", properties, new HashSet<>());
    return properties;
  }

  /**
   * Recursively extracts property paths from a configuration class.
   *
   * @param clazz The configuration class to analyze
   * @param prefix The current property path prefix
   * @param properties The set to collect property paths
   * @param visited Classes already visited (to avoid infinite recursion)
   */
  private void extractPropertiesRecursively(
      final Class<?> clazz,
      final String prefix,
      final Set<String> properties,
      final Set<Class<?>> visited) {

    if (visited.contains(clazz)) {
      return;
    }
    visited.add(clazz);

    // Find properties via getter/setter pairs (JavaBean convention)
    // This ensures we use the property name from the getter (e.g., getProcessing -> "processing")
    // rather than the field name (e.g., "processingCfg")
    for (final Method getter : clazz.getMethods()) {
      final String getterName = getter.getName();

      // Only process getters (getXxx or isXxx for booleans)
      if (!isGetter(getter)) {
        continue;
      }

      final String propertyName = extractPropertyNameFromGetterMethod(getter);
      if (propertyName == null || propertyName.isEmpty()) {
        continue;
      }

      // Find the corresponding setter and use its parameter type
      // This handles cases where getter returns a different type than the setter accepts
      // (e.g., getCleanupSchedule() returns Schedule but setCleanupSchedule(String) takes String)
      final Method setter = findSetter(clazz, propertyName);
      if (setter == null) {
        continue;
      }

      // Use the setter's parameter type as the actual property type for configuration
      final Class<?> setterParamType = setter.getParameterTypes()[0];
      final Class<?> getterReturnType = getter.getReturnType();

      // Use setter param type to determine if this is a leaf or nested config
      // But use getter return type for recursion (since that's what we navigate through)
      final Class<?> typeForLeafCheck = setterParamType;
      final Class<?> typeForRecursion = getterReturnType;

      final String propertyPath = prefix.isEmpty() ? propertyName : prefix + "." + propertyName;

      if (isLeafType(typeForLeafCheck)) {
        // This is a leaf property - add it
        properties.add(propertyPath);
      } else if (Map.class.isAssignableFrom(typeForLeafCheck)) {
        // Maps are treated as extensible/dynamic - we track them as a single property
        properties.add(propertyPath);
      } else if (Collection.class.isAssignableFrom(typeForLeafCheck)) {
        // Collections - treat as single property
        properties.add(propertyPath);
      } else {
        // This is a nested configuration object - recurse into it
        // Use getter return type for recursion since that's what we navigate through
        extractPropertiesRecursively(typeForRecursion, propertyPath, properties, visited);
      }
    }
  }

  /** Finds the setter method for a given property name. */
  private Method findSetter(final Class<?> clazz, final String propertyName) {
    final String setterName = "set" + capitalize(propertyName);
    for (final Method method : clazz.getMethods()) {
      if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
        return method;
      }
    }
    return null;
  }

  /** Checks if a method is a getter (getXxx or isXxx for booleans). */
  private boolean isGetter(final Method method) {
    final String name = method.getName();
    final int paramCount = method.getParameterCount();

    if (paramCount != 0) {
      return false;
    }

    // Skip getClass() and other Object methods
    if (method.getDeclaringClass() == Object.class) {
      return false;
    }

    if (name.startsWith("get") && name.length() > 3) {
      return true;
    }

    if (name.startsWith("is")
        && name.length() > 2
        && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
      return true;
    }

    return false;
  }

  /** Extracts the property name from a getter method. */
  private String extractPropertyNameFromGetterMethod(final Method getter) {
    final String name = getter.getName();
    String withoutPrefix;

    if (name.startsWith("get")) {
      withoutPrefix = name.substring(3);
    } else if (name.startsWith("is")) {
      withoutPrefix = name.substring(2);
    } else {
      return null;
    }

    if (withoutPrefix.isEmpty()) {
      return null;
    }

    return Character.toLowerCase(withoutPrefix.charAt(0)) + withoutPrefix.substring(1);
  }

  /**
   * Checks if a type is a "leaf" type (primitive, wrapper, String, Duration, etc.) that should not
   * be recursed into.
   */
  private boolean isLeafType(final Class<?> type) {
    return LEAF_TYPES.contains(type) || type.isEnum() || type.isPrimitive();
  }

  private String capitalize(final String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }

  /**
   * Extracts property paths that are mapped in BrokerBasedPropertiesOverride by analyzing its
   * source code using JavaParser.
   *
   * <p>This analysis tracks local variables in two directions:
   *
   * <ul>
   *   <li>Forward: Variables assigned FROM override chains (e.g., {@code s3 =
   *       override.getData().getBackup().getS3()})
   *   <li>Reverse: Variables assigned TO override via setters (e.g., {@code
   *       override.setThreads(threadsCfg)})
   * </ul>
   *
   * @return Set of property paths that have mappings
   */
  private Set<String> extractMappedPropertiesFromOverride() throws IOException {
    final Set<String> mappedProperties = new HashSet<>();

    final String sourceCode = Files.readString(BROKER_BASED_PROPERTIES_OVERRIDE_PATH);
    final CompilationUnit cu = StaticJavaParser.parse(sourceCode);

    // Process each method in the class
    final List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);

    for (final MethodDeclaration method : methods) {
      // Track variable-to-path mappings within this method
      // e.g., "s3BackupStoreConfig" -> "data.backup.s3"
      final Map<String, String> variablePathMap = new HashMap<>();

      // The "override" variable is always the root
      variablePathMap.put("override", "");

      // First pass: find all variable declarations/assignments from override chains
      method
          .findAll(VariableDeclarator.class)
          .forEach(
              vd -> {
                final String varName = vd.getNameAsString();
                vd.getInitializer()
                    .ifPresent(
                        init -> {
                          final String path = resolvePathFromExpression(init, variablePathMap);
                          if (path != null) {
                            variablePathMap.put(varName, path);
                          }
                        });
              });

      // Also handle assignments like "brokerNetwork = override.getNetwork()"
      method
          .findAll(AssignExpr.class)
          .forEach(
              ae -> {
                if (ae.getTarget().isNameExpr()) {
                  final String varName = ae.getTarget().asNameExpr().getNameAsString();
                  final String path = resolvePathFromExpression(ae.getValue(), variablePathMap);
                  if (path != null) {
                    variablePathMap.put(varName, path);
                  }
                }
              });

      // Second pass: find reverse mappings where a local variable is passed to override.setXxx()
      // e.g., override.setThreads(threadsCfg) means threadsCfg maps to "threads"
      method
          .findAll(MethodCallExpr.class)
          .forEach(
              mc -> {
                final String methodName = mc.getNameAsString();
                if (methodName.startsWith("set") && mc.getArguments().size() == 1) {
                  final var arg = mc.getArgument(0);
                  if (arg.isNameExpr()) {
                    final String argVarName = arg.asNameExpr().getNameAsString();
                    // Check if this setter is called on override or a tracked variable
                    if (mc.getScope().isPresent()) {
                      final String scopePath =
                          resolvePathFromExpression(mc.getScope().get(), variablePathMap);
                      if (scopePath != null) {
                        final String propertyName = extractPropertyNameFromSetter(methodName);
                        final String fullPath =
                            scopePath.isEmpty() ? propertyName : scopePath + "." + propertyName;
                        // Map the argument variable to this path
                        variablePathMap.put(argVarName, fullPath);
                      }
                    }
                  }
                }
              });

      // Third pass: find all setter calls and resolve their full paths
      method
          .findAll(MethodCallExpr.class)
          .forEach(
              mc -> {
                final String propertyPath = extractPropertyPathFromMethodCall(mc, variablePathMap);
                if (propertyPath != null && !propertyPath.isEmpty()) {
                  mappedProperties.add(propertyPath);
                }
              });
    }

    return mappedProperties;
  }

  /**
   * Resolves the property path from an expression, using the variable-to-path map.
   *
   * @param expr The expression (typically a method call chain)
   * @param variablePathMap Map of variable names to their known paths
   * @return The resolved property path, or null if it can't be resolved
   */
  private String resolvePathFromExpression(
      final Expression expr, final Map<String, String> variablePathMap) {

    if (expr.isMethodCallExpr()) {
      final MethodCallExpr methodCall = expr.asMethodCallExpr();
      final String methodName = methodCall.getNameAsString();

      // Only process getter methods
      if (!methodName.startsWith("get")) {
        return null;
      }

      final String propertyName = extractPropertyNameFromGetter(methodName);

      // Recursively resolve the scope
      if (methodCall.getScope().isPresent()) {
        final Expression scope = methodCall.getScope().get();
        final String scopePath = resolvePathFromExpression(scope, variablePathMap);
        if (scopePath != null) {
          return scopePath.isEmpty() ? propertyName : scopePath + "." + propertyName;
        }
      }
      return null;
    } else if (expr.isNameExpr()) {
      final String varName = expr.asNameExpr().getNameAsString();
      return variablePathMap.get(varName);
    }

    return null;
  }

  /**
   * Extracts a property path from a method call expression.
   *
   * <p>Handles two patterns:
   *
   * <ul>
   *   <li>Setter calls: override.getCluster().setNodeId(...) -> "cluster.nodeId"
   *   <li>Map operations: override.getExporters().put(...) -> "exporters"
   * </ul>
   *
   * @param methodCall The method call expression
   * @param variablePathMap Map of variable names to their known paths
   * @return The property path, or null if this isn't a mapping operation on a tracked object
   */
  private String extractPropertyPathFromMethodCall(
      final MethodCallExpr methodCall, final Map<String, String> variablePathMap) {

    final String methodName = methodCall.getNameAsString();

    // Handle setter calls (setXxx)
    if (methodName.startsWith("set")) {
      final String propertyName = extractPropertyNameFromSetter(methodName);

      // Get the scope (what the setter is called on)
      if (methodCall.getScope().isEmpty()) {
        return null;
      }

      final Expression scope = methodCall.getScope().get();
      final String scopePath = resolvePathFromExpression(scope, variablePathMap);

      if (scopePath == null) {
        return null;
      }

      return scopePath.isEmpty() ? propertyName : scopePath + "." + propertyName;
    }

    // Handle map operations like getExporters().put() or getExporters().forEach()
    // These indicate the map property itself is being mapped from unified config
    if (methodName.equals("put") || methodName.equals("forEach") || methodName.equals("putAll")) {
      if (methodCall.getScope().isEmpty()) {
        return null;
      }

      final Expression scope = methodCall.getScope().get();
      // The scope should be a getter call like getExporters()
      final String mapPropertyPath = resolvePathFromExpression(scope, variablePathMap);
      if (mapPropertyPath != null && !mapPropertyPath.isEmpty()) {
        return mapPropertyPath;
      }
    }

    return null;
  }

  /** Extracts property name from a setter method name (e.g., "setNodeId" -> "nodeId"). */
  private String extractPropertyNameFromSetter(final String setterName) {
    if (setterName.length() <= 3) {
      return "";
    }
    final String withoutSet = setterName.substring(3);
    return Character.toLowerCase(withoutSet.charAt(0)) + withoutSet.substring(1);
  }

  /** Extracts property name from a getter method name (e.g., "getCluster" -> "cluster"). */
  private String extractPropertyNameFromGetter(final String getterName) {
    if (getterName.length() <= 3) {
      return "";
    }
    final String withoutGet = getterName.substring(3);
    return Character.toLowerCase(withoutGet.charAt(0)) + withoutGet.substring(1);
  }
}
