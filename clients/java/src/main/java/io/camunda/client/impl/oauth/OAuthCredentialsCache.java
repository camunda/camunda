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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.camunda.client.impl.CamundaClientCredentials;
import io.camunda.client.impl.util.FunctionWithIO;
import io.camunda.client.impl.util.SupplierWithIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public final class OAuthCredentialsCache {

  private static final String KEY_AUTH = "auth";
  private static final String KEY_CREDENTIALS = "credentials";
  private static final TypeReference<Map<String, OAuthCachedCredentials>> TYPE_REFERENCE =
      new TypeReference<Map<String, OAuthCachedCredentials>>() {};
  private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

  /**
   * This lock is used to make access to the cache file thread-safe. It allows multiple threads to
   * read at once, as long as no threads are writing. Only one thread is allowed to write at a time.
   */
  private static final ReentrantReadWriteLock READ_WRITE_LOCK = new ReentrantReadWriteLock();

  private static final ReentrantReadWriteLock.ReadLock READ_LOCK = READ_WRITE_LOCK.readLock();
  private static final ReentrantReadWriteLock.WriteLock WRITE_LOCK = READ_WRITE_LOCK.writeLock();

  private final File cacheFile;
  private final AtomicReference<Map<String, OAuthCachedCredentials>> credentialsByClientId;

  /**
   * Monotonically increasing counter incremented after every successful token refresh. Used by
   * {@link #forceRefreshIfChanged} to detect that a concurrent caller already refreshed while this
   * thread was waiting for the lock, so a potential redundant auth call can be skipped.
   */
  private final AtomicLong refreshGeneration = new AtomicLong(0);

  public OAuthCredentialsCache(final File cacheFile) {
    this.cacheFile = cacheFile;
    credentialsByClientId = new AtomicReference<>(new HashMap<>());
  }

  public OAuthCredentialsCache readCache() throws IOException {
    READ_LOCK.lock();
    try {
      if (!cacheFile.exists() || cacheFile.length() == 0) {
        return this;
      }

      final Map<String, OAuthCachedCredentials> cache = MAPPER.readValue(cacheFile, TYPE_REFERENCE);
      credentialsByClientId.set(cache);
    } finally {
      READ_LOCK.unlock();
    }

    return this;
  }

  public void writeCache() throws IOException {
    final Map<String, OAuthCachedCredentials> values = credentialsByClientId.get();

    final Map<String, Map<String, OAuthCachedCredentials>> cache = new HashMap<>(values.size());
    for (final Entry<String, OAuthCachedCredentials> clients : values.entrySet()) {
      cache.put(clients.getKey(), Collections.singletonMap(KEY_AUTH, clients.getValue()));
    }

    WRITE_LOCK.lock();
    try {
      ensureCacheFileExists();
      MAPPER.writer().writeValue(cacheFile, cache);
    } finally {
      WRITE_LOCK.unlock();
    }
  }

  public Optional<CamundaClientCredentials> get(final String clientId) {
    final Map<String, OAuthCachedCredentials> cache = credentialsByClientId.get();
    return Optional.ofNullable(cache.get(clientId)).map(OAuthCachedCredentials::getCredentials);
  }

  public synchronized CamundaClientCredentials computeIfMissingOrInvalid(
      final String clientId,
      final SupplierWithIO<CamundaClientCredentials> zeebeClientCredentialsConsumer)
      throws IOException {
    return computeIfMissingOrInvalid(clientId, zeebeClientCredentialsConsumer, null);
  }

  /**
   * Returns a valid cached token, or fetches a new one if missing/invalid. When a {@code
   * proactiveRefreshCallback} is provided and the cached token is valid but nearing expiry (as
   * determined by {@link CamundaClientCredentials#shouldRefreshProactively()}), the callback is
   * invoked to trigger a background refresh while the still-valid token is returned immediately.
   *
   * <p>This method first checks the in-memory cache (a fast HashMap lookup) before falling back to
   * reading from the on-disk YAML cache. Under high throughput (~300 req/s), this avoids hundreds
   * of file reads per second while holding the synchronized lock.
   */
  public synchronized CamundaClientCredentials computeIfMissingOrInvalid(
      final String clientId,
      final SupplierWithIO<CamundaClientCredentials> zeebeClientCredentialsConsumer,
      final Runnable proactiveRefreshCallback)
      throws IOException {

    // Fast path: check the in-memory cache first (no disk I/O).
    // This is the hot path under steady-state load — the token is valid in memory
    // and we can return it without touching the filesystem.
    final Optional<CamundaClientCredentials> inMemoryCredentials = get(clientId);
    if (inMemoryCredentials.isPresent() && inMemoryCredentials.get().isValid()) {
      final CamundaClientCredentials credentials = inMemoryCredentials.get();
      if (proactiveRefreshCallback != null && credentials.shouldRefreshProactively()) {
        proactiveRefreshCallback.run();
      }
      return credentials;
    }

    // Slow path: read from disk (another process/thread might have updated the file),
    // then check validity again.
    final Optional<CamundaClientCredentials> optionalCredentials =
        readCache()
            .get(clientId)
            .flatMap(
                zeebeClientCredentials -> {
                  if (!zeebeClientCredentials.isValid()) {
                    return Optional.empty();
                  } else {
                    return Optional.of(zeebeClientCredentials);
                  }
                });
    if (optionalCredentials.isPresent()) {
      final CamundaClientCredentials credentials = optionalCredentials.get();
      if (proactiveRefreshCallback != null && credentials.shouldRefreshProactively()) {
        proactiveRefreshCallback.run();
      }
      return credentials;
    } else {
      final CamundaClientCredentials credentials = zeebeClientCredentialsConsumer.get();
      put(clientId, credentials).writeCache();
      return credentials;
    }
  }

  /**
   * Ensures that a fresh token is available in the cache, and returns {@code true} if the caller
   * should retry its request (i.e. credentials were refreshed — either by this thread or by a
   * concurrent one that completed while this thread was waiting for the lock).
   *
   * <p>When a concurrent call already refreshed the token (detected via a generation counter read
   * before acquiring the monitor), the expensive OAuth HTTP call is skipped and {@code true} is
   * returned immediately. This reduces lock hold time from N × ~200ms (where N is the number of
   * concurrent 401 retries) to ~200ms + (N−1) × O(μs), preventing a cascading freeze of the HTTP
   * client's I/O reactor.
   *
   * @return {@code true} if credentials were refreshed (by any thread) and the request should be
   *     retried; {@code false} if the freshly fetched credentials are identical to the previously
   *     cached ones (i.e. the 401 was not caused by a stale token)
   */
  public boolean forceRefreshIfChanged(
      final String clientId, final SupplierWithIO<CamundaClientCredentials> credentialsSupplier)
      throws IOException {
    // Capture the generation BEFORE blocking on the synchronized monitor.
    // If another thread refreshes the token while we wait, the generation will be higher
    // when we enter the critical section, and we can safely skip the redundant fetch.
    final long generationOnEntry = refreshGeneration.get();
    return doForceRefreshIfChanged(clientId, credentialsSupplier, generationOnEntry);
  }

  private synchronized boolean doForceRefreshIfChanged(
      final String clientId,
      final SupplierWithIO<CamundaClientCredentials> credentialsSupplier,
      final long generationOnEntry)
      throws IOException {
    // Stampede prevention: another thread already refreshed while we were waiting for the
    // lock. Return true ("retry the request") — the cache now holds a fresh token. We do
    // not know whether the new token differs from the old one, but the caller only needs
    // to know whether a retry is worthwhile, and it is.
    if (refreshGeneration.get() > generationOnEntry) {
      return true;
    }

    final CamundaClientCredentials previous = readCache().get(clientId).orElse(null);
    final CamundaClientCredentials fresh = credentialsSupplier.get();
    put(clientId, fresh).writeCache();
    refreshGeneration.incrementAndGet();
    return !fresh.equals(previous);
  }

  /**
   * Atomically puts credentials into the cache and writes to disk. This method is synchronized on
   * the same monitor as {@link #computeIfMissingOrInvalid}, ensuring that a concurrent {@code
   * readCache()} cannot clobber the in-memory state between the put and the write. This is designed
   * for the background proactive refresh: the caller fetches the token outside any lock (the slow
   * HTTP call), then calls this method to briefly acquire the monitor for the fast put+write.
   */
  public synchronized void putAndWrite(
      final String clientId, final CamundaClientCredentials credentials) throws IOException {
    put(clientId, credentials).writeCache();
    refreshGeneration.incrementAndGet();
  }

  public <T> Optional<T> withCache(
      final String clientId, final FunctionWithIO<CamundaClientCredentials, T> function)
      throws IOException {
    final Optional<CamundaClientCredentials> optionalCredentials = readCache().get(clientId);
    if (optionalCredentials.isPresent()) {
      return Optional.ofNullable(function.apply(optionalCredentials.get()));
    } else {
      return Optional.ofNullable(function.apply(null));
    }
  }

  public OAuthCredentialsCache put(
      final String clientId, final CamundaClientCredentials credentials) {
    credentialsByClientId.getAndUpdate(
        current -> {
          final HashMap<String, OAuthCachedCredentials> cache = new HashMap<>(current);
          cache.put(clientId, new OAuthCachedCredentials(credentials));
          return cache;
        });
    return this;
  }

  public int size() {
    return credentialsByClientId.get().size();
  }

  private void ensureCacheFileExists() throws IOException {
    if (cacheFile.exists()) {
      return;
    }
    final File parentDirectory = cacheFile.getParentFile();
    if (parentDirectory.exists()) {
      if (!parentDirectory.isDirectory()) {
        if (Files.isSymbolicLink(parentDirectory.toPath())) {
          requireSymbolicLinkPointsToDirectory(parentDirectory);
        } else {
          throw new IOException(
              "Expected "
                  + parentDirectory.getAbsolutePath()
                  + " to be a directory, but it was a regular file.");
        }
      }
    } else {
      if (Files.isSymbolicLink(parentDirectory.toPath())) {
        requireSymbolicLinkPointsToDirectory(parentDirectory);
      } else {
        Files.createDirectories(parentDirectory.toPath());
      }
    }
    Files.createFile(cacheFile.toPath());
  }

  /**
   * Makes sure the symbolic link is pointing to a directory.
   *
   * @param file fil to check.
   * @throws IOException if the file pointer is not a symbolic link or is not pointing to a
   *     directory.
   */
  private void requireSymbolicLinkPointsToDirectory(final File file) throws IOException {
    final Path resolvedPath = Files.readSymbolicLink(file.toPath());
    if (Files.exists(resolvedPath)) {
      if (!Files.isDirectory(resolvedPath)) {
        throw new IOException(
            "Expected "
                + file.getAbsolutePath()
                + " to be a directory, but it was a symbolic link pointing to a regular file.");
      }
    } else {
      throw new IOException(
          "Expected "
              + file.getAbsolutePath()
              + " to be a directory, but it was a symbolic link to unresolvable path.");
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class OAuthCachedCredentials {

    private final CamundaClientCredentials credentials;

    @JsonCreator
    private OAuthCachedCredentials(
        @JsonProperty(KEY_AUTH) final Map<String, CamundaClientCredentials> auth) {
      this(auth.get(KEY_CREDENTIALS));
    }

    private OAuthCachedCredentials(final CamundaClientCredentials credentials) {
      this.credentials = credentials;
    }

    @JsonGetter(KEY_CREDENTIALS)
    private CamundaClientCredentials getCredentials() {
      return credentials;
    }
  }
}
