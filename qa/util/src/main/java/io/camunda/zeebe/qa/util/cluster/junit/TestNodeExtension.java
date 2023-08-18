/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster.junit;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.shared.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.zeebe.qa.util.cluster.TestStandalone;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneCluster;
import io.camunda.zeebe.qa.util.cluster.ZeebeHealthProbe;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes.TestCluster;
import io.camunda.zeebe.test.util.record.RecordLogger;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
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

final class TestNodeExtension
    implements BeforeAllCallback, BeforeEachCallback, BeforeTestExecutionCallback, TestWatcher {

  @Override
  public void beforeAll(final ExtensionContext extensionContext) {
    final var resources = lookupClusters(extensionContext, null, ReflectionUtils::isStatic);
    final var nodes = lookupNodes(extensionContext, null, ReflectionUtils::isStatic);
    manageClusters(extensionContext, resources);
    manageNodes(extensionContext, nodes);
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    final var testInstance = extensionContext.getRequiredTestInstance();
    final var clusters =
        lookupClusters(extensionContext, testInstance, ReflectionUtils::isNotStatic);
    final var nodes = lookupNodes(extensionContext, testInstance, ReflectionUtils::isNotStatic);
    manageClusters(extensionContext, clusters);
    manageNodes(extensionContext, nodes);
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
                .and(field -> field.isAnnotationPresent(TestCluster.class))
                .and(
                    field ->
                        ReflectionUtils.isAssignableTo(
                            field.getType(), TestStandaloneCluster.class)),
            HierarchyTraversalMode.TOP_DOWN)
        .stream()
        .map(field -> asClusterResource(testInstance, field))
        .toList();
  }

  private Iterable<NodeResource> lookupNodes(
      final ExtensionContext extensionContext,
      final Object testInstance,
      final Predicate<Field> fieldType) {
    return ReflectionSupport.findFields(
            extensionContext.getRequiredTestClass(),
            fieldType
                .and(field -> field.isAnnotationPresent(ManageTestNodes.TestNode.class))
                .and(
                    field -> ReflectionUtils.isAssignableTo(field.getType(), TestStandalone.class)),
            HierarchyTraversalMode.TOP_DOWN)
        .stream()
        .map(field -> asNodeResource(testInstance, field))
        .toList();
  }

  private void manageClusters(
      final ExtensionContext extensionContext, final Iterable<ClusterResource> resources) {
    final var store = store(extensionContext);

    // register all resources first to ensure we close them; this avoids leaking resource if
    // starting one fails
    resources.forEach(resource -> store.put(resource, resource));
    for (final var resource : resources) {
      final var directory = createManagedDirectory(store, resource.cluster.name());
      manageCluster(directory, resource);
    }
  }

  private void manageCluster(final Path directory, final ClusterResource resource) {
    final var cluster = resource.cluster;
    final var annotation = resource.annotation;

    // assign a working directory for each broker that gets deleted with the extension lifecycle,
    // and not when the broker is shutdown. this allows to introspect or move the data around even
    // after stopping a broker
    cluster.brokers().forEach((id, broker) -> setWorkingDirectory(directory, id, broker));

    if (annotation.autoStart()) {
      cluster.start();

      if (annotation.awaitStarted()) {
        cluster.await(ZeebeHealthProbe.STARTED);
      }

      if (annotation.awaitReady()) {
        cluster.await(ZeebeHealthProbe.READY);
      }

      if (annotation.awaitCompleteTopology()) {
        cluster.awaitCompleteTopology();
      }
    }
  }

  private void manageNodes(
      final ExtensionContext extensionContext, final Iterable<NodeResource> resources) {
    final var store = store(extensionContext);

    // register all resources first to ensure we close them; this avoids leaking resource if
    // starting one fails
    resources.forEach(resource -> store.put(resource, resource));
    for (final var resource : resources) {
      manageNode(store, resource);
    }
  }

  private void manageNode(final Store store, final NodeResource resource) {
    final var node = resource.node;
    final var annotation = resource.annotation;

    if (node instanceof final TestStandaloneBroker broker) {
      final var directory = createManagedDirectory(store, "broker-" + broker.nodeId().id());
      setWorkingDirectory(directory, broker.nodeId(), broker);
    }

    if (annotation.autoStart()) {
      node.start();

      if (annotation.awaitStarted()) {
        node.await(ZeebeHealthProbe.STARTED);
      }

      if (annotation.awaitReady()) {
        node.await(ZeebeHealthProbe.READY);
      }
    }
  }

  private void setWorkingDirectory(
      final Path directory, final MemberId id, final TestStandaloneBroker broker) {
    final Path workingDirectory = directory.resolve("broker-" + id.id());
    try {
      Files.createDirectory(workingDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    broker.withBean(
        "workingDirectory", new WorkingDirectory(workingDirectory, false), WorkingDirectory.class);
  }

  private Path createManagedDirectory(final Store store, final String prefix) {
    try {
      final var directory = Files.createTempDirectory(prefix);
      store.put(directory, new DirectoryResource(directory));
      return directory;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ClusterResource asClusterResource(final Object testInstance, final Field field) {
    final TestStandaloneCluster value;

    try {
      value = (TestStandaloneCluster) ReflectionUtils.makeAccessible(field).get(testInstance);
    } catch (final IllegalAccessException e) {
      throw new UnsupportedOperationException(e);
    }

    return new ClusterResource(value, field.getAnnotation(TestCluster.class));
  }

  private NodeResource asNodeResource(final Object testInstance, final Field field) {
    final TestStandalone<?> value;

    try {
      value = (TestStandalone<?>) ReflectionUtils.makeAccessible(field).get(testInstance);
    } catch (final IllegalAccessException e) {
      throw new UnsupportedOperationException(e);
    }

    return new NodeResource(value, field.getAnnotation(ManageTestNodes.TestNode.class));
  }

  private Store store(final ExtensionContext extensionContext) {
    return extensionContext.getStore(Namespace.create(TestNodeExtension.class));
  }

  private record ClusterResource(TestStandaloneCluster cluster, TestCluster annotation)
      implements CloseableResource {

    @Override
    public void close() {
      cluster.close();
    }
  }

  private record NodeResource(TestStandalone<?> node, ManageTestNodes.TestNode annotation)
      implements CloseableResource {

    @Override
    public void close() {
      node.stop();
    }
  }

  private record DirectoryResource(Path directory) implements CloseableResource {

    @Override
    public void close() throws Throwable {
      FileUtil.deleteFolderIfExists(directory);
    }
  }
}
