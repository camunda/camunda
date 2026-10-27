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
package io.camunda.client.impl.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.impl.CamundaClientCredentials;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class OAuthCredentialsCacheTest {
  private static final ZonedDateTime EXPIRY =
      ZonedDateTime.of(3020, 1, 1, 0, 0, 0, 0, ZoneId.of("Z"));

  private static final String WOMBAT_CLIENT_ID = "wombat-client";
  private static final String AARDVARK_CLIENT_ID = "aardvark-client";
  private static final String GOLDEN_FILE = "/oauth/credentialsCache.yml";
  private static final CamundaClientCredentials WOMBAT =
      new CamundaClientCredentials("wombat", EXPIRY, "Bearer");
  private static final CamundaClientCredentials AARDVARK =
      new CamundaClientCredentials("aardvark", EXPIRY, "Bearer");

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private File cacheFile;

  @Before
  public void setUp() throws IOException {
    cacheFile = new File(temporaryFolder.getRoot(), ".credsCache.yml");
    try (final InputStream input = getClass().getResourceAsStream(GOLDEN_FILE)) {
      Files.copy(input, cacheFile.toPath());
    }
  }

  @Test
  public void shouldCreateFileOnlyIfParentIsSymbolicLinkToFolder() throws IOException {
    final File root = new File(temporaryFolder.getRoot(), "/some/root");
    Files.createDirectories(root.toPath());
    assertThat(root.exists()).isTrue();

    final File target = new File(root, "target");
    Files.createDirectories(target.toPath());
    assertThat(target.exists()).isTrue();

    final File container = new File(root, ".camunda");
    assertThat(container.exists()).isFalse();
    Files.createSymbolicLink(container.toPath(), target.toPath());
    assertThat(container.exists()).isTrue();

    final File fileInContainer = new File(container, "/credentials");
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(fileInContainer);
    cache.writeCache();

    assertThat(fileInContainer.exists()).isTrue();
  }

  @Test
  public void shouldFailIfParentIsSymbolicLinkToFile() throws IOException {
    final File root = new File(temporaryFolder.getRoot(), "/some/root");
    Files.createDirectories(root.toPath());
    assertThat(root.exists()).isTrue();

    final File target = new File(root, "target");
    Files.createFile(target.toPath());
    assertThat(target.exists()).isTrue();

    final File container = new File(root, ".camunda");
    assertThat(container.exists()).isFalse();
    Files.createSymbolicLink(container.toPath(), target.toPath());
    assertThat(container.exists()).isTrue();

    final File fileInContainer = new File(container, "/credentials");
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(fileInContainer);

    assertThatThrownBy(cache::writeCache)
        .hasMessage(
            "Expected "
                + container.getAbsolutePath()
                + " to be a directory, but it was a symbolic link pointing to a regular file.");
    assertThat(fileInContainer.exists()).isFalse();
  }

  @Test
  public void shouldFailIfParentIsFile() throws IOException {
    final File root = new File(temporaryFolder.getRoot(), "/some/root");
    Files.createDirectories(root.toPath());
    assertThat(root.exists()).isTrue();

    final File target = new File(root, "target");
    Files.createFile(target.toPath());
    assertThat(target.exists()).isTrue();

    final File fileInContainer = new File(target, "/credentials");
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(fileInContainer);

    assertThatThrownBy(cache::writeCache)
        .hasMessage(
            "Expected "
                + target.getAbsolutePath()
                + " to be a directory, but it was a regular file.");
    assertThat(fileInContainer.exists()).isFalse();
  }

  @Test
  public void shouldFailIfParentIsBrokenSymbolicLink() throws IOException {
    final File root = new File(temporaryFolder.getRoot(), "/some/root");
    Files.createDirectories(root.toPath());
    assertThat(root.exists()).isTrue();

    final File target = new File(root, "target");
    Files.createDirectories(target.toPath());
    assertThat(target.exists()).isTrue();

    final File container = new File(root, ".camunda");
    assertThat(container.exists()).isFalse();
    Files.createSymbolicLink(container.toPath(), target.toPath());
    assertThat(container.exists()).isTrue();

    // now /some/root/.camunda -> /some/root/target
    // we will delete target, creating a dead link.
    assertThat(target.delete()).isTrue();

    final File fileInContainer = new File(container, "/credentials");
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(fileInContainer);

    assertThatThrownBy(cache::writeCache)
        .hasMessage(
            "Expected "
                + container.getAbsolutePath()
                + " to be a directory, but it was a symbolic link to unresolvable path.");
    assertThat(fileInContainer.exists()).isFalse();
  }

  @Test
  public void shouldCreateFileOnlyIfParentIsDirectory() throws IOException {
    final File root = new File(temporaryFolder.getRoot(), "/some/root");
    Files.createDirectories(root.toPath());
    assertThat(root.exists()).isTrue();

    final File target = new File(root, "target");
    Files.createDirectories(target.toPath());
    assertThat(target.exists()).isTrue();

    final File fileInContainer = new File(target, "/credentials");
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(fileInContainer);
    cache.writeCache();

    assertThat(target.exists()).isTrue();
    assertThat(fileInContainer.exists()).isTrue();
  }

  @Test
  public void shouldCreateDirectoryIfMissing() throws IOException {
    final File root = new File(temporaryFolder.getRoot(), "/some/root");
    Files.createDirectories(root.toPath());
    assertThat(root.exists()).isTrue();

    final File target = new File(root, "target");
    Files.createFile(target.toPath());
    assertThat(target.exists()).isTrue();

    final File container = new File(root, ".camunda");
    assertThat(container.exists()).isFalse();
    final File fileInContainer = new File(container, "/credentials");
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(fileInContainer);

    cache.writeCache();
    assertThat(container.exists()).isTrue();
    assertThat(fileInContainer.exists()).isTrue();
  }

  @Test
  public void shouldReadGoldenFile() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);

    // when
    cache.readCache();

    // then
    assertThat(cache.get(WOMBAT_CLIENT_ID)).contains(WOMBAT);
    assertThat(cache.get(AARDVARK_CLIENT_ID)).contains(AARDVARK);
    assertThat(cache.size()).isEqualTo(2);
  }

  @Test
  public void shouldWriteGoldenFile() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);

    // when
    cache.put(WOMBAT_CLIENT_ID, WOMBAT).put(AARDVARK_CLIENT_ID, AARDVARK).writeCache();

    // then
    final OAuthCredentialsCache copy = new OAuthCredentialsCache(cacheFile).readCache();
    assertThat(copy.get(WOMBAT_CLIENT_ID)).contains(WOMBAT);
    assertThat(copy.get(AARDVARK_CLIENT_ID)).contains(AARDVARK);
    assertThat(copy.size()).isEqualTo(2);
  }

  @Test
  public void shouldForceRefreshAndReturnTrueWhenCredentialsChanged() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);
    cache.readCache();
    final CamundaClientCredentials newCredentials =
        new CamundaClientCredentials("newToken", EXPIRY, "Bearer");

    // when — forceRefreshIfChanged replaces the wombat credentials with new ones
    final boolean changed = cache.forceRefreshIfChanged(WOMBAT_CLIENT_ID, () -> newCredentials);

    // then
    assertThat(changed).isTrue();
    assertThat(cache.get(WOMBAT_CLIENT_ID)).isNotEmpty().contains(newCredentials);
  }

  @Test
  public void shouldForceRefreshAndReturnFalseWhenCredentialsUnchanged() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);
    cache.readCache();

    // when — forceRefreshIfChanged returns the same token
    final boolean changed = cache.forceRefreshIfChanged(WOMBAT_CLIENT_ID, () -> WOMBAT);

    // then
    assertThat(changed).isFalse();
  }

  @Test
  public void shouldRejectNullRefreshThresholdWhenProactiveCallbackProvided() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);
    cache.readCache();

    // when / then — a non-null callback paired with a null threshold should fail fast
    // rather than NPE inside shouldRefreshProactively()
    assertThatThrownBy(
            () ->
                cache.computeIfMissingOrInvalid(
                    WOMBAT_CLIENT_ID,
                    () -> WOMBAT,
                    () -> {
                      /* no-op */
                    },
                    null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("proactiveTokenRefreshThreshold")
        .hasMessageContaining("non-null");
  }

  @Test
  public void shouldInvokeProactiveRefreshCallbackWhenTokenIsNearingExpiry() throws IOException {
    // given — a token that is within the proactive refresh window (expires in 45s: valid but
    // past the 60s proactive threshold)
    final ZonedDateTime nearExpiry = ZonedDateTime.now().plusSeconds(45);
    final CamundaClientCredentials nearExpiryCredentials =
        new CamundaClientCredentials("nearExpiry", nearExpiry, "Bearer");
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);
    cache.put(WOMBAT_CLIENT_ID, nearExpiryCredentials).writeCache();
    final AtomicBoolean callbackInvoked = new AtomicBoolean(false);

    // when
    final CamundaClientCredentials result =
        cache.computeIfMissingOrInvalid(
            WOMBAT_CLIENT_ID,
            () -> WOMBAT,
            () -> callbackInvoked.set(true),
            Duration.ofSeconds(60));

    // then — returns the still-valid token but triggers the callback
    assertThat(result.getAccessToken()).isEqualTo("nearExpiry");
    assertThat(callbackInvoked).isTrue();
  }

  @Test
  public void shouldNotInvokeProactiveRefreshCallbackWhenTokenIsFarFromExpiry() throws IOException {
    // given — a token far from expiry
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);
    cache.readCache();
    final AtomicBoolean callbackInvoked = new AtomicBoolean(false);

    // when — WOMBAT has expiry in year 3020, far from any threshold
    final CamundaClientCredentials result =
        cache.computeIfMissingOrInvalid(
            WOMBAT_CLIENT_ID,
            () -> WOMBAT,
            () -> callbackInvoked.set(true),
            Duration.ofSeconds(60));

    // then — returns the token without triggering the callback
    assertThat(result.getAccessToken()).isEqualTo("wombat");
    assertThat(callbackInvoked).isFalse();
  }

  @Test
  public void shouldSkipRedundantFetchWhenConcurrentRefreshCompleted() throws Exception {
    // given — two threads race to call forceRefreshIfChanged; only one should actually fetch
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);
    cache.readCache();
    final CountDownLatch fetchStarted = new CountDownLatch(1);
    final CountDownLatch fetchCanComplete = new CountDownLatch(1);
    final AtomicInteger fetchCount = new AtomicInteger(0);
    final AtomicReference<Thread> thread2 = new AtomicReference<>();
    final CamundaClientCredentials freshCredentials =
        new CamundaClientCredentials("freshToken", EXPIRY, "Bearer");

    // when — both threads read the generation counter before the lock, then one acquires
    // the lock and fetches while the other waits. When the second thread enters the lock,
    // it sees the generation was incremented and skips the fetch.
    final ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // Thread 1: enters the synchronized block, signals fetchStarted, then waits
      final Callable<Boolean> task1 =
          () ->
              cache.forceRefreshIfChanged(
                  WOMBAT_CLIENT_ID,
                  () -> {
                    fetchCount.incrementAndGet();
                    fetchStarted.countDown();
                    try {
                      fetchCanComplete.await(5, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    return freshCredentials;
                  });

      // Thread 2: records itself so we can observe its state, then calls forceRefreshIfChanged
      final Callable<Boolean> task2 =
          () -> {
            thread2.set(Thread.currentThread());
            return cache.forceRefreshIfChanged(
                WOMBAT_CLIENT_ID,
                () -> {
                  fetchCount.incrementAndGet();
                  return freshCredentials;
                });
          };

      // Submit only task1 first; task2 is submitted only after task1 holds the lock.
      // This guarantees task2 reads generationOnEntry = 0 while Thread 1 is mid-fetch.
      final Future<Boolean> first = pool.submit(task1);

      // Wait for Thread 1 to be inside the supplier (it holds the synchronized lock)
      assertThat(fetchStarted.await(5, TimeUnit.SECONDS)).isTrue();

      // Now submit task2: it will read generationOnEntry = 0, then block on the monitor
      final Future<Boolean> second = pool.submit(task2);

      // Wait until Thread 2 is blocked on the synchronized monitor entry.
      // At this point Thread 2 has already read generationOnEntry = 0 (before the lock),
      // so releasing Thread 1 now guarantees Thread 2 will observe the incremented generation.
      Awaitility.await()
          .atMost(5, TimeUnit.SECONDS)
          .until(
              () -> {
                final Thread t = thread2.get();
                return t != null && t.getState() == Thread.State.BLOCKED;
              });

      // Release Thread 1 to complete its fetch and increment the generation
      fetchCanComplete.countDown();

      // then — only one thread should have performed the expensive fetch
      assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
      assertThat(fetchCount.get()).isEqualTo(1);
      assertThat(cache.get(WOMBAT_CLIENT_ID)).isNotEmpty().contains(freshCredentials);
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void shouldFetchWhenCachedTokenIsExpired() throws IOException {
    // given — cache contains an expired token
    final ZonedDateTime pastExpiry = ZonedDateTime.now().minusMinutes(5);
    final CamundaClientCredentials expired =
        new CamundaClientCredentials("expiredToken", pastExpiry, "Bearer");
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);
    cache.put(WOMBAT_CLIENT_ID, expired).writeCache();

    final CamundaClientCredentials freshCredentials =
        new CamundaClientCredentials("freshToken", EXPIRY, "Bearer");

    // when — forceRefreshIfChanged is called with an expired cached token
    final boolean changed = cache.forceRefreshIfChanged(WOMBAT_CLIENT_ID, () -> freshCredentials);

    // then — the fetch was performed because the cached token was expired
    assertThat(changed).isTrue();
    assertThat(cache.get(WOMBAT_CLIENT_ID)).isNotEmpty().contains(freshCredentials);
  }

  @Test
  public void shouldUseInMemoryCacheOnHotPath() throws IOException {
    // given — a cache with a valid token in memory (put without writeCache to isolate in-memory)
    final File emptyFile = new File(temporaryFolder.getRoot(), ".emptyCacheFile.yml");
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(emptyFile);
    cache.put(WOMBAT_CLIENT_ID, WOMBAT);
    final AtomicBoolean fetchCalled = new AtomicBoolean(false);

    // when — computeIfMissingOrInvalid is called
    final CamundaClientCredentials result =
        cache.computeIfMissingOrInvalid(
            WOMBAT_CLIENT_ID,
            () -> {
              fetchCalled.set(true);
              return new CamundaClientCredentials("shouldNotBeUsed", EXPIRY, "Bearer");
            });

    // then — returns the in-memory token without fetching
    assertThat(result).isEqualTo(WOMBAT);
    assertThat(fetchCalled).isFalse();
  }

  @Test
  public void shouldFallBackToDiskWhenInMemoryCacheIsEmpty() throws IOException {
    // given — a cache with a valid token on disk but empty in-memory
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);
    // Don't call readCache() or put() — in-memory is empty, but disk has the golden file
    final AtomicBoolean fetchCalled = new AtomicBoolean(false);

    // when
    final CamundaClientCredentials result =
        cache.computeIfMissingOrInvalid(
            WOMBAT_CLIENT_ID,
            () -> {
              fetchCalled.set(true);
              return new CamundaClientCredentials("shouldNotBeUsed", EXPIRY, "Bearer");
            });

    // then — falls back to disk, finds WOMBAT from the golden file
    assertThat(result).isEqualTo(WOMBAT);
    assertThat(fetchCalled).isFalse();
  }

  @Test
  public void shouldConstructWithNullCacheFile() {
    // given / when
    final OAuthCredentialsCache cache = new OAuthCredentialsCache();

    // then — no eager disk access at construction time
    assertThat(cache.size()).isZero();
  }

  @Test
  public void shouldReadCacheAsNoOpWhenCacheFileIsNull() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache();
    cache.put(WOMBAT_CLIENT_ID, WOMBAT);

    // when — readCache must not touch disk, and must not clobber the in-memory state
    cache.readCache();

    // then
    assertThat(cache.get(WOMBAT_CLIENT_ID)).contains(WOMBAT);
  }

  @Test
  public void shouldWriteCacheAsNoOpWhenCacheFileIsNull() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache();
    cache.put(WOMBAT_CLIENT_ID, WOMBAT);

    // when — writeCache must not throw when no file is configured
    cache.writeCache();

    // then — in-memory state is preserved
    assertThat(cache.get(WOMBAT_CLIENT_ID)).contains(WOMBAT);
  }

  @Test
  public void shouldComputeIfMissingOrInvalidWithoutTouchingDiskWhenCacheFileIsNull()
      throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache();
    final AtomicInteger fetchCount = new AtomicInteger(0);

    // when — first call triggers the supplier, second call serves from in-memory
    final CamundaClientCredentials first =
        cache.computeIfMissingOrInvalid(
            WOMBAT_CLIENT_ID,
            () -> {
              fetchCount.incrementAndGet();
              return WOMBAT;
            });
    final CamundaClientCredentials second =
        cache.computeIfMissingOrInvalid(
            WOMBAT_CLIENT_ID,
            () -> {
              fetchCount.incrementAndGet();
              return WOMBAT;
            });

    // then
    assertThat(first).isEqualTo(WOMBAT);
    assertThat(second).isEqualTo(WOMBAT);
    assertThat(fetchCount.get()).isEqualTo(1);
  }

  @Test
  public void shouldForceRefreshIfChangedWithoutTouchingDiskWhenCacheFileIsNull()
      throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache();
    cache.put(WOMBAT_CLIENT_ID, WOMBAT);
    final CamundaClientCredentials refreshed =
        new CamundaClientCredentials("refreshed", EXPIRY, "Bearer");

    // when
    final boolean changed = cache.forceRefreshIfChanged(WOMBAT_CLIENT_ID, () -> refreshed);

    // then
    assertThat(changed).isTrue();
    assertThat(cache.get(WOMBAT_CLIENT_ID)).contains(refreshed);
  }

  @Test
  public void shouldPutAndWriteWithoutTouchingDiskWhenCacheFileIsNull() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache();

    // when
    cache.putAndWrite(WOMBAT_CLIENT_ID, WOMBAT);

    // then
    assertThat(cache.get(WOMBAT_CLIENT_ID)).contains(WOMBAT);
  }

  @Test
  public void shouldBeThreadSafe() throws InterruptedException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);

    final int threads = 5;
    final List<Callable<Object>> cacheOperations = new ArrayList<>();

    for (int i = 0; i < 5; i++) {
      cacheOperations.add(
          () ->
              cache.computeIfMissingOrInvalid(
                  WOMBAT_CLIENT_ID,
                  () -> {
                    cache.put(WOMBAT_CLIENT_ID, WOMBAT).writeCache();
                    return WOMBAT;
                  }));
      cacheOperations.add(
          () ->
              cache.withCache(
                  WOMBAT_CLIENT_ID,
                  value -> {
                    cache.put(WOMBAT_CLIENT_ID, WOMBAT).writeCache();
                    return WOMBAT;
                  }));
    }

    final ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      // when
      pool.invokeAll(cacheOperations)
          .forEach(
              future -> {
                try {
                  future.get();
                } catch (final InterruptedException | ExecutionException e) {
                  throw new RuntimeException(e);
                }
              });
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(60, TimeUnit.SECONDS);
    }

    // then
    assertThat(cache.get(WOMBAT_CLIENT_ID)).isNotEmpty().contains(WOMBAT);
  }
}
