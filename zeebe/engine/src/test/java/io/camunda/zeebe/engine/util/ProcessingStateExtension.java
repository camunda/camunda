/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.util.stream.Collectors.joining;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.util.ReflectUtil;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.InstantSource;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode;

/**
 * Extension that creates a temporary folder, and sets up a Zeebe database in that temporary folder.
 * The extension injects instances of
 *
 * <ul>
 *   <li>{@code ZeebeDb} (exact type match)
 *   <li>{@code TransactionContext} (exact type match)
 *   <li>{@code MutableProcessingState} (exact type match or supertypes)
 * </ul>
 *
 * on fields with the corresponding type.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @ExtendWith(ProcessingStateExtension)
 * public class Test {
 *   private ZeebeDb db; //will be injected
 *   private TransactionContext txContext; //will be injected
 *   private MutableProcessingState state //will be injected
 *
 *   ...
 * }
 * }</pre>
 *
 * <p>Not Supported:
 *
 * <pre>{@code
 * private ZeebeDb db1; // will be injected
 * private ZeebeDb db2; // will be injected, but will be the same instance as db1
 * }</pre>
 */
public class ProcessingStateExtension implements BeforeEachCallback {

  private static final String FIELD_STATE = "state";

  private Function<EngineConfiguration, EngineConfiguration> engineConfigurationBuilder = c -> c;

  @Override
  public void beforeEach(final ExtensionContext context) {
    context
        .getRequiredTestInstances()
        .getAllInstances()
        .forEach(instance -> injectFields(context, instance, instance.getClass()));
  }

  public ProcessingStateExtension withEngineConfiguration(
      final Function<EngineConfiguration, EngineConfiguration> engineConfigurationBuilder) {
    this.engineConfigurationBuilder = engineConfigurationBuilder;
    return this;
  }

  public ProcessingStateExtensionState lookupOrCreate(final ExtensionContext extensionContext) {
    final var store = getStore(extensionContext);

    return (ProcessingStateExtensionState)
        store.getOrComputeIfAbsent(
            FIELD_STATE, (key) -> new ProcessingStateExtensionState(engineConfigurationBuilder));
  }

  private void injectFields(
      final ExtensionContext context, final Object testInstance, final Class<?> testClass) {

    ReflectionUtils.findFields(
            testClass,
            field -> ReflectionUtils.isNotStatic(field) && field.getType() == ZeebeDb.class,
            HierarchyTraversalMode.TOP_DOWN)
        .forEach(
            field -> {
              try {
                ReflectUtil.makeAccessible(field, testInstance)
                    .set(testInstance, lookupOrCreate(context).getZeebeDb());
              } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
              }
            });

    ReflectionUtils.findFields(
            testClass,
            field ->
                ReflectionUtils.isNotStatic(field) && field.getType() == TransactionContext.class,
            HierarchyTraversalMode.TOP_DOWN)
        .forEach(
            field -> {
              try {
                ReflectUtil.makeAccessible(field, testInstance)
                    .set(testInstance, lookupOrCreate(context).getTransactionContext());
              } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
              }
            });

    ReflectionUtils.findFields(
            testClass,
            field ->
                ReflectionUtils.isNotStatic(field)
                    && field.getType().isAssignableFrom(MutableProcessingState.class),
            HierarchyTraversalMode.TOP_DOWN)
        .forEach(
            field -> {
              try {
                ReflectUtil.makeAccessible(field, testInstance)
                    .set(testInstance, lookupOrCreate(context).getProcessingState());
              } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
              }
            });
  }

  private Store getStore(final ExtensionContext context) {
    return context.getStore(Namespace.create(getClass(), context.getUniqueId()));
  }

  private static final class ProcessingStateExtensionState implements CloseableResource {

    private Path tempFolder;
    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private TransactionContext transactionContext;
    private MutableProcessingState processingState;

    private ProcessingStateExtensionState(
        final Function<EngineConfiguration, EngineConfiguration> engineConfigurationBuilder) {

      final var factory = DefaultZeebeDbFactory.defaultFactory();
      try {
        tempFolder = Files.createTempDirectory(null);
        zeebeDb = factory.createDb(tempFolder.toFile());
        transactionContext = zeebeDb.createContext();
        final var keyGenerator =
            new DbKeyGenerator(Protocol.DEPLOYMENT_PARTITION, zeebeDb, transactionContext);
        processingState =
            new ProcessingDbState(
                Protocol.DEPLOYMENT_PARTITION,
                zeebeDb,
                transactionContext,
                keyGenerator,
                new TransientPendingSubscriptionState(),
                new TransientPendingSubscriptionState(),
                engineConfigurationBuilder.apply(new EngineConfiguration()),
                InstantSource.system());
      } catch (final Exception e) {
        ExceptionUtils.throwAsUncheckedException(e);
      }
    }

    @Override
    public void close() throws Throwable {
      transactionContext.getCurrentTransaction().rollback();
      zeebeDb.close();

      final SortedMap<Path, IOException> failures = clearFolder();

      if (!failures.isEmpty()) {
        throwException(failures);
      }
    }

    private void throwException(final SortedMap<Path, IOException> failures) throws IOException {
      final String joinedPaths =
          failures.keySet().stream().map(Path::toString).collect(joining(", "));

      final IOException exception =
          new IOException(
              "Failed to clear temp directory "
                  + tempFolder.toAbsolutePath()
                  + ". The following paths could not be deleted: "
                  + joinedPaths);
      failures.values().forEach(exception::addSuppressed);
      throw exception;
    }

    private SortedMap<Path, IOException> clearFolder() throws IOException {
      final SortedMap<Path, IOException> failures = new TreeMap<>();
      Files.walkFileTree(
          tempFolder,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(
                final Path file, final BasicFileAttributes attributes) {
              return delete(file);
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
              return delete(dir);
            }

            private FileVisitResult delete(final Path path) {
              try {
                Files.delete(path);
              } catch (final NoSuchFileException ignore) {
                // ignore
              } catch (final IOException exception) {
                try {
                  path.toFile().deleteOnExit();
                } catch (final UnsupportedOperationException ignore) {
                  // ignore
                }
                failures.put(path, exception);
              }
              return CONTINUE;
            }
          });
      return failures;
    }

    private ZeebeDb<ZbColumnFamilies> getZeebeDb() {
      return zeebeDb;
    }

    private MutableProcessingState getProcessingState() {
      return processingState;
    }

    private TransactionContext getTransactionContext() {
      return transactionContext;
    }
  }
}
