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
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConcurrentSecretStoreTest {

  private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
  }

  @Test
  void shouldFanOutAOneByOneStoreAndRunChunksConcurrentlyUpToThePermitCount() {
    // given a store synchronized on a barrier sized to the permit count: resolve() cannot return
    // until exactly that many chunks are inside it at once, so the bound is proven by
    // construction instead of inferred from timing (a serial, or over-concurrent, resolution
    // would either hang past the barrier's timeout or trip it with the wrong number of parties).
    // Twice as many chunks as permits, so the bound has to hold across two full rounds, not just
    // one lucky batch.
    final var permits = 4;
    final var names = namesUpTo(permits * 2);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 1;
    delegate.barrier = new CyclicBarrier(permits);
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(permits, true));

    // when
    final var results = store.resolve(names);

    // then every name still resolves...
    names.forEach(name -> assertThat(results.get(name)).isEqualTo(new Resolved(name + "-value")));
    // ...and exactly `permits` chunks were in flight at once: a regression that dispatched all
    // chunks at once, or serialized them, would fail this exact-equality check rather than the
    // weaker "at least one overlap" a `> 1` assertion allows to pass
    assertThat(delegate.maxObserved.get()).isEqualTo(permits);
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
  void shouldNotFanOutWhenNamesExactlyFillOneCall() {
    // given exactly as many names as one call covers: the `callSize >= names.size()` short
    // circuit from the equal side, not just the clearly-larger side the other single-call tests
    // below exercise
    final var names = namesUpTo(4);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 4;
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(8, true));

    // when
    store.resolve(names);

    // then the whole set still reaches the delegate in a single call, not two (one full chunk and
    // one spuriously empty one)
    assertThat(delegate.resolveCalls).containsExactly(names);
  }

  @Test
  void shouldFanOutIntoExactlyTwoChunks() {
    // given one more name than a single call covers: the minimal possible fan-out, distinct from
    // the larger chunk counts the other chunking test above exercises
    final var names = namesUpTo(5);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 4;
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(8, true));

    // when
    final var results = store.resolve(names);

    // then every name resolves, split across exactly two chunks of at most 4 names each
    names.forEach(name -> assertThat(results.get(name)).isEqualTo(new Resolved(name + "-value")));
    assertThat(delegate.resolveCalls).hasSize(2);
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
    // given one of three chunks hits a transient store failure, held back with a latch until the
    // other two have actually resolved at the delegate: without this, the failing chunk could
    // otherwise set the shared flag before either sibling even starts, and the assertion below
    // would no longer prove anything about discarded successes
    final var names = namesUpTo(9);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 3;
    delegate.unavailableNames.add("name-5");
    delegate.releaseFailureAfter = new CountDownLatch(2);
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(4, true));

    // when / then: the whole call fails, exactly as an unwrapped one-by-one store failing
    // mid-batch would; the refs from the succeeded chunks stay pending and are retried next cycle
    assertThatThrownBy(() -> store.resolve(names))
        .isInstanceOf(SecretStoreUnavailableException.class);

    // then the delegate did resolve the two succeeding chunks, proving their results existed and
    // were discarded by the failing call rather than never having been attempted
    assertThat(delegate.resolveCalls).hasSize(3);
    assertThat(delegate.resolveCalls.stream().flatMap(Set::stream))
        .containsExactlyInAnyOrderElementsOf(names);
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
  void shouldSurfaceARealChunkFailureRatherThanASkippedSiblingAsThePrimaryException() {
    // given six chunks (namesPerCall=1) all naming a secret the store treats as unavailable, with
    // only two permits: at most the first two chunks to acquire one can ever call the store for
    // real (nothing has set the flag yet when they start), and each sets the shared flag before
    // releasing its permit, so every one of the remaining four chunks is guaranteed to observe the
    // flag and skip once it acquires. Which chunks win that initial race is left to the scheduler,
    // so a real failure can land anywhere in chunk order, not only first.
    final var names =
        new LinkedHashSet<>(List.of("fail-a", "fail-b", "fail-c", "fail-d", "fail-e", "fail-f"));
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 1;
    delegate.unavailableNames.addAll(names);
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(2, true));

    // when
    final var thrown = catchThrowable(() -> store.resolve(names));

    // then the primary exception is the real backend failure, never the synthetic "Skipped: ..."
    // message a losing chunk throws, regardless of which chunk actually reached the store...
    assertThat(thrown).isInstanceOf(SecretStoreUnavailableException.class);
    assertThat(thrown.getMessage()).startsWith("store unavailable for");
    // ...and every skipped sibling is still visible, attached as suppressed rather than dropped
    assertThat(thrown.getSuppressed()).isNotEmpty();
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

  @Test
  void shouldRejectADelegateReportingLessThanOneNamePerCall() {
    // a namesPerCall below 1 cannot be split into chunks (chunk() would either loop forever on 0
    // or throw on a negative subList bound), so a store reporting it is rejected outright instead
    // of hanging or throwing an unrelated exception on the first resolution
    final var names = namesUpTo(4);
    final var delegate = new FakeOneByOneStore();
    delegate.namesPerCall = 0;
    final var store = new ConcurrentSecretStore(delegate, pool, new Semaphore(4, true));

    // when / then
    assertThatIllegalArgumentException().isThrownBy(() -> store.resolve(names));
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
    // stands in for a real backend's round trip: rather than sleeping, resolve() blocks here until
    // as many chunks as the barrier has parties are inside it at once, so a fan-out test asserts
    // the exact peak concurrency deterministically instead of inferring "some overlap happened"
    // from wall-clock timing
    private CyclicBarrier barrier;
    private final Set<String> failedNames = new LinkedHashSet<>();
    private final Set<String> unavailableNames = new LinkedHashSet<>();
    private final Set<String> erroringNames = new LinkedHashSet<>();
    // held by a chunk about to fail with SecretStoreUnavailableException, released once by every
    // chunk that resolves successfully: lets a test force the succeeding chunks to actually
    // complete at the delegate before the failing chunk sets the shared flag, so the ordering a
    // test wants to observe does not depend on how the pool happens to schedule the chunks
    private CountDownLatch releaseFailureAfter;
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
          awaitLatch(releaseFailureAfter);
          throw new SecretStoreUnavailableException("store unavailable for " + names);
        }
        awaitBarrier();
        final var result = names.stream().collect(toMap(name -> name, this::resultFor));
        if (releaseFailureAfter != null) {
          releaseFailureAfter.countDown();
        }
        return result;
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

    private void awaitBarrier() {
      if (barrier == null) {
        return;
      }
      try {
        barrier.await(5, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SecretStoreUnavailableException(
            "interrupted awaiting the concurrency barrier", e);
      } catch (final BrokenBarrierException | TimeoutException e) {
        // fewer parties reached the barrier than expected within the timeout: fail loudly with a
        // clear cause instead of hanging the remaining chunks (and the test run) indefinitely
        throw new AssertionError(
            "Expected "
                + barrier.getParties()
                + " chunks in flight at once, but the barrier never tripped within the timeout",
            e);
      }
    }

    private static void awaitLatch(final CountDownLatch latch) {
      if (latch == null) {
        return;
      }
      try {
        if (!latch.await(5, TimeUnit.SECONDS)) {
          throw new AssertionError(
              "Expected the sibling chunks to resolve within the timeout before this chunk fails");
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SecretStoreUnavailableException("interrupted awaiting sibling chunks", e);
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
