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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies (conceptually) the re-entrant callback hypothesis: a synchronous dispatcher that
 * re-enters itself while still on the stack can produce deep nesting; adding a simple guard
 * flattens the depth. This does NOT depend on the real StrictConnPool internals (which may already
 * be guarded in the dependency version), but proves the mechanism.
 */
class ReentrantDispatchHypothesisTest {

  private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

  private int enter() {
    final int d = DEPTH.get() + 1;
    DEPTH.set(d);
    return d;
  }

  private void exit() {
    DEPTH.set(DEPTH.get() - 1);
  }

  @Test
  @DisplayName("Re-entrant dispatcher exhibits depth > 1")
  void reentrantDispatcherShowsStackGrowth() {
    final ReentrantDispatcher d = new ReentrantDispatcher();
    final List<Integer> depths = new ArrayList<>();
    final int tasks = 40;

    // Seed one task that will chain the rest synchronously.
    d.submit(
        new Callback() {
          int produced = 0;

          @Override
          public void run() {
            final int depth = enter();
            try {
              depths.add(depth);
              if (produced < tasks) {
                produced++;
                // Schedule next then immediately re-fire causing nested invocation.
                d.submit(this);
                d.fire();
              }
            } finally {
              exit();
            }
          }
        });

    d.fire();

    assertTrue(depths.size() >= tasks, "All chained tasks should run");
    final int max = depths.stream().mapToInt(i -> i).max().orElse(0);
    final int min = depths.stream().mapToInt(i -> i).min().orElse(0);
    assertTrue(max > 1, "Expected nesting depth > 1 for re-entrant dispatcher");
    assertTrue(max > min, "Expected variation in depth");
  }

  @Test
  @DisplayName("Guarded dispatcher flattens callback depth")
  void guardedDispatcherFlattensDepth() throws InterruptedException {
    final GuardedDispatcher d = new GuardedDispatcher();
    final List<Integer> depths = new ArrayList<>();
    final int tasks = 40;
    final CountDownLatch done = new CountDownLatch(tasks);

    final Callback c =
        new Callback() {
          int produced = 0;

          @Override
          public void run() {
            final int depth = enter();
            try {
              depths.add(depth);
              done.countDown();
              if (produced < tasks - 1) {
                produced++;
                d.submit(this);
                // Intentionally call fire(). Guard prevents new depth.
                d.fire();
              }
            } finally {
              exit();
            }
          }
        };

    d.submit(c);
    d.fire();
    done.await();

    assertEquals(tasks, depths.size(), "All tasks should complete");
    final int max = depths.stream().mapToInt(i -> i).max().orElse(0);
    final int min = depths.stream().mapToInt(i -> i).min().orElse(0);
    assertEquals(1, min, "Flattened dispatch should start at depth 1");
    assertEquals(1, max, "Guarded dispatcher should maintain constant depth 1");
  }

  /** Deliberately re-entrant dispatcher (no guard). */
  static final class ReentrantDispatcher {
    final Deque<Callback> queue = new ArrayDeque<>();

    void submit(final Callback c) {
      queue.addLast(c);
    }

    void fire() { // recursion risk if callbacks call fire() again before returning
      while (!queue.isEmpty()) {
        final Callback c = queue.removeFirst();
        c.run();
      }
    }
  }

  /** Guarded (non-reentrant) dispatcher: nested fire() calls coalesce. */
  static final class GuardedDispatcher {
    final Deque<Callback> queue = new ArrayDeque<>();
    final AtomicBoolean dispatching = new AtomicBoolean();

    void submit(final Callback c) {
      queue.addLast(c);
    }

    void fire() {
      if (!dispatching.compareAndSet(false, true)) {
        return; // outer invocation will drain
      }
      try {
        while (true) {
          final Callback c = queue.pollFirst();
          if (c == null) {
            break;
          }
          c.run();
        }
      } finally {
        dispatching.set(false);
        if (!queue.isEmpty()) { // tail-drain without growing stack
          fire();
        }
      }
    }
  }

  interface Callback {
    void run();
  }
}
