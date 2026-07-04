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
import io.camunda.secretstore.SecretRef;
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
    final var result = store.resolve(Set.of(new SecretRef("db-password")));

    // then
    assertThat(result.get(new SecretRef("db-password")))
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
    final var result = store.resolve(Set.of(new SecretRef("missing")));

    // then
    final var entry = result.get(new SecretRef("missing"));
    assertThat(entry).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) entry).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldResolveMultipleRefsInOneBatch() throws IOException {
    // given
    final var file = writeProperties("a=1\nb=2");
    final var store = new FileBasedSecretStore(file);

    // when
    final var refs = Set.of(new SecretRef("a"), new SecretRef("b"), new SecretRef("missing"));
    final var result = store.resolve(refs);

    // then — result map contains an entry for every requested ref
    assertThat(result)
        .containsKeys(new SecretRef("a"), new SecretRef("b"), new SecretRef("missing"));
    assertThat(result.get(new SecretRef("a"))).isInstanceOf(SecretResolutionResult.Resolved.class);
    assertThat(result.get(new SecretRef("b"))).isInstanceOf(SecretResolutionResult.Resolved.class);
    assertThat(result.get(new SecretRef("missing")))
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
        .containsExactlyInAnyOrder(new SecretRef("a"), new SecretRef("b"), new SecretRef("c"));
  }

  @Test
  void shouldPickUpRotatedValue() throws IOException {
    // given
    final var file = writeProperties("api-key=old-value");
    final var store = new FileBasedSecretStore(file);

    // when — first resolve returns old value
    assertThat(store.resolve(Set.of(new SecretRef("api-key"))).get(new SecretRef("api-key")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("old-value");

    // rotate the file
    Files.writeString(file, "api-key=new-value", StandardCharsets.UTF_8);

    // then — second resolve picks up the new value without restart
    assertThat(store.resolve(Set.of(new SecretRef("api-key"))).get(new SecretRef("api-key")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("new-value");
  }

  @Test
  void shouldReturnStoreUnavailableWhenFileMissing() {
    // given
    final var store = new FileBasedSecretStore(tempDir.resolve("nonexistent.properties"));

    // when — must not throw
    final var result = store.resolve(Set.of(new SecretRef("any"), new SecretRef("other")));

    // then — every ref gets STORE_UNAVAILABLE, no exception propagated
    assertThat(result.values())
        .allSatisfy(
            r -> {
              assertThat(r).isInstanceOf(SecretResolutionResult.Failed.class);
              assertThat(((SecretResolutionResult.Failed) r).code())
                  .isEqualTo(SecretErrorCode.STORE_UNAVAILABLE);
            });
  }

  @Test
  void shouldThrowWhenListingMissingFile() {
    // given
    final var store = new FileBasedSecretStore(tempDir.resolve("nonexistent.properties"));

    // when / then
    assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldHandleUtf8Values() throws IOException {
    // given — non-Latin-1 characters that would be mangled by ISO-8859-1
    final var file = tempDir.resolve("secrets.properties");
    Files.writeString(file, "token=café-中文", StandardCharsets.UTF_8);
    final var store = new FileBasedSecretStore(file);

    // when
    final var result = store.resolve(Set.of(new SecretRef("token")));

    // then
    assertThat(result.get(new SecretRef("token")))
        .isInstanceOf(SecretResolutionResult.Resolved.class)
        .extracting(r -> ((SecretResolutionResult.Resolved) r).value())
        .isEqualTo("café-中文");
  }

  @Test
  void shouldHandleValuesWithSpecialPropertiesChars() throws IOException {
    // given — values containing characters that are special in .properties format (escaped)
    final var file = tempDir.resolve("secrets.properties");
    Files.writeString(file, "key1=val\\=ue\nkey2=has\\:colon\n", StandardCharsets.UTF_8);
    final var store = new FileBasedSecretStore(file);

    // when
    final var result = store.resolve(Set.of(new SecretRef("key1"), new SecretRef("key2")));

    // then
    assertThat(((SecretResolutionResult.Resolved) result.get(new SecretRef("key1"))).value())
        .isEqualTo("val=ue");
    assertThat(((SecretResolutionResult.Resolved) result.get(new SecretRef("key2"))).value())
        .isEqualTo("has:colon");
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
                final var result = store.resolve(Set.of(new SecretRef("secret")));
                if (!(result.get(new SecretRef("secret"))
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
