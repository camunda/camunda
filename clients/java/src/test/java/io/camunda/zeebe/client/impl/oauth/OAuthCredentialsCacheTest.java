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

import static io.camunda.zeebe.client.OAuthCredentialsProviderTest.EXPIRY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.impl.ZeebeClientCredentials;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class OAuthCredentialsCacheTest {
  private static final String WOMBAT_ENDPOINT = "wombat.cloud.camunda.io";
  private static final String AARDVARK_ENDPOINT = "aardvark.cloud.camunda.io";
  private static final String GOLDEN_FILE = "/oauth/credentialsCache.yml";
  private static final ZeebeClientCredentials WOMBAT =
      new ZeebeClientCredentials("wombat", EXPIRY, "Bearer");
  private static final ZeebeClientCredentials AARDVARK =
      new ZeebeClientCredentials("aardvark", EXPIRY, "Bearer");

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
  public void shouldReadGoldenFile() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);

    // when
    cache.readCache();

    // then
    assertThat(cache.get(WOMBAT_ENDPOINT)).contains(WOMBAT);
    assertThat(cache.get(AARDVARK_ENDPOINT)).contains(AARDVARK);
    assertThat(cache.size()).isEqualTo(2);
  }

  @Test
  public void shouldWriteGoldenFile() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFile);

    // when
    cache.put(WOMBAT_ENDPOINT, WOMBAT).put(AARDVARK_ENDPOINT, AARDVARK).writeCache();

    // then
    final OAuthCredentialsCache copy = new OAuthCredentialsCache(cacheFile).readCache();
    assertThat(copy.get(WOMBAT_ENDPOINT)).contains(WOMBAT);
    assertThat(copy.get(AARDVARK_ENDPOINT)).contains(AARDVARK);
    assertThat(copy.size()).isEqualTo(2);
  }
}
