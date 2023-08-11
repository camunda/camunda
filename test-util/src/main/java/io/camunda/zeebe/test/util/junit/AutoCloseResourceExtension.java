/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.junit;

import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.commons.util.ReflectionUtils;

final class AutoCloseResourceExtension implements BeforeEachCallback, BeforeAllCallback {

  @Override
  public void beforeAll(final ExtensionContext extensionContext) {
    final var store = store(extensionContext);
    lookupAnnotatedFields(extensionContext, null, ReflectionUtils::isStatic)
        .forEach(resource -> store.put(resource, resource));

    if (shouldManageNonAnnotatedFields(extensionContext)) {
      lookupAutoCloseableFields(extensionContext, null, ReflectionUtils::isStatic)
          .forEach(resource -> store.put(resource, resource));
    }
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) throws Exception {
    final var store = store(extensionContext);
    final var testInstance = extensionContext.getRequiredTestInstance();
    lookupAnnotatedFields(extensionContext, testInstance, ReflectionUtils::isNotStatic)
        .forEach(resource -> store.put(resource, resource));

    if (shouldManageNonAnnotatedFields(extensionContext)) {
      lookupAutoCloseableFields(extensionContext, testInstance, ReflectionUtils::isNotStatic)
          .forEach(resource -> store.put(resource, resource));
    }
  }

  private Iterable<CloseableResource> lookupAutoCloseableFields(
      final ExtensionContext extensionContext,
      final Object testInstance,
      final Predicate<Field> fieldType) {
    return ReflectionSupport.findFields(
            extensionContext.getRequiredTestClass(),
            fieldType
                .and(field -> !field.isAnnotationPresent(AutoCloseResource.class))
                .and(field -> ReflectionUtils.isAssignableTo(field.getType(), AutoCloseable.class)),
            HierarchyTraversalMode.TOP_DOWN)
        .stream()
        .map(field -> ofAutoCloseable(testInstance, field))
        .toList();
  }

  private Iterable<CloseableResource> lookupAnnotatedFields(
      final ExtensionContext extensionContext,
      final Object testInstance,
      final Predicate<Field> fieldType) {
    return ReflectionSupport.findFields(
            extensionContext.getRequiredTestClass(),
            fieldType.and(field -> field.isAnnotationPresent(AutoCloseResource.class)),
            HierarchyTraversalMode.TOP_DOWN)
        .stream()
        .map(field -> ofAnnotatedCloseable(testInstance, field))
        .toList();
  }

  private CloseableResource ofAnnotatedCloseable(final Object testInstance, final Field field) {
    final var annotation = field.getAnnotation(AutoCloseResource.class);
    final var method =
        ReflectionUtils.findMethod(field.getType(), annotation.closeMethod()).orElseThrow();

    ReflectionUtils.makeAccessible(field);
    ReflectionUtils.makeAccessible(method);

    return new AnnotatedCloseable(testInstance, field, method);
  }

  private CloseableResource ofAutoCloseable(final Object testInstance, final Field field) {
    final AutoCloseable value;

    try {
      value = (AutoCloseable) ReflectionUtils.makeAccessible(field).get(testInstance);
    } catch (final IllegalAccessException e) {
      throw new UnsupportedOperationException(e);
    }

    return new AutoCloseableResource(value);
  }

  private Store store(final ExtensionContext extensionContext) {
    return extensionContext.getStore(Namespace.create(AutoCloseResourceExtension.class));
  }

  private boolean shouldManageNonAnnotatedFields(final ExtensionContext extensionContext) {
    return !extensionContext
        .getRequiredTestClass()
        .getAnnotation(AutoCloseResources.class)
        .onlyAnnotated();
  }

  private record AnnotatedCloseable(Object testInstance, Field objectField, Method method)
      implements CloseableResource {

    @Override
    public void close() throws Exception {
      final var value = objectField.get(testInstance);
      if (value != null) {
        method.invoke(value);
      }
    }
  }

  private record AutoCloseableResource(AutoCloseable resource) implements CloseableResource {

    @Override
    public void close() throws Exception {
      resource.close();
    }
  }
}
