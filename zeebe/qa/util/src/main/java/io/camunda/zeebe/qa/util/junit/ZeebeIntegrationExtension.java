/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.zeebe.util.ReflectUtil;
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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
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
    implements BeforeAllCallback, BeforeEachCallback, TestWatcher {

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

    RecordingExporter.reset();
  }

  @Override
  public void testFailed(final ExtensionContext context, final Throwable cause) {
    RecordLogger.logRecords();
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
        .peek(
            field ->
                initResourceField(extensionContext.getRequiredTestClass(), testInstance, field))
        .map(field -> asClusterResource(testInstance, field))
        .toList();
  }

  private void initResourceField(
      final Class<?> requiredTestClass, final Object testInstance, final Field field) {
    final String initMethod = field.getAnnotation(TestZeebe.class).initMethod();
    if (!initMethod.isEmpty()) {
      ReflectionUtils.findMethod(requiredTestClass, initMethod)
          .ifPresentOrElse(
              method -> {
                method.setAccessible(true);
                try {
                  method.invoke(testInstance);
                } catch (final ReflectiveOperationException e) {
                  throw new UnsupportedOperationException(e);
                }
              },
              () -> {
                throw new IllegalArgumentException(
                    "Could not find method '%s' in class '%s'"
                        .formatted(initMethod, requiredTestClass));
              });
    }
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
        .peek(
            field ->
                initResourceField(extensionContext.getRequiredTestClass(), testInstance, field))
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
      final var directory = createManagedDirectory(store, resource.cluster().name());
      manageCluster(directory, resource);
    }
  }

  private void manageCluster(final Path directory, final ClusterResource resource) {
    final var cluster = resource.cluster();

    // assign a working directory for each broker that gets deleted with the extension lifecycle,
    // and not when the broker is shutdown. this allows to introspect or move the data around even
    // after stopping a broker
    cluster.brokers().forEach((id, broker) -> setWorkingDirectory(directory, id, broker));
    startTestZeebe(resource);
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
    // assign a working directory to the broker that gets deleted with the extension lifecycle,
    // and not when the broker is shutdown. this allows to introspect or move the data around even
    // after stopping a broker
    if (resource.app() instanceof final TestStandaloneBroker broker) {
      final var directory = createManagedDirectory(store, "broker-" + broker.nodeId().id());
      setWorkingDirectory(directory, broker.nodeId(), broker);
    }

    startTestZeebe(resource);
  }

  private void startTestZeebe(final TestZeebeResource resource) {
    final var annotation = resource.annotation();

    if (annotation.autoStart()) {
      resource.start();

      if (annotation.awaitStarted()) {
        resource.await(TestHealthProbe.STARTED);
      }

      if (annotation.awaitReady()) {
        resource.await(TestHealthProbe.READY);
      }

      if (annotation.awaitCompleteTopology()) {
        resource.awaitCompleteTopology();
      }
    }
  }

  private void setWorkingDirectory(
      final Path directory, final MemberId id, final TestStandaloneBroker broker) {
    if (broker.getWorkingDirectory() != null) {
      return;
    }
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
      // add the common junit prefix to clearly indicate these are test folders
      final var directory = Files.createTempDirectory("junit-" + prefix);
      store.put(directory, new DirectoryResource(directory));
      return directory;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ClusterResource asClusterResource(final Object testInstance, final Field field) {
    ReflectUtil.makeAccessible(field, testInstance);

    return new ClusterResource(testInstance, field, field.getAnnotation(TestZeebe.class));
  }

  private ApplicationResource asNodeResource(final Object testInstance, final Field field) {
    ReflectUtil.makeAccessible(field, testInstance);

    return new ApplicationResource(testInstance, field, field.getAnnotation(TestZeebe.class));
  }

  private Store store(final ExtensionContext extensionContext) {
    return extensionContext.getStore(Namespace.create(ZeebeIntegrationExtension.class));
  }

  private record ClusterResource(Object testInstance, Field field, TestZeebe annotation)
      implements TestZeebeResource, AutoCloseable {

    public TestCluster cluster() {
      try {
        return (TestCluster) field.get(testInstance);
      } catch (final IllegalAccessException e) {
        throw new UnsupportedOperationException(e);
      }
    }

    @Override
    public void close() {
      CloseHelper.close(
          error ->
              LOG.warn("Failed to close cluster {}, leaking resources", cluster().name(), error),
          cluster());
    }

    @Override
    public void start() {
      cluster().start();
    }

    @Override
    public void await(final TestHealthProbe probe) {
      cluster().await(probe);
    }

    @Override
    public void awaitCompleteTopology() {
      final var clusterSize =
          annotation.clusterSize() <= 0 ? cluster().brokers().size() : annotation.clusterSize();
      final var partitionCount =
          annotation.partitionCount() <= 0
              ? cluster().partitionsCount()
              : annotation.partitionCount();
      final var replicationFactor =
          annotation.replicationFactor() <= 0
              ? cluster().replicationFactor()
              : annotation.replicationFactor();
      final var timeout =
          annotation.topologyTimeoutMs() == 0
              ? Duration.ofMinutes(clusterSize)
              : Duration.ofMillis(annotation().topologyTimeoutMs());

      cluster().awaitCompleteTopology(clusterSize, partitionCount, replicationFactor, timeout);
    }
  }

  private record ApplicationResource(Object testInstance, Field field, TestZeebe annotation)
      implements TestZeebeResource, AutoCloseable {

    public TestApplication<?> app() {
      try {
        return (TestApplication<?>) field.get(testInstance);
      } catch (final IllegalAccessException e) {
        throw new UnsupportedOperationException(e);
      }
    }

    @Override
    public void close() {
      CloseHelper.close(
          error -> LOG.warn("Failed to close test app {}, leaking resources", app().nodeId()),
          app());
    }

    @Override
    public void start() {
      app().start();
    }

    @Override
    public void await(final TestHealthProbe probe) {
      app().await(probe);
    }

    @Override
    public void awaitCompleteTopology() {
      if (!(app().isGateway() && (app() instanceof final TestGateway<?> gateway))) {
        return;
      }

      final var timeout =
          annotation.topologyTimeoutMs() == 0
              ? Duration.ofMinutes(1)
              : Duration.ofMillis(annotation().topologyTimeoutMs());
      gateway.awaitCompleteTopology(
          Math.max(1, annotation.clusterSize()),
          Math.max(1, annotation.partitionCount()),
          Math.max(1, annotation.replicationFactor()),
          timeout);
    }
  }

  private record DirectoryResource(Path directory) implements AutoCloseable {

    @Override
    public void close() {
      try {
        FileUtil.deleteFolderIfExists(directory);
      } catch (final IOException e) {
        LOG.warn("Failed to clean up temporary directory {}, leaking resources...", directory, e);
      }
    }
  }

  private interface TestZeebeResource {
    TestZeebe annotation();

    void start();

    void await(final TestHealthProbe probe);

    void awaitCompleteTopology();
  }
}
