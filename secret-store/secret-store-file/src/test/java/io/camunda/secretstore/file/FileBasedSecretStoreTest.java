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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBasedSecretStoreTest {

  @TempDir Path secretsDir;
  @TempDir Path outsideDir;

  private static String resolvedValue(
      final Map<String, SecretResolutionResult> result, final String name) {
    final var entry = result.get(name);
    assertThat(entry).isInstanceOf(SecretResolutionResult.Resolved.class);
    return ((SecretResolutionResult.Resolved) entry).value();
  }

  private void writeSecret(final String name, final String value) throws IOException {
    Files.writeString(secretsDir.resolve(name), value, StandardCharsets.UTF_8);
  }

  @Test
  void shouldResolveKnownSecret() throws IOException {
    // given
    writeSecret("db-password", "s3cr3t");
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("db-password"));

    // then
    assertThat(resolvedValue(result, "db-password")).isEqualTo("s3cr3t");
  }

  @Test
  void shouldReturnFailedForUnknownRef() throws IOException {
    // given
    writeSecret("other", "value");
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("missing"));

    // then
    final var entry = result.get("missing");
    assertThat(entry).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) entry).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldResolveMultipleRefsInOneBatch() throws IOException {
    // given
    writeSecret("a", "1");
    writeSecret("b", "2");
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("a", "b", "missing"));

    // then — result map contains an entry for every requested ref
    assertThat(result).containsKeys("a", "b", "missing");
    assertThat(resolvedValue(result, "a")).isEqualTo("1");
    assertThat(resolvedValue(result, "b")).isEqualTo("2");
    assertThat(result.get("missing")).isInstanceOf(SecretResolutionResult.Failed.class);
  }

  @Test
  void shouldReturnEmptyMapForEmptyRefSet() {
    // given
    final var store = new FileBasedSecretStore(secretsDir);

    // when / then
    assertThat(store.resolve(Set.of())).isEmpty();
  }

  @Test
  void shouldListAllSecrets() throws IOException {
    // given
    writeSecret("a", "1");
    writeSecret("b", "2");
    writeSecret("c", "3");
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var list = store.list();

    // then
    assertThat(list).containsExactlyInAnyOrder("a", "b", "c");
  }

  @Test
  void shouldSkipHiddenEntriesInList() throws IOException {
    // given: a real secret plus hidden entries (a leading-dot file and an atomic-update dir)
    writeSecret("a", "1");
    writeSecret(".env", "hidden");
    Files.createDirectory(secretsDir.resolve("..2026_07_13_10_00_00.123456789"));
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var list = store.list();

    // then
    assertThat(list).containsExactly("a");
  }

  @Test
  void shouldSkipSubdirectoriesInList() throws IOException {
    // given
    writeSecret("a", "1");
    Files.createDirectory(secretsDir.resolve("nested"));
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var list = store.list();

    // then
    assertThat(list).containsExactly("a");
  }

  @Test
  void shouldFollowSymlinkToRealFile() throws IOException {
    // given: real files under a hidden ..data dir with top-level symlinks (the atomic-update
    // layout a mounted Secret volume uses)
    final var dataDir = Files.createDirectory(secretsDir.resolve("..data"));
    Files.writeString(dataDir.resolve("db-password"), "s3cr3t", StandardCharsets.UTF_8);
    Files.createSymbolicLink(secretsDir.resolve("db-password"), dataDir.resolve("db-password"));
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("db-password"));
    final var list = store.list();

    // then — the symlink is followed for the value, and ..data is not listed
    assertThat(resolvedValue(result, "db-password")).isEqualTo("s3cr3t");
    assertThat(list).containsExactly("db-password");
  }

  @Test
  void shouldReturnNotFoundForSymlinkResolvingOutsideDirectory() throws IOException {
    // given — a symlink inside the store pointing at a file outside it (arbitrary-file-read
    // attempt)
    final var target = outsideDir.resolve("shadow");
    Files.writeString(target, "root:x:0:0", StandardCharsets.UTF_8);
    Files.createSymbolicLink(secretsDir.resolve("evil"), target);
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var entry = store.resolve(Set.of("evil")).get("evil");

    // then — the escaping symlink is refused, not read
    assertThat(entry).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) entry).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldSkipSymlinkResolvingOutsideDirectoryInList() throws IOException {
    // given — a legitimate secret plus an escaping symlink
    writeSecret("valid", "1");
    final var target = outsideDir.resolve("shadow");
    Files.writeString(target, "root:x:0:0", StandardCharsets.UTF_8);
    Files.createSymbolicLink(secretsDir.resolve("evil"), target);
    final var store = new FileBasedSecretStore(secretsDir);

    // when / then — the escaping symlink is not advertised as a secret
    assertThat(store.list()).containsExactly("valid");
  }

  @Test
  void shouldPickUpRotatedValue() throws IOException {
    // given
    writeSecret("api-key", "old-value");
    final var store = new FileBasedSecretStore(secretsDir);

    // when — first resolve returns old value
    assertThat(resolvedValue(store.resolve(Set.of("api-key")), "api-key")).isEqualTo("old-value");

    // rotate the file
    writeSecret("api-key", "new-value");

    // then — second resolve picks up the new value without restart
    assertThat(resolvedValue(store.resolve(Set.of("api-key")), "api-key")).isEqualTo("new-value");
  }

  @Test
  void shouldTrimSingleTrailingNewline() throws IOException {
    // given
    writeSecret("lf", "value\n");
    writeSecret("crlf", "value\r\n");
    writeSecret("none", "value");
    writeSecret("double", "value\n\n");
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("lf", "crlf", "none", "double"));

    // then — exactly one trailing terminator is stripped
    assertThat(resolvedValue(result, "lf")).isEqualTo("value");
    assertThat(resolvedValue(result, "crlf")).isEqualTo("value");
    assertThat(resolvedValue(result, "none")).isEqualTo("value");
    assertThat(resolvedValue(result, "double")).isEqualTo("value\n");
  }

  @Test
  void shouldPreserveMultilineAndSpecialCharValuesVerbatim() throws IOException {
    // given — values that .properties would mangle: JSON, colons/equals, a PEM-style block
    final var json = "{\"user\":\"a\",\"pw\":\"b=c:d\"}";
    final var pem = "-----BEGIN KEY-----\nabc=def\nghi/jkl\n-----END KEY-----";
    writeSecret("json", json);
    writeSecret("pem", pem);
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("json", "pem"));

    // then
    assertThat(resolvedValue(result, "json")).isEqualTo(json);
    assertThat(resolvedValue(result, "pem")).isEqualTo(pem);
  }

  @Test
  void shouldHandleUtf8Value() throws IOException {
    // given — non-Latin-1 characters
    writeSecret("token", "café-中文");
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("token"));

    // then
    assertThat(resolvedValue(result, "token")).isEqualTo("café-中文");
  }

  @Test
  void shouldReturnNotFoundForHiddenName() throws IOException {
    // given: a hidden (leading-dot) file is not a listable or resolvable secret
    writeSecret(".env", "hidden");
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var entry = store.resolve(Set.of(".env")).get(".env");

    // then — resolve is symmetric with list(), which also skips hidden entries
    assertThat(entry).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) entry).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnFailedWhenSecretIsNotValidUtf8() throws IOException {
    // given: bytes that are not decodable as UTF-8 (0xC3 expects a continuation byte)
    Files.write(secretsDir.resolve("bad"), new byte[] {(byte) 0xC3, (byte) 0x28});
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var entry = store.resolve(Set.of("bad")).get("bad");

    // then: surfaced as a per-ref failure rather than silently substituting U+FFFD or throwing
    assertThat(entry).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) entry).code())
        .isEqualTo(SecretErrorCode.UNREADABLE);
  }

  @Test
  void shouldFailOnlyUnreadableRefWhileResolvingOthersInBatch() throws IOException {
    // given: one readable secret and one that cannot be decoded as UTF-8
    writeSecret("good", "value");
    Files.write(secretsDir.resolve("bad"), new byte[] {(byte) 0xC3, (byte) 0x28});
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("good", "bad"));

    // then: the unreadable secret fails on its own without aborting the batch
    assertThat(resolvedValue(result, "good")).isEqualTo("value");
    final var bad = result.get("bad");
    assertThat(bad).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) bad).code()).isEqualTo(SecretErrorCode.UNREADABLE);
  }

  @Test
  void shouldSkipFilesWithInvalidNameInList() throws IOException {
    // given — a backslash is a legal file-name char on POSIX but not a valid secret name
    writeSecret("valid", "1");
    Files.writeString(secretsDir.resolve("in\\valid"), "x", StandardCharsets.UTF_8);
    final var store = new FileBasedSecretStore(secretsDir);

    // when / then — the stray file is skipped, not propagated as an exception
    assertThat(store.list()).containsExactly("valid");
  }

  @Test
  void shouldReturnFailedForInvalidNameInResolve() {
    // given — names that are invalid as single-segment secret names
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var resultDotDot = store.resolve(Set.of(".."));
    final var resultSlash = store.resolve(Set.of("a/b"));

    // then — invalid names return Failed(INVALID_REF) rather than throwing
    assertThat(resultDotDot.get("..")).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) resultDotDot.get("..")).code())
        .isEqualTo(SecretErrorCode.INVALID_REF);
    assertThat(resultSlash.get("a/b")).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) resultSlash.get("a/b")).code())
        .isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnInvalidRefForBlankNameInResolve() {
    // given
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("   "));

    // then
    final var entry = result.get("   ");
    assertThat(entry).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) entry).code())
        .isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnInvalidRefForBackslashNameInResolve() {
    // given
    final var store = new FileBasedSecretStore(secretsDir);

    // when
    final var result = store.resolve(Set.of("a\\b"));

    // then
    final var entry = result.get("a\\b");
    assertThat(entry).isInstanceOf(SecretResolutionResult.Failed.class);
    assertThat(((SecretResolutionResult.Failed) entry).code())
        .isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldThrowWhenResolvingMissingDirectory() {
    // given
    final var store = new FileBasedSecretStore(secretsDir.resolve("nonexistent"));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("any")))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("does not exist or is not a directory");
  }

  @Test
  void shouldThrowWhenListingMissingDirectory() {
    // given
    final var store = new FileBasedSecretStore(secretsDir.resolve("nonexistent"));

    // when / then
    assertThatThrownBy(store::list)
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("does not exist or is not a directory");
  }

  @Test
  void shouldThrowWhenPathIsFileNotDirectory() throws IOException {
    // given — the configured path points at a regular file
    final var file = secretsDir.resolve("not-a-dir");
    Files.writeString(file, "x", StandardCharsets.UTF_8);
    final var store = new FileBasedSecretStore(file);

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("any")))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("does not exist or is not a directory");
    assertThatThrownBy(store::list)
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("does not exist or is not a directory");
  }

  @Test
  void shouldBeThreadSafe() throws IOException, InterruptedException {
    // given
    writeSecret("secret", "value");
    final var store = new FileBasedSecretStore(secretsDir);
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
                final var result = store.resolve(Set.of("secret"));
                if (!(result.get("secret") instanceof SecretResolutionResult.Resolved)) {
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
}
