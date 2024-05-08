/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.junit;

import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ModifierSupport;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.commons.util.ReflectionUtils;

final class AutoCloseResourceExtension implements BeforeEachCallback, BeforeAllCallback {
  @Override
  public void beforeAll(final ExtensionContext extensionContext) {
    final var store = store(extensionContext);
    lookupAnnotatedFields(extensionContext, null, ModifierSupport::isStatic)
        .forEach(resource -> store.put(resource, resource));
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    final var store = store(extensionContext);
    final var testInstance = extensionContext.getRequiredTestInstance();
    lookupAnnotatedFields(extensionContext, testInstance, ModifierSupport::isNotStatic)
        .forEach(resource -> store.put(resource, resource));
  }

  private Iterable<CloseableResource> lookupAnnotatedFields(
      final ExtensionContext extensionContext,
      final Object testInstance,
      final Predicate<Field> fieldType) {
    return AnnotationSupport.findAnnotatedFields(
            extensionContext.getRequiredTestClass(),
            AutoCloseResource.class,
            fieldType,
            HierarchyTraversalMode.TOP_DOWN)
        .stream()
        .map(field -> ofAnnotatedCloseable(testInstance, field))
        .toList();
  }

  private CloseableResource ofAnnotatedCloseable(final Object testInstance, final Field field) {
    final var annotation = field.getAnnotation(AutoCloseResource.class);
    final var method =
        ReflectionSupport.findMethod(field.getType(), annotation.closeMethod())
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "No close method '%s' for object of type '%s'; did you forget to set a custom close method?"
                            .formatted(annotation.closeMethod(), field.getType().getName())));

    ReflectionUtils.makeAccessible(field);
    ReflectionUtils.makeAccessible(method);

    return new AnnotatedCloseable(testInstance, field, method);
  }

  private Store store(final ExtensionContext extensionContext) {
    return extensionContext.getStore(Namespace.create(AutoCloseResourceExtension.class));
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
}
