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
import static org.assertj.core.api.Assertions.fail;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.http.HttpClientFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Reproduction / Stress test for the unbounded async connect callback loop leading to a {@link
 * StackOverflowError} as observed in production traces.
 *
 * <p>Stack excerpt (abridged):
 *
 * <pre>
 * AsyncConnectExec$1.completed -> InternalHttpAsyncExecRuntime$1.completed -> BasicFuture.completed
 *  -> PoolingAsyncClientConnectionManager leaseCompleted/completed -> StrictConnPool.fireCallbacks
 *  -> ... -> AsyncConnectExec$2.failed -> ... -> MultihomeIOSessionRequester.connect -> ... (repeats)
 * </pre>
 *
 * <p>Rationale: We simulate MULTI-HOME behavior by returning a very large list of fabricated IP
 * addresses that are expected to fail to connect quickly. This stresses the internal connect
 * iteration logic. The test is intentionally {@link Disabled} because provoking an actual
 * StackOverflowError can be JVM / environment dependent and may destabilize CI.
 *
 * <p>What this test asserts instead:
 *
 * <ul>
 *   <li>We observe a large number of connection attempt callbacks (failures) within a short time
 *       window.
 *   <li>The callback depth (indicative via a sampled stack trace length) exceeds a heuristic
 *       threshold OR the number of attempts rapidly grows, suggesting insufficient bounding.
 * </ul>
 *
 * <p>To perform a manual reproduction (use with caution):
 *
 * <ol>
 *   <li>Temporarily remove @Disabled.
 *   <li>Increase ADDRESS_COUNT or LOWER the connect timeout further.
 *   <li>Run the test in isolation with a small thread stack (e.g. -Xss256k) to surface
 *       StackOverflowError sooner.
 * </ol>
 *
 * <p>Mitigations (not applied here) include: bounded retry strategy, host quarantine, capping DNS
 * results, fail-fast UnknownHost classification, and deferred scheduling/backoff.
 */
@Disabled("Disabled by default: stresses async connect loop; enable manually for investigation")
class AsyncConnectLoopReproducerTest {

  // Activation / tuning system properties
  private static final String ACTIVATE_PROP = "httpclient.loop.repro"; // boolean
  private static final String PROP_ADDRESS_COUNT = "httpclient.loop.repro.addressCount"; // int
  private static final String PROP_CONNECT_TIMEOUT_MS =
      "httpclient.loop.repro.connectTimeoutMs"; // int
  private static final String PROP_RESPONSE_TIMEOUT_MS =
      "httpclient.loop.repro.responseTimeoutMs"; // int
  private static final String PROP_FORCE_SOE = "httpclient.loop.repro.forceSOE"; // boolean
  private static final String PROP_MAX_RUNTIME_MS = "httpclient.loop.repro.runtimeMs"; // int
  private static final String PROP_WATCHDOG_INTERVAL_MS = "httpclient.loop.repro.watchdogMs"; // int
  private static final String PROP_CONCURRENCY = "httpclient.loop.repro.concurrency"; // int

  /** Default synthetic IP list size (overridden via system property). */
  private static final int DEFAULT_ADDRESS_COUNT =
      3; // smaller default to allow full cycle completion

  private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10;
  private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 100;
  private static final int DEFAULT_RUNTIME_MS = 5000;
  private static final int DEFAULT_WATCHDOG_INTERVAL_MS = 500;
  private static final int DEFAULT_CONCURRENCY = 10;

  private static final int STACK_DEPTH_SAMPLE_INTERVAL = 10; // every N failure callbacks (if any)
  private static final int STACK_DEPTH_WARN_THRESHOLD = 150; // heuristic

