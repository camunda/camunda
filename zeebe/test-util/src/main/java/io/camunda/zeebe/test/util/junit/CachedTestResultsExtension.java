/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.junit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * JUnit extension to cache successfully executed test methods and skip them on subsequent runs.
 * Results are written after all tests have run or (in a best-effort manner) when the JVM is shut
 * down to avoid losing the cache in case of a crash or an unexpected termination.
 *
 * <p>
 * The cache is organized by test method name and arguments and should not be used across different test classes to avoid conflicts.
 *
 * <p>Usage:
 * <pre>{@code
 * final class MyExpensiveTest {
 *   @RegisterExtension
 *   static final CachedTestResultsExtension cache = new CachedTestResultsExtension(Path.of("my-expensive-test"));
 *
 *   @ParameterizedTest
 *   @ValueSource(strings = {"foo", "bar"})
 *   void testSomethingSlowly(String arg) {
 *
 *   }
 * }</pre>
 */
public final class CachedTestResultsExtension
    implements TestWatcher, AfterAllCallback, BeforeAllCallback, InvocationInterceptor {
  public static final String CACHE_KEY = "cache";
  public static final String SHUTDOWN_HOOK_KEY = "shutdownHook";
  public static final String PERIODIC_SAVE_KEY = "periodicSave";
  private final Path cachePath;

  /**
   * Create a new extension that caches all successful test invocations in a local file.
   *
   * @param cachePath the path to the file to store the cache in. If null, a temporary file will be
   *     used.
   */
  public CachedTestResultsExtension(final Path cachePath) {
    this.cachePath =
        Optional.ofNullable(cachePath)
            .orElseGet(
                () -> {
                  try {
                    return Files.createTempFile("cached-test-results-", null);
                  } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                  }
                });
  }

  private ExtensionContext.Store getStore(final ExtensionContext context) {
    return context.getStore(Namespace.create(CachedTestResultsExtension.class));
  }

  private InvocationCache getCache(final ExtensionContext context) {
    return getStore(context).get(CACHE_KEY, InvocationCache.class);
  }

  @Override
  public void testSuccessful(final ExtensionContext context) {
    getCache(context).confirm(context.getUniqueId());
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    final var cache =
        getStore(context)
            .getOrComputeIfAbsent(
                CACHE_KEY, (k) -> InvocationCache.recover(cachePath), InvocationCache.class);
    final var hook =
        getStore(context)
            .getOrComputeIfAbsent(
                SHUTDOWN_HOOK_KEY, key -> new Thread(cache::snapshot), Thread.class);
    Runtime.getRuntime().addShutdownHook(hook);

    final var timer =
        getStore(context)
            .getOrComputeIfAbsent(PERIODIC_SAVE_KEY, key -> new Timer(true), Timer.class);
    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            cache.snapshot();
          }
        },
        30_000,
        30_000);
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    final var shutdownHook = getStore(context).remove(SHUTDOWN_HOOK_KEY, Thread.class);
    Runtime.getRuntime().removeShutdownHook(shutdownHook);
    getStore(context).remove(PERIODIC_SAVE_KEY, Timer.class).cancel();
    getCache(context).snapshot();
  }

  @Override
  public void interceptTestMethod(
      final Invocation<Void> invocation,
      final ReflectiveInvocationContext<Method> invocationContext,
      final ExtensionContext extensionContext)
      throws Throwable {
    interceptMethod(extensionContext, invocation, invocationContext.getArguments());
  }

  @Override
  public <T> T interceptTestFactoryMethod(
      final Invocation<T> invocation,
      final ReflectiveInvocationContext<Method> invocationContext,
      final ExtensionContext extensionContext)
      throws Throwable {
    return interceptMethod(extensionContext, invocation, invocationContext.getArguments());
  }

  @Override
  public void interceptTestTemplateMethod(
      final Invocation<Void> invocation,
      final ReflectiveInvocationContext<Method> invocationContext,
      final ExtensionContext extensionContext)
      throws Throwable {
    interceptMethod(extensionContext, invocation, invocationContext.getArguments());
  }

  @Override
  public void interceptDynamicTest(
      final Invocation<Void> invocation,
      final DynamicTestInvocationContext invocationContext,
      final ExtensionContext extensionContext) {
    // `invocationContext` doesn't contain the arguments, so we can't cache dynamic tests
    throw new UnsupportedOperationException("Can't cache dynamic tests.");
  }

  <T> T interceptMethod(
      final ExtensionContext extensionContext,
      final Invocation<T> invocation,
      final List<Object> arguments)
      throws Throwable {
    final var cache = getCache(extensionContext);
    final var cached =
        CachedInvocation.ofMethod(extensionContext.getRequiredTestMethod(), arguments);
    if (cache.contains(cached)) {
      invocation.skip();
      return null;
    }
    cache.stage(extensionContext.getUniqueId(), cached);
    return invocation.proceed();
  }

  /**
   * Manages the in-memory and on-disk cache of test invocations. New invocations have to be {@link
   * #stage(String, CachedInvocation) staged} with a unique id first and then {@link
   * #confirm(String) confirmed}. Confirmed invocations are written to disk when {@link #snapshot()
   * snapshotted} and are recovered from disk when the cache is {@link #recover(Path) recovered}.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * final var cache = InvocationCache.recover(Path.of("my-cache"));
   * assertThat(cache.contains(new CachedInvocation("myTest1", "arg1->arg2"))).isTrue();
   * assertThat(cache.contains(new CachedInvocation("myTest1", "arg1->arg3"))).isFalse();
   * cache.stage("test-run-id-123", new CachedInvocation("myTest1", "arg1->arg3"));
   * assertThat(cache.contains(new CachedInvocation("myTest1", "arg1->arg3"))).isFalse();
   * cache.confirm("test-run-id-123");
   * assertThat(cache.contains(new CachedInvocation("myTest1", "arg1->arg3"))).isTrue();
   *
   * final var cache2 = InvocationCache.recover(Path.of("my-cache"));
   * assertThat(cache2.contains(new CachedInvocation("myTest1", "arg1->arg3"))).isFalse();
   * cache2.stage("test-run-id-123", new CachedInvocation("myTest1", "arg1->arg3"));
   * cache2.confirm("myTest2");
   * cache2.snapshot();
   *
   * final var cache3 = InvocationCache.recover(Path.of("my-cache"));
   * assertThat(cache3.contains(new CachedInvocation("myTest1", "arg1->arg3"))).isTrue();
   * }</pre>
   */
  private static final class InvocationCache {
    final Path path;
    final SortedSet<CachedInvocation> cached;
    final Map<String, CachedInvocation> staged;

    private InvocationCache(final Path path, final SortedSet<CachedInvocation> arguments) {
      this.path = Objects.requireNonNull(path);
      cached = new ConcurrentSkipListSet<>(arguments);
      staged = new ConcurrentHashMap<>();
    }

    boolean contains(final CachedInvocation invocation) {
      return cached.contains(invocation);
    }

    void stage(final String id, final CachedInvocation invocation) {
      final var previous = staged.putIfAbsent(id, invocation);
      if (previous != null) {
        throw new IllegalArgumentException(
            "Invocation with id " + id + " is already staged: " + previous);
      }
    }

    void confirm(final String id) {
      final var invocation = staged.remove(id);
      if (invocation == null) {
        return;
      }
      if (!cached.add(invocation)) {
        throw new IllegalStateException("Invocation " + invocation + "is already cached.");
      }
    }

    static InvocationCache recover(final Path path) {
      if (!Files.exists(path)) {
        return new InvocationCache(path, new TreeSet<>());
      }

      try (final var reader = Files.newBufferedReader(path)) {
        final var pairs =
            reader
                .lines()
                .map(CachedInvocation::fromLine)
                .collect(Collectors.toCollection(TreeSet::new));
        return new InvocationCache(path, pairs);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    synchronized void snapshot() {
      try (final var writer = Files.newBufferedWriter(path)) {
        for (final var version : cached) {
          writer.write(version.toLine());
          writer.newLine();
        }
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private record CachedInvocation(String method, String arguments)
      implements Comparable<CachedInvocation> {

    static CachedInvocation ofMethod(final Method method, final List<Object> arguments) {
      return new CachedInvocation(
          method.getName(),
          arguments.stream().map(Object::toString).collect(Collectors.joining("->")));
    }

    @Override
    public int compareTo(final CachedTestResultsExtension.CachedInvocation o) {
      return Comparator.comparing(CachedInvocation::method)
          .thenComparing(CachedInvocation::arguments)
          .compare(this, o);
    }

    String toLine() {
      return "%s,%s".formatted(method(), arguments());
    }

    static CachedInvocation fromLine(final String line) {
      final var parts = line.split(",");
      return new CachedInvocation(parts[0], parts[1]);
    }
  }
}
