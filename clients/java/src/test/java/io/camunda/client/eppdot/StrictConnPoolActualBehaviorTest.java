/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.eppdot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Uses the real {@link StrictConnPool} class from httpcore5 to observe whether synchronous
 * lease->release cycles produce re-entrant callback piling. The production StackOverflowError
 * hypothesis assumes fireCallbacks() can be re-entered inside a lease callback, deepening the
 * stack. In the guarded implementation (as of httpcore5 5.3.x) the dispatch is flattened, so the
 * logical depth should remain 1.
 *
 * <p>If this test ever reports depth > 1 (maxDepthObserved > 1) in an unmodified environment, that
 * indicates a regression (re-entrancy reintroduced) or a different version without the guard.
 *
 * <p>To simulate piling manually (for experimentation): 1. Copy the library StrictConnPool source
 * into test scope under a different package/class name. 2. Remove/disable any internal
 * non-reentrancy guard (e.g. dispatching flag) and adjust a pooled component to use that version.
 * 3. Re-run this logic expecting maxDepth > 1.
 */
class StrictConnPoolActualBehaviorTest {

  private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

  private static int enter() {
    final int d = DEPTH.get() + 1;
    DEPTH.set(d);
    return d;
  }

  private static void exit() {
    DEPTH.set(DEPTH.get() - 1);
  }

  @Test
  @DisplayName("StrictConnPool guarded dispatch keeps callback depth at 1")
  void shouldNotShowReentrantDepthGrowth() throws InterruptedException {
    final int requests = 250; // enough to reveal nesting if present
    final String route = "r";

    final StrictConnPool<String, DummyClosable> pool =
        new StrictConnPool<>(1, 1, TimeValue.NEG_ONE_MILLISECOND, PoolReusePolicy.LIFO, null, null);

    final CountDownLatch latch = new CountDownLatch(requests);
    final List<Integer> depths = new ArrayList<>(requests);
    final AtomicInteger callbacks = new AtomicInteger();
    final AtomicInteger reuseCount = new AtomicInteger();

    for (int i = 0; i < requests; i++) {
      pool.lease(
          route,
          null,
          Timeout.DISABLED,
          new FutureCallback<PoolEntry<String, DummyClosable>>() {
            @Override
            public void completed(final PoolEntry<String, DummyClosable> entry) {
              final int logicalDepth = enter();
              try {
                depths.add(logicalDepth);
                callbacks.incrementAndGet();
                // Mark reusable so the same entry services queued leases synchronously if allowed.
                reuseCount.incrementAndGet();
                pool.release(entry, true);
              } finally {
                exit();
                latch.countDown();
              }
            }

            @Override
            public void failed(final Exception ex) {
              latch.countDown();
              throw new AssertionError("Unexpected failure leasing pool entry", ex);
            }

            @Override
            public void cancelled() {
              latch.countDown();
              throw new AssertionError("Unexpected cancellation");
            }
          });
    }

    final boolean finished = latch.await(10, TimeUnit.SECONDS);
    assertThat(finished).as("All callbacks should finish in time").isTrue();
    assertThat(callbacks.get()).isEqualTo(requests);
    assertThat(depths).hasSize(requests);

    final int maxDepth = depths.stream().mapToInt(i -> i).max().orElse(0);
    final int minDepth = depths.stream().mapToInt(i -> i).min().orElse(0);

    // EXPECTATION for guarded implementation: depth never exceeds 1.
    assertThat(minDepth).isEqualTo(1);
    assertThat(maxDepth)
        .as(
            () ->
                "Expected flattened callback dispatch (no re-entrancy). Observed maxDepth="
                    + maxDepth
                    + ", minDepth="
                    + minDepth
                    + ". If >1, investigate potential re-entrant StrictConnPool.fireCallbacks in current version.")
        .isEqualTo(1);
  }

  static final class DummyClosable implements org.apache.hc.core5.io.ModalCloseable {
    @Override
    public void close() {}

    @Override
    public void close(final org.apache.hc.core5.io.CloseMode closeMode) {}
  }
}