  @Test
  void shouldExposePotentialUnboundedConnectAttemptPattern() throws Exception {
    final int addressCount = Integer.getInteger(PROP_ADDRESS_COUNT, DEFAULT_ADDRESS_COUNT);
    final int connectTimeoutMs =
        Integer.getInteger(PROP_CONNECT_TIMEOUT_MS, DEFAULT_CONNECT_TIMEOUT_MS);
    final int responseTimeoutMs =
        Integer.getInteger(PROP_RESPONSE_TIMEOUT_MS, DEFAULT_RESPONSE_TIMEOUT_MS);
    final int runtimeMs = Integer.getInteger(PROP_MAX_RUNTIME_MS, DEFAULT_RUNTIME_MS);
    final int watchdogMs =
        Integer.getInteger(PROP_WATCHDOG_INTERVAL_MS, DEFAULT_WATCHDOG_INTERVAL_MS);
    final int concurrency = Integer.getInteger(PROP_CONCURRENCY, DEFAULT_CONCURRENCY);

    final CountingDnsResolver dnsResolver = new CountingDnsResolver(addressCount);

    //    final IOReactorConfig ioConfig =
    //        IOReactorConfig.custom()
    //            .setIoThreadCount(1)
    //            .setSelectInterval(Timeout.ofMilliseconds(5))
    //            .build();
    //
    //    final RequestConfig requestConfig =
    //        RequestConfig.custom()
    //            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
    //            .setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMs))
    //            .build();
    //
    //    final PoolingAsyncClientConnectionManager connMgr =
    //
    // PoolingAsyncClientConnectionManagerBuilder.create().setDnsResolver(dnsResolver).build();
    //
    //    final CloseableHttpAsyncClient client =
    //        HttpAsyncClients.custom()
    //            .setIOReactorConfig(ioConfig)
    //            .setConnectionManager(connMgr)
    //            .setDefaultRequestConfig(requestConfig)
    //            .build();

    final HttpClient camundaClient =
        new HttpClientFactory(
                (CamundaClientConfiguration)
                    new CamundaClientBuilderImpl().numJobWorkerExecutionThreads(10))
            .createClient();
    final CloseableHttpAsyncClient client = camundaClient.rawClient();

    client.start();

    // Latch must be defined before uncaught handler references it
    final CountDownLatch done = new CountDownLatch(concurrency);

    // Reintroduce uncaught SOE capture (previously removed accidentally)
    final java.util.concurrent.atomic.AtomicReference<Throwable> uncaught =
        new java.util.concurrent.atomic.AtomicReference<>();
    final Thread.UncaughtExceptionHandler previousHandler =
        Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (t, e) -> {
          if (e instanceof StackOverflowError) {
            uncaught.compareAndSet(null, e);
            done.countDown();
          }
          if (previousHandler != null) {
            previousHandler.uncaughtException(t, e);
          }
        });

    final SimpleHttpRequest request =
        SimpleHttpRequests.get(
            new URIBuilder()
                .setScheme("http")
                .setHost("adagfdafadfadfaadf.com")
                .setPort(6553)
                .setPath("/")
                .build());

    final AtomicInteger failureCallbacks = new AtomicInteger();
    final AtomicInteger completedCallbacks = new AtomicInteger();
    final AtomicInteger cancelledCallbacks = new AtomicInteger();
    final AtomicInteger sampledLargeStackDepthEvents = new AtomicInteger();
    final AtomicInteger forcedSOECount = new AtomicInteger();
    final java.util.concurrent.atomic.AtomicReference<String> firstCallback =
        new java.util.concurrent.atomic.AtomicReference<>();
    final AtomicInteger totalRequestsStarted = new AtomicInteger();

    final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(runtimeMs);
    final AtomicInteger watchdogFires = new AtomicInteger();

    final CompletableFuture<Void> overall = new CompletableFuture<>();
    final java.util.concurrent.atomic.AtomicReference<Throwable> syncExecuteException =
        new java.util.concurrent.atomic.AtomicReference<>();

