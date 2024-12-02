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
package io.camunda.zeebe.client.impl.oauth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.camunda.zeebe.client.impl.ZeebeClientCredentials;
import io.camunda.zeebe.client.impl.util.FunctionWithIO;
import io.camunda.zeebe.client.impl.util.SupplierWithIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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

  public Optional<ZeebeClientCredentials> get(final String clientId) {
    final Map<String, OAuthCachedCredentials> cache = credentialsByClientId.get();
    return Optional.ofNullable(cache.get(clientId)).map(OAuthCachedCredentials::getCredentials);
  }

  public synchronized ZeebeClientCredentials computeIfMissingOrInvalid(
      final String clientId,
      final SupplierWithIO<ZeebeClientCredentials> zeebeClientCredentialsConsumer)
      throws IOException {
    final Optional<ZeebeClientCredentials> optionalCredentials =
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
      return optionalCredentials.get();
    } else {
      final ZeebeClientCredentials credentials = zeebeClientCredentialsConsumer.get();
      put(clientId, credentials).writeCache();
      return credentials;
    }
  }

  public <T> Optional<T> withCache(
      final String clientId, final FunctionWithIO<ZeebeClientCredentials, T> function)
      throws IOException {
    final Optional<ZeebeClientCredentials> optionalCredentials = readCache().get(clientId);
    if (optionalCredentials.isPresent()) {
      return Optional.ofNullable(function.apply(optionalCredentials.get()));
    } else {
      return Optional.empty();
    }
  }

  public OAuthCredentialsCache put(
      final String clientId, final ZeebeClientCredentials credentials) {
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

    private final ZeebeClientCredentials credentials;

    @JsonCreator
    private OAuthCachedCredentials(
        @JsonProperty(KEY_AUTH) final Map<String, ZeebeClientCredentials> auth) {
      this(auth.get(KEY_CREDENTIALS));
    }

    private OAuthCachedCredentials(final ZeebeClientCredentials credentials) {
      this.credentials = credentials;
    }

    @JsonGetter(KEY_CREDENTIALS)
    private ZeebeClientCredentials getCredentials() {
      return credentials;
    }
  }
}
