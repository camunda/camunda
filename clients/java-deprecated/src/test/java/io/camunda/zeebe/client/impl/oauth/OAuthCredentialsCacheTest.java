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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.impl.ZeebeClientCredentials;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