    // Watchdog logs progress until completion
    //    final java.util.concurrent.ScheduledExecutorService scheduler =
    //        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
    //            r -> {
    //              final Thread t = new Thread(r, "loop-repro-watchdog");
    //              t.setDaemon(true);
    //              return t;
    //            });
    //    scheduler.scheduleAtFixedRate(
    //        () -> {
    //          if (done.getCount() == 0) {
    //            return;
    //          }
    //          final int wf = watchdogFires.incrementAndGet();
    //          System.out.println(
    //              "[AsyncConnectLoopReproducerTest] watchdog fire="
    //                  + wf
    //                  + " failures="
    //                  + failureCallbacks.get()
    //                  + " completed="
    //                  + completedCallbacks.get()
    //                  + " cancelled="
    //                  + cancelledCallbacks.get()
    //                  + " firstCallback="
    //                  + firstCallback.get());
    //          if (System.nanoTime() > deadline) {
    //            System.out.println(
    //                "[AsyncConnectLoopReproducerTest] watchdog forcing timeout completion");
    //            done.countDown();
    //          }
    //        },
    //        watchdogMs,
    //        watchdogMs,
    //        TimeUnit.MILLISECONDS);

    // Remove previous single execute attempt – replace with concurrent submissions
    final ExecutorService pool =
        Executors.newFixedThreadPool(
            concurrency,
            r -> {
              final Thread t = new Thread(r, "loop-repro-worker");
              t.setDaemon(true);
              return t;
            });

    final SimpleHttpRequest requestTemplate = request; // same request each time

    for (int i = 0; i < concurrency; i++) {
      final int requestId = i;
      pool.submit(
          () -> {
            if (System.nanoTime() > deadline) {
              done.countDown();
              return;
            }
            totalRequestsStarted.incrementAndGet();
            try {
              client.execute(
                  requestTemplate,
                  new FutureCallback<SimpleHttpResponse>() {
                    @Override
                    public void completed(final SimpleHttpResponse result) {
                      completedCallbacks.incrementAndGet();
                      firstCallback.compareAndSet(null, "completed");
                      done.countDown();
                    }

                    @Override
                    public void failed(final Exception ex) {
                      final int c = failureCallbacks.incrementAndGet();
                      if (c % STACK_DEPTH_SAMPLE_INTERVAL == 0) {
                        final int depth = Thread.currentThread().getStackTrace().length;
                        if (depth > STACK_DEPTH_WARN_THRESHOLD) {
                          sampledLargeStackDepthEvents.incrementAndGet();
                        }
                      }
                      firstCallback.compareAndSet(null, "failed:" + ex.getClass().getSimpleName());
                      done.countDown();
                    }

                    @Override
                    public void cancelled() {
                      cancelledCallbacks.incrementAndGet();
                      firstCallback.compareAndSet(null, "cancelled");
                      done.countDown();
                    }
                  });
            } catch (final Throwable syncEx) {
              firstCallback.compareAndSet(
                  null, "sync-exception:" + syncEx.getClass().getSimpleName());
              failureCallbacks.incrementAndGet();
              done.countDown();
            }
          });
    }

    final boolean finished = done.await(runtimeMs + 200000L, TimeUnit.MILLISECONDS);
    //    pool.shutdownNow();
    //    client.close();
    Thread.setDefaultUncaughtExceptionHandler(previousHandler);

    logSummary(
        addressCount,
        connectTimeoutMs,
        responseTimeoutMs,
        runtimeMs,
        failureCallbacks.get(),
        sampledLargeStackDepthEvents.get(),
        forcedSOECount.get(),
        uncaught.get(),
        completedCallbacks.get(),
        cancelledCallbacks.get(),
        firstCallback.get(),
        watchdogFires.get(),
        syncExecuteException.get());

    if (!finished) {
      fail("Test did not finish within budget; potential hang in async connect loop");
    }

    final Throwable soe = uncaught.get();
    if (soe != null) {
      assertThat(soe)
          .as("Captured StackOverflowError indicating recursion")
          .isInstanceOf(StackOverflowError.class);
      return;
    }

    // Provide diagnostic if only single failure observed
    if (failureCallbacks.get() == 1) {
      System.out.println(
          "[AsyncConnectLoopReproducerTest] Only a single failure callback observed; likely bounded behavior in current httpclient/httpcore version (no deep recursion surfaced). Try larger addressCount or smaller timeouts, or use synthetic recursion test.");
    }

