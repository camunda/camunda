/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.scheduler;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Spike comparing how a future could store its callbacks, in isolation from the rest of {@code
 * CompletableActorFuture}. Each operation creates the storage, registers {@code callbacks}
 * callbacks and drains them once. The callbacks are created up front so that only the storage is
 * measured. Run with {@code -prof gc} and compare {@code gc.alloc.rate.norm}.
 *
 * <ul>
 *   <li>{@code inlineFirstThenStack}: first callback inline, others on an intrusive lock-free stack
 *   <li>{@code inlineFirstThenLazyQueue}: first callback inline, others in a lazily created Agrona
 *       queue
 *   <li>{@code inlineFirstThenLazyJdkQueue}: first callback inline, others in a lazily created
 *       {@link ConcurrentLinkedQueue}
 *   <li>{@code inlineFirstThenCowArray}: first callback inline, others in an immutable array that
 *       is replaced by a copy one slot longer on every add
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
// Agrona's queue uses UnsafeApi, which needs access to jdk.internal.misc
@Fork(value = 2, jvmArgsAppend = "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
public class CallbackStorageSpikeBenchmark {

  @State(Scope.Thread)
  public static class Callbacks {
    @Param({"0", "1", "2", "3", "10"})
    int count;

    long sink;
    BiConsumer<Long, Throwable> callback;

    @Setup
    public void setup() {
      callback = (value, error) -> sink += value;
    }
  }

  private static final Long VALUE = 42L;

  @Benchmark
  public void inlineFirstThenStack(final Callbacks callbacks, final Blackhole blackhole) {
    final var storage = new InlineFirstThenStack();
    for (int i = 0; i < callbacks.count; i++) {
      storage.add(callbacks.callback);
    }
    storage.drain(VALUE);
    blackhole.consume(storage);
  }

  @Benchmark
  public void inlineFirstThenLazyQueue(final Callbacks callbacks, final Blackhole blackhole) {
    final var storage = new InlineFirstThenLazyQueue();
    for (int i = 0; i < callbacks.count; i++) {
      storage.add(callbacks.callback);
    }
    storage.drain(VALUE);
    blackhole.consume(storage);
  }

  @Benchmark
  public void inlineFirstThenLazyJdkQueue(final Callbacks callbacks, final Blackhole blackhole) {
    final var storage = new InlineFirstThenLazyJdkQueue();
    for (int i = 0; i < callbacks.count; i++) {
      storage.add(callbacks.callback);
    }
    storage.drain(VALUE);
    blackhole.consume(storage);
  }

  @Benchmark
  public void inlineFirstThenCowArray(final Callbacks callbacks, final Blackhole blackhole) {
    final var storage = new InlineFirstThenCowArray();
    for (int i = 0; i < callbacks.count; i++) {
      storage.add(callbacks.callback);
    }
    storage.drain(VALUE);
    blackhole.consume(storage);
  }

  private static final class InlineFirstThenStack {
    private static final VarHandle FIRST;
    private static final VarHandle MORE;

    static {
      try {
        final var lookup = MethodHandles.lookup();
        FIRST = lookup.findVarHandle(InlineFirstThenStack.class, "first", BiConsumer.class);
        MORE = lookup.findVarHandle(InlineFirstThenStack.class, "more", Node.class);
      } catch (final ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    private volatile BiConsumer<Long, Throwable> first;
    private volatile Node more;

    void add(final BiConsumer<Long, Throwable> callback) {
      if (FIRST.compareAndSet(this, (BiConsumer<Long, Throwable>) null, callback)) {
        return;
      }
      final var node = new Node(callback);
      Node head;
      do {
        head = more;
        node.next = head;
      } while (!MORE.compareAndSet(this, head, node));
    }

    @SuppressWarnings("unchecked")
    void drain(final Long value) {
      while (first != null || more != null) {
        final var firstCallback =
            first != null
                ? (BiConsumer<Long, Throwable>)
                    FIRST.getAndSet(this, (BiConsumer<Long, Throwable>) null)
                : null;
        final var pending = more != null ? (Node) MORE.getAndSet(this, (Node) null) : null;
        if (firstCallback != null) {
          firstCallback.accept(value, null);
        }
        Node reversed = null;
        for (var node = pending; node != null; ) {
          final var next = node.next;
          node.next = reversed;
          reversed = node;
          node = next;
        }
        for (var node = reversed; node != null; node = node.next) {
          node.callback.accept(value, null);
        }
      }
    }

    private static final class Node {
      private final BiConsumer<Long, Throwable> callback;
      private Node next;

      private Node(final BiConsumer<Long, Throwable> callback) {
        this.callback = callback;
      }
    }
  }

  private static final class InlineFirstThenLazyQueue {
    private static final VarHandle FIRST;
    private static final VarHandle MORE;

    static {
      try {
        final var lookup = MethodHandles.lookup();
        FIRST = lookup.findVarHandle(InlineFirstThenLazyQueue.class, "first", BiConsumer.class);
        MORE =
            lookup.findVarHandle(
                InlineFirstThenLazyQueue.class, "more", ManyToOneConcurrentLinkedQueue.class);
      } catch (final ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    private volatile BiConsumer<Long, Throwable> first;
    private volatile ManyToOneConcurrentLinkedQueue<BiConsumer<Long, Throwable>> more;

    void add(final BiConsumer<Long, Throwable> callback) {
      if (FIRST.compareAndSet(this, (BiConsumer<Long, Throwable>) null, callback)) {
        return;
      }
      var queue = more;
      if (queue == null) {
        final var fresh = new ManyToOneConcurrentLinkedQueue<BiConsumer<Long, Throwable>>();
        queue = MORE.compareAndSet(this, null, fresh) ? fresh : more;
      }
      queue.offer(callback);
    }

    @SuppressWarnings("unchecked")
    void drain(final Long value) {
      // loop until both are empty, callbacks may be added while draining
      while (true) {
        final var queue = more;
        if (first == null && (queue == null || queue.isEmpty())) {
          return;
        }
        final var firstCallback =
            first != null
                ? (BiConsumer<Long, Throwable>)
                    FIRST.getAndSet(this, (BiConsumer<Long, Throwable>) null)
                : null;
        if (firstCallback != null) {
          firstCallback.accept(value, null);
        }
        if (queue != null) {
          while (!queue.isEmpty()) {
            final var callback = queue.poll();
            if (callback != null) {
              callback.accept(value, null);
            }
          }
        }
      }
    }
  }

  private static final class InlineFirstThenLazyJdkQueue {
    private static final VarHandle FIRST;
    private static final VarHandle MORE;

    static {
      try {
        final var lookup = MethodHandles.lookup();
        FIRST = lookup.findVarHandle(InlineFirstThenLazyJdkQueue.class, "first", BiConsumer.class);
        MORE =
            lookup.findVarHandle(
                InlineFirstThenLazyJdkQueue.class, "more", ConcurrentLinkedQueue.class);
      } catch (final ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    private volatile BiConsumer<Long, Throwable> first;
    private volatile ConcurrentLinkedQueue<BiConsumer<Long, Throwable>> more;

    void add(final BiConsumer<Long, Throwable> callback) {
      if (FIRST.compareAndSet(this, (BiConsumer<Long, Throwable>) null, callback)) {
        return;
      }
      var queue = more;
      if (queue == null) {
        final var fresh = new ConcurrentLinkedQueue<BiConsumer<Long, Throwable>>();
        queue = MORE.compareAndSet(this, null, fresh) ? fresh : more;
      }
      queue.offer(callback);
    }

    @SuppressWarnings("unchecked")
    void drain(final Long value) {
      // loop until both are empty, callbacks may be added while draining
      while (true) {
        final var queue = more;
        if (first == null && (queue == null || queue.isEmpty())) {
          return;
        }
        final var firstCallback =
            first != null
                ? (BiConsumer<Long, Throwable>)
                    FIRST.getAndSet(this, (BiConsumer<Long, Throwable>) null)
                : null;
        if (firstCallback != null) {
          firstCallback.accept(value, null);
        }
        if (queue != null) {
          BiConsumer<Long, Throwable> callback;
          while ((callback = queue.poll()) != null) {
            callback.accept(value, null);
          }
        }
      }
    }
  }

  private static final class InlineFirstThenCowArray {
    private static final VarHandle FIRST;
    private static final VarHandle MORE;

    static {
      try {
        final var lookup = MethodHandles.lookup();
        FIRST = lookup.findVarHandle(InlineFirstThenCowArray.class, "first", BiConsumer.class);
        MORE = lookup.findVarHandle(InlineFirstThenCowArray.class, "more", BiConsumer[].class);
      } catch (final ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    private volatile BiConsumer<Long, Throwable> first;
    private volatile BiConsumer<Long, Throwable>[] more;

    @SuppressWarnings("unchecked")
    void add(final BiConsumer<Long, Throwable> callback) {
      if (FIRST.compareAndSet(this, (BiConsumer<Long, Throwable>) null, callback)) {
        return;
      }
      BiConsumer<Long, Throwable>[] current;
      BiConsumer<Long, Throwable>[] next;
      do {
        current = more;
        if (current == null) {
          next = new BiConsumer[] {callback};
        } else {
          next = Arrays.copyOf(current, current.length + 1);
          next[current.length] = callback;
        }
      } while (!MORE.compareAndSet(this, current, next));
    }

    @SuppressWarnings("unchecked")
    void drain(final Long value) {
      // loop until both are empty, callbacks may be added while draining
      while (first != null || more != null) {
        final var firstCallback =
            first != null
                ? (BiConsumer<Long, Throwable>)
                    FIRST.getAndSet(this, (BiConsumer<Long, Throwable>) null)
                : null;
        final var pending =
            more != null ? (BiConsumer<Long, Throwable>[]) MORE.getAndSet(this, null) : null;
        if (firstCallback != null) {
          firstCallback.accept(value, null);
        }
        if (pending != null) {
          for (final var callback : pending) {
            callback.accept(value, null);
          }
        }
      }
    }
  }
}
