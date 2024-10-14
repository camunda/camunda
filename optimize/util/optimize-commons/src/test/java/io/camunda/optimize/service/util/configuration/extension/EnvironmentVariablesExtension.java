/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.extension;

import static java.lang.Class.forName;
import static java.lang.System.getenv;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

/**
 * Extension to allow manipulation of Environment Variables and restore their state after test
 * completion The logic is an extension based variant on the environment variable aspect of this WIP
 * library:
 * https://github.com/stefanbirkner/system-lambda/blob/master/src/main/java/com/github/stefanbirkner/systemlambda/SystemLambda.java
 */
public class EnvironmentVariablesExtension implements BeforeEachCallback, AfterEachCallback {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(EnvironmentVariablesExtension.class);
  private Map<String, String> originalVariables;
  private final Map<String, String> setVariables;

  public EnvironmentVariablesExtension() {
    setVariables = new HashMap<>();
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    originalVariables = new HashMap<>(getenv());
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    restoreOriginalVariables(originalVariables);
  }

  public void set(final String name, final String value) {
    validateNotSet(name, value);
    setVariables.put(name, value);
    setEnvironmentVariables();
  }

  private void validateNotSet(final String name, final String value) {
    if (setVariables.containsKey(name)) {
      final String currentValue = setVariables.get(name);
      throw new IllegalArgumentException(
          String.format(
              "The environment variable '%s' cannot be set to %s because it was already set to %s.",
              name, format(value), format(currentValue)));
    }
  }

  private String format(final String text) {
    return text == null ? "null" : "'" + text + "'";
  }

  private void setEnvironmentVariables() {
    overrideVariables(getEditableMapOfVariables());
    overrideVariables(getTheCaseInsensitiveEnvironment());
  }

  private void overrideVariables(final Map<String, String> existingVariables) {
    if (existingVariables != null) { // will be null when running on non-Windows machine and
      // theCaseInsensitiveEnvironment variables are passed
      setVariables.forEach((name, value) -> set(existingVariables, name, value));
    }
  }

  private void set(final Map<String, String> variables, final String name, final String value) {
    if (value == null) {
      variables.remove(name);
    } else {
      variables.put(name, value);
    }
  }

  private void restoreOriginalVariables(final Map<String, String> originalVariables) {
    restoreVariables(getEditableMapOfVariables(), originalVariables);
    restoreVariables(getTheCaseInsensitiveEnvironment(), originalVariables);
  }

  private void restoreVariables(
      final Map<String, String> variables, final Map<String, String> originalVariables) {
    if (variables != null) { // theCaseInsensitiveEnvironment may be null
      variables.clear();
      variables.putAll(originalVariables);
    }
  }

  private static Map<String, String> getEditableMapOfVariables() {
    final Class<?> classOfMap = getenv().getClass();
    try {
      return getFieldValue(classOfMap, getenv(), "m");
    } catch (final IllegalAccessException e) {
      throw new OptimizeRuntimeException(
          "Cannot access the field 'm' of the map System.getenv().", e);
    } catch (final NoSuchFieldException e) {
      throw new OptimizeRuntimeException(
          "Expecting System.getenv() to have a field 'm' but it has not.", e);
    }
  }

  /*
   * The names of environment variables are case-insensitive in Windows.
   * Therefore it stores the variables in a TreeMap named
   * theCaseInsensitiveEnvironment.
   */
  private static Map<String, String> getTheCaseInsensitiveEnvironment() {
    final String caseInsensitiveEnv = "theCaseInsensitiveEnvironment";
    final String processEnv = "java.lang.ProcessEnvironment";
    try {
      final Class<?> processEnvironment = forName(processEnv);
      return getFieldValue(processEnvironment, null, caseInsensitiveEnv);
    } catch (final ClassNotFoundException e) {
      throw new OptimizeRuntimeException(
          String.format(
              "Expecting the existence of the class %s but it does not exist.", processEnv),
          e);
    } catch (final IllegalAccessException e) {
      throw new OptimizeRuntimeException(
          String.format(
              "Cannot access the static field %s of the class %s.", caseInsensitiveEnv, processEnv),
          e);
    } catch (final NoSuchFieldException e) {
      // this field is only available for Windows
      return null;
    }
  }

  private static Map<String, String> getFieldValue(
      final Class<?> klass, final Object object, final String name)
      throws NoSuchFieldException, IllegalAccessException {
    final Field field = klass.getDeclaredField(name);
    field.setAccessible(true);
    return (Map<String, String>) field.get(object);
  }
}