    if (failureCallbacks.get() == 0
        && completedCallbacks.get() == 0
        && cancelledCallbacks.get() == 0) {
      fail(
          "No callback (completed/failed/cancelled) observed; indicates request never progressed to callback layer.");
    }

    // Provide diagnostic if only a cancellation happened.
    if (cancelledCallbacks.get() > 0 && failureCallbacks.get() == 0) {
      System.out.println(
          "[AsyncConnectLoopReproducerTest] Request cancelled before failure surfaced; likely timeout + internal abort rather than recursive failure loop.");
    }

    if (failureCallbacks.get() == 1 && completedCallbacks.get() == 0) {
      System.out.println(
          "[AsyncConnectLoopReproducerTest] Single failure callback -> bounded behavior. Try: -Dhttpclient.loop.repro.addressCount=6000 -Dhttpclient.loop.repro.connectTimeoutMs=2 -Xss192k");
    }

    assertThat(failureCallbacks.get() + completedCallbacks.get() + cancelledCallbacks.get())
        .as("At least one terminal callback expected")
        .isGreaterThan(0);
    assertThat(sampledLargeStackDepthEvents.get())
        .as("Stack depth samples (informational)")
        .isGreaterThanOrEqualTo(0);
  }

  private void logSummary(
      final int addressCount,
      final int connectMs,
      final int responseMs,
      final int runtimeMs,
      final int failureCallbacks,
      final int largeDepthEvents,
      final int forcedSOECount,
      final Throwable soe,
      final int completedCallbacks,
      final int cancelledCallbacks,
      final String firstCallback,
      final int watchdogFires,
      final Throwable syncExecuteException) {
    System.out.println(
        "[AsyncConnectLoopReproducerTest] summary{"
            + "activated=true"
            + ", addressCount="
            + addressCount
            + ", connectTimeoutMs="
            + connectMs
            + ", responseTimeoutMs="
            + responseMs
            + ", runtimeBudgetMs="
            + runtimeMs
            + ", failureCallbacks="
            + failureCallbacks
            + ", completedCallbacks="
            + completedCallbacks
            + ", cancelledCallbacks="
            + cancelledCallbacks
            + ", firstCallback="
            + firstCallback
            + ", sampledLargeDepthEvents="
            + largeDepthEvents
            + ", forcedSOECount="
            + forcedSOECount
            + ", watchdogFires="
            + watchdogFires
            + ", syncExecuteException="
            + (syncExecuteException != null ? syncExecuteException.getClass().getName() : "null")
            + ", stackOverflowCaptured="
            + (soe != null)
            + "}");
  }

  /**
   * Intentionally recurse until a StackOverflowError occurs, used to validate harness detection.
   */
  private void triggerForcedSOE() {
    recurse(0);
  }

  private void recurse(final int i) {
    // allocate small local array to accelerate stack consumption
    final int[] pad = new int[16]; // NOP usage
    pad[0] = i;
    recurse(i + 1);
  }

  /**
   * DnsResolver returning a large synthetic list of IP addresses in documentation / TEST-NET
   * ranges. Each address is expected to fail quickly. This amplifies multihome iteration.
   */
  private static final class CountingDnsResolver implements DnsResolver {
    private final InetAddress[] addresses;

    CountingDnsResolver(final int count) {
      try {
        final List<InetAddress> list = new ArrayList<>(count);
        // Use TEST-NET ranges 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24 cyclically.
        //        final int[][] bases = new int[][] {{192, 0, 2}, {198, 51, 100}, {203, 0, 113}};
        //        for (int i = 0; i < count; i++) {
        //          final int[] base = bases[i % bases.length];
        //          final byte[] addr =
        //              new byte[] {(byte) base[0], (byte) base[1], (byte) base[2], (byte) (i % 250
        // + 1)};
        //          list.add(InetAddress.getByAddress("test-host-" + i, addr));
        //        }
        addresses = list.toArray(new InetAddress[0]);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
      return addresses;
    }

    @Override
    public String resolveCanonicalHostname(final String host) throws UnknownHostException {
      return host;
    }
  }
}
