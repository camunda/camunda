/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.junit;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordLogger;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Predicate;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ModifierSupport;
import org.junit.platform.commons.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An extension which will manage all static and instance level fields of type {@link
 * TestApplication} and {@link TestCluster}, iff they are annotated by {@link TestZeebe}.
 *
 * <p>The lifecycle of these thus depends on the field being static. If it's static, then it's
 * started once before all tests, and stopped after all tests; if it's instance, then it's started
 * for every test, and stopped after every test. This includes all adjacent resources created for
 * that field (e.g. temporary folders, assigned ports, etc.)
 *
 * <p>For brokers, a temporary folder is created and managed by the extension. This allows you to
 * stop and restart the same broker with the same data without losing it.
 *
 * <p>Additionally, after every test, will reset the recording exporter. On failure, prints out the
 * recording exporter using a {@link RecordLogger}. If using a shared cluster, this may output
 * records from a previous test, since the recording exporter is not isolated to your test.
 *
 * <p>See {@link TestZeebe} for annotation parameters.
 */
final class ZeebeIntegrationExtension
    implements BeforeAllCallback, BeforeEachCallback, BeforeTestExecutionCallback, TestWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeIntegrationExtension.class);

  /**
   * Looks up all static {@link TestCluster} and {@link TestApplication} fields, tying their own
   * lifecycle to the {@link org.junit.jupiter.api.TestInstance.Lifecycle#PER_CLASS} lifecycle.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public void beforeAll(final ExtensionContext extensionContext) {
    final var resources = lookupClusters(extensionContext, null, ModifierSupport::isStatic);
    final var nodes = lookupApplications(extensionContext, null, ModifierSupport::isStatic);
    manageClusters(extensionContext, resources);
    manageApplications(extensionContext, nodes);
  }

  /**
   * Looks up all non-static {@link TestCluster} and {@link TestApplication} fields, tying their own
   * lifecycle to the {@link org.junit.jupiter.api.TestInstance.Lifecycle#PER_METHOD} lifecycle.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    final var testInstance = extensionContext.getRequiredTestInstance();
    final var clusters =
        lookupClusters(extensionContext, testInstance, ModifierSupport::isNotStatic);
    final var nodes =
        lookupApplications(extensionContext, testInstance, ModifierSupport::isNotStatic);
    manageClusters(extensionContext, clusters);
    manageApplications(extensionContext, nodes);
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
    return AnnotationSupport.findAnnotatedFields(
            extensionContext.getRequiredTestClass(),
            TestZeebe.class,
            fieldType.and(
                field -> ReflectionUtils.isAssignableTo(field.getType(), TestCluster.class)),
            HierarchyTraversalMode.TOP_DOWN)
        .stream()
        .map(field -> asClusterResource(testInstance, field))
        .toList();
  }

  private Iterable<ApplicationResource> lookupApplications(
      final ExtensionContext extensionContext,
      final Object testInstance,
      final Predicate<Field> fieldType) {
    return AnnotationSupport.findAnnotatedFields(
            extensionContext.getRequiredTestClass(),
            TestZeebe.class,
            fieldType.and(
                field -> ReflectionUtils.isAssignableTo(field.getType(), TestApplication.class)),
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
        cluster.await(TestHealthProbe.STARTED);
      }

      if (annotation.awaitReady()) {
        cluster.await(TestHealthProbe.READY);
      }

      if (annotation.awaitCompleteTopology()) {
        final var clusterSize =
            annotation.clusterSize() <= 0 ? cluster.brokers().size() : annotation.clusterSize();
        final var partitionCount =
            annotation.partitionCount() <= 0
                ? cluster.partitionsCount()
                : annotation.partitionCount();
        final var replicationFactor =
            annotation.replicationFactor() <= 0
                ? cluster.replicationFactor()
                : annotation.replicationFactor();

        cluster.awaitCompleteTopology(
            clusterSize,
            partitionCount,
            replicationFactor,
            Duration.ofMinutes(cluster.nodes().size()));
      }
    }
  }

  private void manageApplications(
      final ExtensionContext extensionContext, final Iterable<ApplicationResource> resources) {
    final var store = store(extensionContext);

    // register all resources first to ensure we close them; this avoids leaking resource if
    // starting one fails
    resources.forEach(resource -> store.put(resource, resource));
    for (final var resource : resources) {
      manageApplication(store, resource);
    }
  }

  private void manageApplication(final Store store, final ApplicationResource resource) {
    final var node = resource.node;
    final var annotation = resource.annotation;

    if (node instanceof final TestStandaloneBroker broker) {
      final var directory = createManagedDirectory(store, "broker-" + broker.nodeId().id());
      setWorkingDirectory(directory, broker.nodeId(), broker);
    }

    if (annotation.autoStart()) {
      node.start();

      if (annotation.awaitStarted()) {
        node.await(TestHealthProbe.STARTED);
      }

      if (annotation.awaitReady()) {
        node.await(TestHealthProbe.READY);
      }

      if (annotation.awaitCompleteTopology()
          && node.isGateway()
          && node instanceof final TestGateway<?> gateway) {
        gateway.awaitCompleteTopology(
            Math.max(1, annotation.clusterSize()),
            Math.max(1, annotation.clusterSize()),
            Math.max(1, annotation.clusterSize()),
            Duration.ofSeconds(30));
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

    broker.withWorkingDirectory(workingDirectory);
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
    final TestCluster value;

    try {
      value = (TestCluster) ReflectionUtils.makeAccessible(field).get(testInstance);
    } catch (final IllegalAccessException e) {
      throw new UnsupportedOperationException(e);
    }

    return new ClusterResource(value, field.getAnnotation(TestZeebe.class));
  }

  private ApplicationResource asNodeResource(final Object testInstance, final Field field) {
    final TestApplication<?> value;

    try {
      value = (TestApplication<?>) ReflectionUtils.makeAccessible(field).get(testInstance);
    } catch (final IllegalAccessException e) {
      throw new UnsupportedOperationException(e);
    }

    return new ApplicationResource(value, field.getAnnotation(TestZeebe.class));
  }

  private Store store(final ExtensionContext extensionContext) {
    return extensionContext.getStore(Namespace.create(ZeebeIntegrationExtension.class));
  }

  private record ClusterResource(TestCluster cluster, TestZeebe annotation)
      implements CloseableResource {

    @Override
    public void close() {
      CloseHelper.close(
          error -> LOG.warn("Failed to close cluster {}, leaking resources", cluster.name(), error),
          cluster);
    }
  }

  private record ApplicationResource(TestApplication<?> node, TestZeebe annotation)
      implements CloseableResource {

    @Override
    public void close() {
      CloseHelper.close(
          error -> LOG.warn("Failed to close test node {}, leaking resources", node.nodeId()),
          node);
    }
  }

  private record DirectoryResource(Path directory) implements CloseableResource {

    @Override
    public void close() {
      try {
        FileUtil.deleteFolderIfExists(directory);
      } catch (final IOException e) {
        LOG.warn("Failed to clean up temporary directory {}, leaking resources...", directory, e);
      }
    }
  }
}
