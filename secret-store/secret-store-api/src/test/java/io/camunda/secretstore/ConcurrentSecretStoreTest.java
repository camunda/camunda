/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConcurrentSecretStoreTest {

  private static final Duration PER_NAME_DELAY = Duration.ofMillis(50);

  private final ExecutorService pool = Executors.newFixedThreadPool(8);

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
  }

  @Test
  void shouldResolveAOneByOneStoreConcurrentlyAndCollapseWallClock() {
    // given a store that pays PER_NAME_DELAY per name inside one resolve() call, exactly as a real
    // one-by-one cloud store would
    final var names = namesUpTo(16);
    final var delegate = new FakeOneByOneStore();
    delegate.resolvesOneByOne = true;
    delegate.perNameDelay = PER_NAME_DELAY;
    final var store = new ConcurrentSecretStore(delegate, pool, 8);

    // when
    final var start = System.nanoTime();
    final var results = store.resolve(names);
    final var elapsed = Duration.ofNanos(System.nanoTime() - start);

    // then every name still resolves...
    names.forEach(name -> assertThat(results.get(name)).isEqualTo(new Resolved(name + "-value")));
    // ...but 16 names at concurrency 8 cost 2 sequential rounds, not 16, well under the serial
    // cost
    assertThat(elapsed).isLessThan(PER_NAME_DELAY.multipliedBy(16).dividedBy(2));
  }

  @Test
  void shouldNotChunkWhenDelegateDoesNotResolveOneByOne() {
    // given a store that already covers many names per call (e.g. a container or batched store)
    final var names = namesUpTo(16);
    final var delegate = new FakeOneByOneStore();
    delegate.resolvesOneByOne = false;
    final var store = new ConcurrentSecretStore(delegate, pool, 8);

    // when
    store.resolve(names);

    // then the whole set reaches the delegate in a single call; chunking it would only cost the
    // backend more calls for a store that already resolves several names per call
    assertThat(delegate.resolveCalls).containsExactly(names);
  }

  @Test
  void shouldNotChunkWhenMaxConcurrencyIsOne() {
    // given
    final var names = namesUpTo(16);
    final var delegate = new FakeOneByOneStore();
    delegate.resolvesOneByOne = true;
    final var store = new ConcurrentSecretStore(delegate, pool, 1);

    // when
    store.resolve(names);

    // then concurrency 1 is today's behavior: exactly one call, no thread hop
    assertThat(delegate.resolveCalls).containsExactly(names);
  }

  @Test
  void shouldNotChunkASingleName() {
    // given
    final var names = Set.of("only-one");
    final var delegate = new FakeOneByOneStore();
    delegate.resolvesOneByOne = true;
    final var store = new ConcurrentSecretStore(delegate, pool, 8);

    // when
    store.resolve(names);

    // then a single name has nothing to gain from a thread hop
    assertThat(delegate.resolveCalls).containsExactly(names);
  }

  @Test
  void shouldPreserveEveryNamesResultAcrossChunks() {
    // given a mix of resolved and permanently failed names, split across chunks
    final var names = namesUpTo(9);
    final var delegate = new FakeOneByOneStore();
    delegate.resolvesOneByOne = true;
    delegate.failedNames.add("name-3");
    delegate.failedNames.add("name-7");
    final var store = new ConcurrentSecretStore(delegate, pool, 4);

    // when
    final var results = store.resolve(names);

    // then every name is answered, with no cross-chunk mixup
    assertThat(results).hasSameSizeAs(names);
    names.forEach(
        name -> {
          if (delegate.failedNames.contains(name)) {
            assertThat(results.get(name)).isInstanceOf(Failed.class);
          } else {
            assertThat(results.get(name)).isEqualTo(new Resolved(name + "-value"));
          }
        });
  }

  @Test
  void shouldPropagateStoreUnavailableFromAnyChunk() {
    // given one of several chunks hits a transient store failure
    final var names = namesUpTo(9);
    final var delegate = new FakeOneByOneStore();
    delegate.resolvesOneByOne = true;
    delegate.unavailableNames.add("name-5");
    final var store = new ConcurrentSecretStore(delegate, pool, 4);

    // when / then: the whole call fails, exactly as an unwrapped one-by-one store failing
    // mid-batch would; the refs from the succeeded chunks stay pending and are retried next cycle
    assertThatThrownBy(() -> store.resolve(names))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldDelegateListCloseAndIs() {
    // given
    final var delegate = new FakeOneByOneStore();
    delegate.listValue = List.of("a", "b");
    final var store = new ConcurrentSecretStore(delegate, pool, 8);

    // when / then: identified by the store it wraps, exactly as CachingSecretStore is
    assertThat(store.list()).containsExactly("a", "b");
    assertThat(store.is(FakeOneByOneStore.class)).isTrue();
    store.close();
    assertThat(delegate.closed).isTrue();
  }

  @Test
  void shouldRejectNonPositiveMaxConcurrency() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConcurrentSecretStore(new FakeOneByOneStore(), pool, 0));
  }

  private static Set<String> namesUpTo(final int count) {
    final var names = new LinkedHashSet<String>();
    IntStream.range(0, count).forEach(i -> names.add("name-" + i));
    return names;
  }

  private static final class FakeOneByOneStore implements SecretStore {

    // a thread-safe list rather than a lock around the whole method: locking resolve() itself
    // would serialize every concurrently-dispatched chunk on this fake's own monitor, hiding the
    // very concurrency the timing test exists to prove
    private final List<Set<String>> resolveCalls =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private boolean resolvesOneByOne;
    private Duration perNameDelay = Duration.ZERO;
    private final Set<String> failedNames = new LinkedHashSet<>();
    private final Set<String> unavailableNames = new LinkedHashSet<>();
    private List<String> listValue = List.of();
    private volatile boolean closed;

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      resolveCalls.add(Set.copyOf(names));
      if (!unavailableNames.isEmpty() && !java.util.Collections.disjoint(names, unavailableNames)) {
        throw new SecretStoreUnavailableException("store unavailable for " + names);
      }
      sleep(perNameDelay.multipliedBy(names.size()));
      return names.stream().collect(toMap(name -> name, this::resultFor));
    }

    private SecretResolutionResult resultFor(final String name) {
      if (failedNames.contains(name)) {
        return new Failed(SecretErrorCode.NOT_FOUND, "failed: " + name, null);
      }
      return new Resolved(name + "-value");
    }

    @Override
    public List<String> list() {
      return listValue;
    }

    @Override
    public boolean resolvesOneByOne() {
      return resolvesOneByOne;
    }

    @Override
    public void close() {
      closed = true;
    }

    private static void sleep(final Duration duration) {
      if (duration.isZero()) {
        return;
      }
      try {
        Thread.sleep(duration.toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SecretStoreUnavailableException("interrupted", e);
      }
    }
  }
}
