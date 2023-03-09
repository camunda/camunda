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
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public final class OAuthCredentialsCache {

  private static final String KEY_AUTH = "auth";
  private static final String KEY_CREDENTIALS = "credentials";
  private static final TypeReference<Map<String, OAuthCachedCredentials>> TYPE_REFERENCE =
      new TypeReference<Map<String, OAuthCachedCredentials>>() {};
  private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

  private final Map<String, OAuthCachedCredentials> audiences;
  private final File cacheFile;

  public OAuthCredentialsCache(final File cacheFile) {
    this.cacheFile = cacheFile;
    audiences = new HashMap<>();
  }

  public synchronized OAuthCredentialsCache readCache() throws IOException {
    if (!cacheFile.exists() || cacheFile.length() == 0) {
      return this;
    }

    final Map<String, OAuthCachedCredentials> cache = MAPPER.readValue(cacheFile, TYPE_REFERENCE);
    audiences.clear();
    audiences.putAll(cache);

    return this;
  }

  public synchronized void writeCache() throws IOException {
    final Map<String, Map<String, OAuthCachedCredentials>> cache = new HashMap<>(audiences.size());
    for (final Entry<String, OAuthCachedCredentials> audience : audiences.entrySet()) {
      cache.put(audience.getKey(), Collections.singletonMap(KEY_AUTH, audience.getValue()));
    }

    ensureCacheFileExists();
    MAPPER.writer().writeValue(cacheFile, cache);
  }

  public synchronized Optional<ZeebeClientCredentials> get(final String endpoint) {
    return Optional.ofNullable(audiences.get(endpoint)).map(OAuthCachedCredentials::getCredentials);
  }

  public synchronized ZeebeClientCredentials computeIfMissingOrInvalid(
      final String endpoint,
      final SupplierWithIO<ZeebeClientCredentials> zeebeClientCredentialsConsumer)
      throws IOException {
    final Optional<ZeebeClientCredentials> optionalCredentials =
        readCache()
            .get(endpoint)
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
      put(endpoint, credentials).writeCache();
      return credentials;
    }
  }

  public synchronized <T> Optional<T> withCache(
      final String endpoint, final FunctionWithIO<ZeebeClientCredentials, T> function)
      throws IOException {
    final Optional<ZeebeClientCredentials> optionalCredentials = readCache().get(endpoint);
    if (optionalCredentials.isPresent()) {
      return Optional.ofNullable(function.apply(optionalCredentials.get()));
    } else {
      return Optional.empty();
    }
  }

  public synchronized OAuthCredentialsCache put(
      final String endpoint, final ZeebeClientCredentials credentials) {
    audiences.put(endpoint, new OAuthCachedCredentials(credentials));
    return this;
  }

  public synchronized int size() {
    return audiences.size();
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
