/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.camunda.zeebe.qa.util.cluster.ZeebeClusters.ZeebeCluster;
import io.camunda.zeebe.qa.util.cluster.spring.SpringCluster;
import io.camunda.zeebe.test.util.record.RecordLogger;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.lang.reflect.Field;
import java.util.function.Predicate;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.commons.util.ReflectionUtils;

final class ZeebeClusterExtension
    implements BeforeAllCallback, BeforeEachCallback, BeforeTestExecutionCallback, TestWatcher {

  @Override
  public void beforeAll(final ExtensionContext extensionContext) {
    final var resources = lookupClusters(extensionContext, null, ReflectionUtils::isStatic);
    manageClusters(extensionContext, resources);
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    final var testInstance = extensionContext.getRequiredTestInstance();
    final var resources =
        lookupClusters(extensionContext, testInstance, ReflectionUtils::isNotStatic);
    manageClusters(extensionContext, resources);
  }

  @Override
  public void testFailed(final ExtensionContext context, final Throwable cause) {
    RecordLogger.logRecords();
  }

  @Override
  public void beforeTestExecution(final ExtensionContext extensionContext) {
    RecordingExporter.reset();
  }

  private Iterable<ClusterResource> lookupClusters(
      final ExtensionContext extensionContext,
      final Object testInstance,
      final Predicate<Field> fieldType) {
    return ReflectionSupport.findFields(
            extensionContext.getRequiredTestClass(),
            fieldType
                .and(field -> field.isAnnotationPresent(ZeebeCluster.class))
                .and(field -> ReflectionUtils.isAssignableTo(field.getType(), SpringCluster.class)),
            HierarchyTraversalMode.TOP_DOWN)
        .stream()
        .map(field -> asResource(testInstance, field))
        .toList();
  }

  private void manageClusters(
      final ExtensionContext extensionContext, final Iterable<ClusterResource> resources) {
    final var store = store(extensionContext);

    // register all resources first to ensure we close them; this avoids leaking resource if
    // starting one fails
    resources.forEach(resource -> store.put(resource, resource));
    for (final var resource : resources) {
      manageCluster(resource);
    }
  }

  private void manageCluster(final ClusterResource resource) {
    final var cluster = resource.cluster;
    final var annotation = resource.annotation;

    cluster.start();

    if (annotation.awaitReady()) {
      cluster.await(ZeebeHealthProbe.READY);
    }

    if (annotation.awaitCompleteTopology()) {
      cluster.awaitCompleteTopology();
    }
  }

  private ClusterResource asResource(final Object testInstance, final Field field) {
    final SpringCluster value;

    try {
      value = (SpringCluster) ReflectionUtils.makeAccessible(field).get(testInstance);
    } catch (final IllegalAccessException e) {
      throw new UnsupportedOperationException(e);
    }

    return new ClusterResource(value, field.getAnnotation(ZeebeCluster.class));
  }

  private Store store(final ExtensionContext extensionContext) {
    return extensionContext.getStore(Namespace.create(ZeebeClusterExtension.class));
  }

  private record ClusterResource(SpringCluster cluster, ZeebeCluster annotation)
      implements CloseableResource {

    @Override
    public void close() {
      cluster.close();
    }
  }
}
