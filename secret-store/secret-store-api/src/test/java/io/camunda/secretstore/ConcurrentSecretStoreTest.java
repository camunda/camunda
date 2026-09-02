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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConcurrentSecretStoreTest {

  private static final Duration PER_NAME_DELAY = Duration.ofMillis(50);

  private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
  }

  @Test
  void shouldFanOutAOneByOneStoreAndRunChunksConcurrently() {
    // given a store that pays PER_NAME_DELAY per name inside one resolve() call, exactly as a
    // real one-by-one cloud store would, with more names than the permit count so at least two
    // chunks must run at once for every name to resolve inside the sleep window
    final var names = namesUpTo(16);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 1;
    delegate.perNameDelay = PER_NAME_DELAY;
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(8, true));

    // when
    final var results = store.resolve(names);

    // then every name still resolves...
    names.forEach(name -> assertThat(results.get(name)).isEqualTo(new Resolved(name + "-value")));
    // ...and more than one chunk was in flight at the same time: a serial (unwrapped) resolution
    // could never observe more than 1, regardless of how long each chunk takes
    assertThat(delegate.maxObserved.get()).isGreaterThan(1);
  }

  @Test
  void shouldChunkByTheDelegatesNamesPerCallRatherThanByMaxConcurrency() {
    // given a store whose namesPerCall mirrors AWS's batched mode (several names covered by one
    // sequential call), with more names than one call covers but fewer chunks than the permit
    // count allows: chunk count must come from namesPerCall, not be forced to match maxConcurrency
    final var names = namesUpTo(17);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 4;
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(8, true));

    // when
    final var results = store.resolve(names);

    // then every name resolves...
    names.forEach(name -> assertThat(results.get(name)).isEqualTo(new Resolved(name + "-value")));
    // ...via exactly ceil(17/4) = 5 calls of at most 4 names each, not 8 (maxConcurrency) calls
    // with some chunks starved and others left idle
    assertThat(delegate.resolveCalls).hasSize(5);
    delegate.resolveCalls.forEach(call -> assertThat(call.size()).isLessThanOrEqualTo(4));
  }

  @Test
  void shouldNotFanOutWhenDelegateCoversWholeRequestInOneCall() {
    // given a store that already covers many names per call (e.g. a container or batched store
    // sized to the whole request): the default namesPerCall, left unset
    final var names = namesUpTo(16);
    final var delegate = new FakeOneByOneStore();
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(8, true));

    // when
    store.resolve(names);

    // then the whole set reaches the delegate in a single call; chunking it would only cost the
    // backend more calls for a store that already resolves the whole request in one
    assertThat(delegate.resolveCalls).containsExactly(names);
  }

  @Test
  void shouldNotFanOutWhenMaxConcurrencyIsOne() {
    // given
    final var names = namesUpTo(16);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 1;
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(1, true));

    // when
    store.resolve(names);

    // then concurrency 1 is today's behavior: exactly one call, no thread hop, regardless of
    // what the delegate's namesPerCall says
    assertThat(delegate.resolveCalls).containsExactly(names);
  }

  @Test
  void shouldNotFanOutASingleName() {
    // given
    final var names = Set.of("only-one");
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 1;
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(8, true));

    // when
    store.resolve(names);

    // then a single name has nothing to gain from a thread hop
    assertThat(delegate.resolveCalls).containsExactly(names);
  }

  @Test
  void shouldPreserveEveryNamesResultAcrossChunks() {
    // given a mix of resolved and permanently failed names, split across chunks of 3
    final var names = namesUpTo(9);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 3;
    delegate.failedNames.add("name-3");
    delegate.failedNames.add("name-7");
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(4, true));

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
    delegate.namesPerCall = 3;
    delegate.unavailableNames.add("name-5");
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(4, true));

    // when / then: the whole call fails, exactly as an unwrapped one-by-one store failing
    // mid-batch would; the refs from the succeeded chunks stay pending and are retried next cycle
    assertThatThrownBy(() -> store.resolve(names))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldSkipAChunkStillWaitingForAPermitOnceASiblingChunkFails() {
    // given three chunks (namesPerCall=1) all naming a secret the store treats as unavailable, but
    // only two permits: the third chunk cannot reach the store until a failing chunk frees the
    // permit it is queued for, and by then the store is known to be down, so it must see the flag
    // and skip its own call rather than dispatch into a backend already failing. All three names
    // fail identically, so the outcome does not depend on the order the semaphore admits them in.
    final var names = new LinkedHashSet<>(List.of("fail-a", "fail-b", "fail-c"));
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 1;
    delegate.unavailableNames.addAll(names);
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(2, true));

    // when / then
    assertThatThrownBy(() -> store.resolve(names))
        .isInstanceOf(SecretStoreUnavailableException.class);

    // then the queued chunk never reached the delegate: without the shared flag all three would
    // have called the store, one after another as permits freed up
    assertThat(delegate.resolveCalls).hasSizeLessThan(3);
  }

  @Test
  void shouldRethrowAnErrorFromAChunkRatherThanReportItAsAStoreFailure() {
    // given a chunk whose store call fails with an Error rather than an exception
    final var names = namesUpTo(4);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 1;
    delegate.erroringNames.add("name-2");
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(2, true));

    // when / then: an Error is not one of the outcomes a store models, so reporting it as
    // SecretStoreUnavailableException would put the scheduler into a retry ladder over a JVM
    // failure it can neither retry away nor see
    assertThatThrownBy(() -> store.resolve(names)).isInstanceOf(FakeStoreError.class);
  }

  @Test
  void shouldReportARejectedDispatchAsAStoreFailure() {
    // given a pool that is already shut down, as it is once startup rollback has closed it
    final var names = namesUpTo(4);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 1;
    final var shutDownPool = Executors.newVirtualThreadPerTaskExecutor();
    shutDownPool.shutdownNow();
    final var store = new ConcurrentSecretStore(delegate, shutDownPool, new Semaphore(2, true));

    // when / then: a dispatch the pool refuses leaves the store unread, which is what
    // SecretStoreUnavailableException means — the scheduler retries that and only logs anything
    // else as an unexpected engine error
    assertThatThrownBy(() -> store.resolve(names))
        .isInstanceOf(SecretStoreUnavailableException.class);
    assertThat(delegate.resolveCalls).isEmpty();
  }

  @Test
  void shouldDelegateListCloseAndIs() {
    // given
    final var delegate = new FakeOneByOneStore();
    delegate.listValue = List.of("a", "b");
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(8, true));

    // when / then: identified by the store it wraps, exactly as CachingSecretStore is
    assertThat(store.list()).containsExactly("a", "b");
    assertThat(store.is(FakeOneByOneStore.class)).isTrue();
    store.close();
    assertThat(delegate.closed).isTrue();
  }

  @Test
  void shouldRejectASemaphoreCarryingNoPermits() {
    // a semaphore no chunk can ever acquire from would hang every fan-out rather than resolve
    // anything, so it is rejected where it is configured instead of at the first resolution
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ConcurrentSecretStore(new FakeOneByOneStore(), pool, new Semaphore(0, true)));
  }

  private static Set<String> namesUpTo(final int count) {
    final var names = new LinkedHashSet<String>();
    IntStream.range(0, count).forEach(i -> names.add("name-" + i));
    return names;
  }

  private static final class FakeOneByOneStore implements SecretStore {

    // a thread-safe list rather than a lock around the whole method: locking resolve() itself
    // would serialize every concurrently-dispatched chunk on this fake's own monitor, hiding the
    // very concurrency the fan-out tests exist to prove
    private final List<Set<String>> resolveCalls = new CopyOnWriteArrayList<>();
    private int namesPerCall = Integer.MAX_VALUE;
    private Duration perNameDelay = Duration.ZERO;
    private final Set<String> failedNames = new LinkedHashSet<>();
    private final Set<String> unavailableNames = new LinkedHashSet<>();
    private final Set<String> erroringNames = new LinkedHashSet<>();
    private List<String> listValue = List.of();
    private volatile boolean closed;

    // tracks how many resolve() calls are in flight at once, so a fan-out test can assert on
    // observed concurrency directly instead of on wall-clock duration
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxObserved = new AtomicInteger();

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      resolveCalls.add(Set.copyOf(names));
      final var now = inFlight.incrementAndGet();
      maxObserved.accumulateAndGet(now, Math::max);
      try {
        if (!erroringNames.isEmpty() && !Collections.disjoint(names, erroringNames)) {
          throw new FakeStoreError("store failed irrecoverably for " + names);
        }
        if (!unavailableNames.isEmpty() && !Collections.disjoint(names, unavailableNames)) {
          throw new SecretStoreUnavailableException("store unavailable for " + names);
        }
        sleep(perNameDelay.multipliedBy(names.size()));
        return names.stream().collect(toMap(name -> name, this::resultFor));
      } finally {
        inFlight.decrementAndGet();
      }
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
    public int namesPerCall() {
      return namesPerCall;
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

  /**
   * Stands in for the Errors a real store call can raise (an {@code OutOfMemoryError} from an
   * oversized response, a {@code StackOverflowError} from a parser). A dedicated subclass rather
   * than one of those, so a test asserting on it cannot be satisfied by an Error the JVM raised on
   * its own.
   */
  private static final class FakeStoreError extends Error {
    private FakeStoreError(final String message) {
      super(message);
    }
  }
}
