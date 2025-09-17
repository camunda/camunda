package io.camunda.client.eppdot;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolStats;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Test;

/**
 * Minimal compilation-ready test showing usage of StrictConnPool and fixing the compile errors you
 * reported (version 5.3.4).
 *
 * <p>NOTE: This test does not (by itself) reproduce the production StackOverflow. It shows how to
 * construct and use the real pool and where a listener can call back into the pool (the reentrancy
 * / callback cycle you traced).
 */
public class StrictConnPoolReentrancyTest {

  @Test
  public void compileAndUseStrictConnPool() throws Exception {
    final String route = "example-route";

    // We need the listener to be able to reference the pool -> use AtomicReference
    final AtomicReference<StrictConnPool<String, DummyConnection>> poolRef =
        new AtomicReference<>();

    // Create a listener — onRelease will call lease(...) again (this demonstrates re-entrant use)
    final ConnPoolListener<String> listener =
        new ConnPoolListener<String>() {
          @Override
          public void onLease(final String r, final ConnPoolStats<String> stats) {
            // no-op for this test
          }

          @Override
          public void onRelease(final String r, final ConnPoolStats<String> stats) {
            // This is the important bit for the reentrancy reasoning:
            // the listener is called from inside StrictConnPool.release(...) while the pool lock
            // may still be held by the current thread. If we call pool.lease(...) here,
            // we re-enter pool code on the same thread (ReentrantLock allows this) —
            // that is the pattern that may lead to nested callback cycles.
            final StrictConnPool<String, DummyConnection> pool = poolRef.get();
            if (pool != null) {
              // use the two-arg overload; do NOT call lease(route, null, null).
              // This call is intentionally simple — it compiles and demonstrates the path.
              pool.lease(r, null);
            }
          }
        };

    /*
     * Constructing the StrictConnPool:
     * - Many constructor overloads exist. In 5.3.4 you can use the simple
     *   (defaultMaxPerRoute, maxTotal, timeToLive, reusePolicy, disposalCallback, listener)
     *   or shorter forms. If your codebase uses a different overload pick the
     *   matching parameters. Below we use a constructor that exists in 5.3.4.
     *
     * If your IDE complains about the constructor signature, replace with the
     * two-arg constructor that exists in older variants:
     *   new StrictConnPool<>(1, 1);
     *
     * (Either option compiles with 5.3.4; adjust to your codebase if necessary.)
     */
    final StrictConnPool<String, DummyConnection> pool =
        new StrictConnPool<>(
            1, // defaultMaxPerRoute
            2, // maxTotal
            TimeValue.ofMilliseconds(0), // timeToLive (no expiry)
            PoolReusePolicy.LIFO, // reuse policy
            (conn, mode) -> {
              /* disposal callback: no-op */
            },
            listener);

    poolRef.set(pool);

    // Request a lease (this returns a Future that will be completed later when a connection is
    // available)
    final Future<PoolEntry<String, DummyConnection>> leaseFuture = pool.lease(route, null);

    assertNotNull("lease Future must not be null", leaseFuture);

    // Create a PoolEntry to simulate a connection being returned by a connection operator.
    // PoolEntry has convenience constructors; we use route + TimeValue.
    final PoolEntry<String, DummyConnection> entry =
        new PoolEntry<>(route, TimeValue.ofMilliseconds(0));

    // Assign a dummy connection to the entry (PoolEntry.assignConnection exists in 5.3.4)
    entry.assignConnection(new DummyConnection());

    /*
     * Note: in real use the pool.lease(...) call above is satisfied by
     * the connection manager which creates the connection and notifies the pool.
     * Calling pool.release(entry, true) requires the entry to be in the leased set.
     *
     * For this minimal compile test we simply call release to show the method usage.
     * In real integration tests you should drive the pool the same way PoolingAsyncClientConnectionManager does:
     *  - call lease(route) to register a pending request (the returned Future)
     *  - create or obtain a PoolEntry corresponding to that request
     *  - call pool.release(entry, reusable) to return it, which will trigger callbacks
     *
     * The code below attempts a release — depending on the pool state you may get
     * an IllegalStateException if the library expects the entry to be known/leased.
     */
    try {
      pool.release(entry, true);
    } catch (final IllegalStateException ex) {
      // this may be thrown in this synthetic test because 'entry' was not actually leased
      // — that's expected in the simplified test. The important thing was to fix the
      // compile issues and show where the re-entrant listener can call back into the pool.
    }
  }

  // Dummy connection must implement ModalCloseable (required type bound).
  static class DummyConnection implements ModalCloseable {

    private volatile boolean closed = false;

    @Override
    public void close(final CloseMode closeMode) {
      // minimal implementation for compile purposes
      closed = true;
    }

    public boolean isClosed() {
      return closed;
    }

    @Override
    public void close() throws IOException {
      closed = true;
    }
  }
}
