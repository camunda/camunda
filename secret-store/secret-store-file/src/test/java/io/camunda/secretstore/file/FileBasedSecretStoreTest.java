/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBasedSecretStoreTest {

  @TempDir Path tempDir;

  @Test
  void shouldResolveKnownSecret() throws IOException {
    // given
    final var file = writeProperties("db-password=s3cr3t");
    final var store = new FileBasedSecretStore(file);

    // when
    final var result = store.resolve(Set.of(new FileBasedSecretReference("db-password")));

    // then
    assertThat(result.get(new FileBasedSecretReference("db-password")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("s3cr3t");
  }

  @Test
  void shouldReturnFailedForUnknownRef() throws IOException {
    // given
    final var file = writeProperties("other=value");
    final var store = new FileBasedSecretStore(file);

    // when
    final var result = store.resolve(Set.of(new FileBasedSecretReference("missing")));

    // then
    final var entry = result.get(new FileBasedSecretReference("missing"));
    assertThat(entry).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) entry).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldResolveMultipleRefsInOneBatch() throws IOException {
    // given
    final var file = writeProperties("a=1\nb=2");
    final var store = new FileBasedSecretStore(file);

    // when
    final var refs =
        Set.of(
            new FileBasedSecretReference("a"),
            new FileBasedSecretReference("b"),
            new FileBasedSecretReference("missing"));
    final var result = store.resolve(refs);

    // then — result map contains an entry for every requested ref
    assertThat(result)
        .containsKeys(
            new FileBasedSecretReference("a"),
            new FileBasedSecretReference("b"),
            new FileBasedSecretReference("missing"));
    assertThat(result.get(new FileBasedSecretReference("a")))
        .isInstanceOf(SecretResolutionResult.Resolved.class);
    assertThat(result.get(new FileBasedSecretReference("b")))
        .isInstanceOf(SecretResolutionResult.Resolved.class);
    assertThat(result.get(new FileBasedSecretReference("missing")))
        .isInstanceOf(SecretResolutionResult.Failed.class);
  }

  @Test
  void shouldListAllSecrets() throws IOException {
    // given
    final var file = writeProperties("a=1\nb=2\nc=3");
    final var store = new FileBasedSecretStore(file);

    // when
    final var list = store.list();

    // then
    assertThat(list)
        .containsExactlyInAnyOrder(
            new FileBasedSecretReference("a"),
            new FileBasedSecretReference("b"),
            new FileBasedSecretReference("c"));
  }

  @Test
  void shouldPickUpRotatedValue() throws IOException {
    // given
    final var file = writeProperties("api-key=old-value");
    final var store = new FileBasedSecretStore(file);

    // when — first resolve returns old value
    assertThat(
            store
                .resolve(Set.of(new FileBasedSecretReference("api-key")))
                .get(new FileBasedSecretReference("api-key")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("old-value");

    // rotate the file
    Files.writeString(file, "api-key=new-value", StandardCharsets.UTF_8);

    // then — second resolve picks up the new value without restart
    assertThat(
            store
                .resolve(Set.of(new FileBasedSecretReference("api-key")))
                .get(new FileBasedSecretReference("api-key")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("new-value");
  }

  @Test
  void shouldThrowWhenResolvingMissingFile() {
    // given
    final var store = new FileBasedSecretStore(tempDir.resolve("nonexistent.properties"));
    final var ref = new FileBasedSecretReference("any");

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of(ref)))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldThrowWhenListingMissingFile() {
    // given
    final var store = new FileBasedSecretStore(tempDir.resolve("nonexistent.properties"));

    // when / then
    assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldThrowWhenResolvingMalformedFile() throws IOException {
    // given
    final var file = tempDir.resolve("secrets.properties");
    Files.writeString(file, "bad=\\uZZZZ\n", StandardCharsets.UTF_8);
    final var store = new FileBasedSecretStore(file);

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of(new FileBasedSecretReference("bad"))))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldThrowWhenListingMalformedFile() throws IOException {
    // given — a properties file with a malformed unicode escape
    final var file = tempDir.resolve("secrets.properties");
    Files.writeString(file, "bad=\\uZZZZ\n", StandardCharsets.UTF_8);
    final var store = new FileBasedSecretStore(file);

    // when / then
    assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldIgnoreBlankKeysInList() throws IOException {
    // given — a properties file with an empty-key entry and a normal entry
    final var file = tempDir.resolve("secrets.properties");
    Files.writeString(file, "=orphaned-value\na=1\n", StandardCharsets.UTF_8);
    final var store = new FileBasedSecretStore(file);

    // when
    final var refs = store.list();

    // then
    assertThat(refs).containsExactly(new FileBasedSecretReference("a"));
  }

  @Test
  void shouldHandleUtf8Values() throws IOException {
    // given — non-Latin-1 characters that would be mangled by ISO-8859-1
    final var file = writeProperties("token=café-中文\n");
    final var store = new FileBasedSecretStore(file);

    // when
    final var result = store.resolve(Set.of(new FileBasedSecretReference("token")));

    // then
    assertThat(result.get(new FileBasedSecretReference("token")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("café-中文");
  }

  @Test
  void shouldHandleValuesWithSpecialPropertiesChars() throws IOException {
    // given — values containing characters that are special in .properties format (escaped)
    final var file =
        writeProperties("key1=val\\=ue\nkey2=has\\:colon\nkey3= padded\nkey4=back\\\\slash\n");
    final var store = new FileBasedSecretStore(file);

    // when
    final var result =
        store.resolve(
            Set.of(
                new FileBasedSecretReference("key1"),
                new FileBasedSecretReference("key2"),
                new FileBasedSecretReference("key3"),
                new FileBasedSecretReference("key4")));

    // then
    assertThat(
            ((SecretResolutionResult.Resolved) result.get(new FileBasedSecretReference("key1")))
                .value())
        .isEqualTo("val=ue");
    assertThat(
            ((SecretResolutionResult.Resolved) result.get(new FileBasedSecretReference("key2")))
                .value())
        .isEqualTo("has:colon");
    assertThat(
            ((SecretResolutionResult.Resolved) result.get(new FileBasedSecretReference("key3")))
                .value())
        .isEqualTo("padded");
    assertThat(
            ((SecretResolutionResult.Resolved) result.get(new FileBasedSecretReference("key4")))
                .value())
        .isEqualTo("back\\slash");
  }

  @Test
  void shouldBeThreadSafe() throws IOException, InterruptedException {
    // given
    final var file = writeProperties("secret=value");
    final var store = new FileBasedSecretStore(file);
    final int threadCount = 10;
    final int callsPerThread = 100;
    final var latch = new CountDownLatch(1);
    final var errors = new AtomicInteger(0);
    final var executor = Executors.newFixedThreadPool(threadCount);

    // when — all threads released simultaneously via the latch
    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              latch.await();
              for (int j = 0; j < callsPerThread; j++) {
                final var result = store.resolve(Set.of(new FileBasedSecretReference("secret")));
                if (!(result.get(new FileBasedSecretReference("secret"))
                    instanceof SecretResolutionResult.Resolved)) {
                  errors.incrementAndGet();
                }
                store.list();
              }
            } catch (final Exception e) {
              errors.incrementAndGet();
            }
          });
    }
    latch.countDown();
    executor.shutdown();
    assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

    // then — zero errors across all concurrent calls
    assertThat(errors.get()).isZero();
  }

  private Path writeProperties(final String content) throws IOException {
    final var file = tempDir.resolve("secrets.properties");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }
}
