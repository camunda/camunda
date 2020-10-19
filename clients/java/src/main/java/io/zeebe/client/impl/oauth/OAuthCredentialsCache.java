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
package io.zeebe.client.impl.oauth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.zeebe.client.impl.ZeebeClientCredentials;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

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

  public OAuthCredentialsCache readCache() throws IOException {
    if (!cacheFile.exists() || cacheFile.length() == 0) {
      return this;
    }

    final Map<String, OAuthCachedCredentials> cache = MAPPER.readValue(cacheFile, TYPE_REFERENCE);
    audiences.clear();
    audiences.putAll(cache);

    return this;
  }

  public void writeCache() throws IOException {
    final Map<String, Map<String, OAuthCachedCredentials>> cache = new HashMap<>(audiences.size());
    for (final Entry<String, OAuthCachedCredentials> audience : audiences.entrySet()) {
      cache.put(audience.getKey(), Collections.singletonMap(KEY_AUTH, audience.getValue()));
    }

    ensureCacheFileExists();
    MAPPER.writer().writeValue(cacheFile, cache);
  }

  public Optional<ZeebeClientCredentials> get(final String endpoint) {
    return Optional.ofNullable(audiences.get(endpoint)).map(OAuthCachedCredentials::getCredentials);
  }

  public OAuthCredentialsCache put(
      final String endpoint, final ZeebeClientCredentials credentials) {
    audiences.put(endpoint, new OAuthCachedCredentials(credentials));
    return this;
  }

  public int size() {
    return audiences.size();
  }

  private void ensureCacheFileExists() throws IOException {
    if (cacheFile.exists()) {
      return;
    }

    Files.createDirectories(cacheFile.getParentFile().toPath());
    Files.createFile(cacheFile.toPath());
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
