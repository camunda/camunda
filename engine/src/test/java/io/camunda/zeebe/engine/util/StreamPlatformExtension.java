/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import static io.camunda.zeebe.engine.util.StreamProcessingComposite.getLogName;
import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;

import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.util.FileUtil;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode;

public class StreamPlatformExtension implements BeforeEachCallback {

  private static final String FIELD_STATE = "state";

  @Override
  public void beforeEach(final ExtensionContext extensionContext) throws Exception {
    extensionContext
        .getRequiredTestInstances()
        .getAllInstances()
        .forEach(instance -> injectFields(extensionContext, instance, instance.getClass()));
  }

  private Store getStore(final ExtensionContext context) {
    return context.getStore(Namespace.create(getClass(), context.getUniqueId()));
  }

  public StreamProcessorTestContext lookupOrCreate(final ExtensionContext extensionContext) {
    final var store = getStore(extensionContext);

    return (StreamProcessorTestContext)
        store.getOrComputeIfAbsent(FIELD_STATE, (key) -> new StreamProcessorTestContext());
  }

  private void injectFields(
      final ExtensionContext extensionContext,
      final Object testInstance,
      final Class<?> testClass) {

    ReflectionUtils.findFields(
            testClass,
            field -> ReflectionUtils.isNotStatic(field) && field.getType() == StreamPlatform.class,
            HierarchyTraversalMode.TOP_DOWN)
        .forEach(
            field -> {
              try {
                makeAccessible(field)
                    .set(testInstance, lookupOrCreate(extensionContext).streamPlatform);
              } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
              }
            });
  }

  private static final class StreamProcessorTestContext implements CloseableResource {
    private final ControlledActorClock clock = new ControlledActorClock();
    private StreamPlatform streamPlatform;
    private final ArrayList<AutoCloseable> closables;

    public StreamProcessorTestContext() {
      closables = new ArrayList<AutoCloseable>();
      // actor scheduler
      final var builder =
          ActorScheduler.newActorScheduler()
              .setCpuBoundActorThreadCount(
                  Math.max(1, Runtime.getRuntime().availableProcessors() - 2))
              .setIoBoundActorThreadCount(2)
              .setActorClock(clock);

      final ActorScheduler actorScheduler = builder.build();
      actorScheduler.start();
      closables.add(actorScheduler);

      try {
        final var tempFolder = Files.createTempDirectory(null);
        closables.add(() -> FileUtil.deleteFolderIfExists(tempFolder));
        final var factory = DefaultZeebeDbFactory.defaultFactory();

        // streams
        streamPlatform = new StreamPlatform(tempFolder, closables, actorScheduler, factory);
        final var partitionId = 1;
        streamPlatform.createLogStream(getLogName(partitionId), partitionId);

      } catch (final Exception e) {
        ExceptionUtils.throwAsUncheckedException(e);
      }
    }

    @Override
    public void close() throws Exception {
      Collections.reverse(closables);
      for (final var closeable : closables) {
        closeable.close();
      }
    }
  }
}
