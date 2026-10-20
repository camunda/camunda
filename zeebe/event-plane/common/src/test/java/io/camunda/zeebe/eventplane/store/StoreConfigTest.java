/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.store.StoreConfig.Type;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StoreConfigTest {

  @TempDir private Path directory;

  @Test
  void shouldOpenAFilesystemStoreAtThePath() throws IOException {
    // given
    final var config = StoreConfig.filesystem(directory);

    // when
    try (final var store = ObjectStores.open(config)) {
      store.put("a/b.json", new byte[] {1, 2, 3});

      // then
      assertThat(store).isInstanceOf(FileSystemStore.class);
      assertThat(directory.resolve("a/b.json")).hasBinaryContent(new byte[] {1, 2, 3});
      assertThat(store.root()).isEqualTo(new FileSystemStore(directory).root());
    }
  }

  @Test
  void shouldOpenAnS3StoreWithoutAnyRequest() throws IOException {
    // given a bucket and endpoint that nobody listens on
    final var config =
        new StoreConfig(
            Type.S3,
            null,
            "my-bucket",
            "base/path",
            "us-east-1",
            URI.create("http://localhost:1"),
            "key",
            "secret",
            true);

    // when
    try (final var store = ObjectStores.open(config)) {
      // then
      assertThat(store).isInstanceOf(S3Store.class);
      assertThat(store.root()).isEqualTo(URI.create("s3://my-bucket/base/path/"));
    }
  }

  @Test
  void shouldIgnoreTheSettingsOfTheOtherType() throws IOException {
    // given
    final var filesystem =
        new StoreConfig(
            Type.FILESYSTEM,
            directory,
            "bucket",
            "base",
            "eu",
            URI.create("http://x"),
            "k",
            "s",
            true);
    final var s3 =
        new StoreConfig(Type.S3, directory, "bucket", null, "us-east-1", null, null, null, false);

    // when, then
    try (final var store = ObjectStores.open(filesystem);
        final var other = ObjectStores.open(s3)) {
      assertThat(store).isInstanceOf(FileSystemStore.class);
      assertThat(other).isInstanceOf(S3Store.class);
    }
  }

  @Test
  void shouldRequireAPathForAFilesystemStore() {
    // when, then
    assertThatThrownBy(
            () -> new StoreConfig(Type.FILESYSTEM, null, "b", null, null, null, null, null, false))
        .isInstanceOfSatisfying(
            InvalidStoreConfigException.class, e -> assertThat(e.setting()).isEqualTo("path"))
        .hasMessage("Expected path to be set for a filesystem store, but got none");
  }

  @Test
  void shouldRequireABucketForAnS3Store() {
    // when, then
    assertThatThrownBy(
            () -> new StoreConfig(Type.S3, directory, null, null, null, null, null, null, false))
        .isInstanceOfSatisfying(
            InvalidStoreConfigException.class, e -> assertThat(e.setting()).isEqualTo("bucket"))
        .hasMessage("Expected bucket to be set for an S3 store, but got none");
    assertThatThrownBy(
            () -> new StoreConfig(Type.S3, directory, " ", null, null, null, null, null, false))
        .isInstanceOf(InvalidStoreConfigException.class);
  }

  @Test
  void shouldRequireBothKeysOrNeither() {
    // when, then
    assertThatThrownBy(() -> new S3StoreConfig("b", null, null, null, "access", null, false))
        .isInstanceOf(InvalidStoreConfigException.class)
        .hasMessage(
            "Expected accessKeyId and secretAccessKey to be both set or both unset, but got only one");
    assertThatThrownBy(() -> new S3StoreConfig("b", null, null, null, null, "secret", false))
        .isInstanceOf(InvalidStoreConfigException.class);
    assertThatThrownBy(() -> new S3StoreConfig(" ", null, null, null, null, null, false))
        .isInstanceOf(InvalidStoreConfigException.class);
  }

  @Test
  void shouldReadTheTypeByItsNameInAnyCase() {
    // when, then
    assertThat(Type.of("filesystem")).isEqualTo(Type.FILESYSTEM);
    assertThat(Type.of("S3")).isEqualTo(Type.S3);
    assertThat(Type.of("FileSystem")).isEqualTo(Type.FILESYSTEM);
    assertThatThrownBy(() -> Type.of("gcs"))
        .isInstanceOf(InvalidStoreConfigException.class)
        .hasMessage("Expected type to be filesystem or s3, but got gcs");
  }

  @Test
  void shouldConvertBetweenTheTwoKindsOfConfiguration() {
    // given
    final var s3 =
        new S3StoreConfig(
            "b", "base", "eu-west-1", URI.create("http://minio:9000"), "k", "s", true);

    // when
    final var config = StoreConfig.s3(s3);

    // then
    assertThat(config.type()).isEqualTo(Type.S3);
    assertThat(config.toS3()).isEqualTo(s3);
  }

  @Test
  void shouldNotPrintTheSecretAccessKey() {
    // given
    final var s3 = new S3StoreConfig("b", null, null, null, "access-key", "very-secret", false);

    // when, then
    assertThat(s3.toString()).contains("access-key").doesNotContain("very-secret").contains("***");
    assertThat(StoreConfig.s3(s3).toString())
        .contains("access-key")
        .doesNotContain("very-secret")
        .contains("***");
    assertThat(new S3StoreConfig("b", null, null, null, null, null, false).toString())
        .contains("secretAccessKey=null");
  }

  @Test
  void shouldPutAllKeysBelowTheBasePathWithoutSlashesAtItsEnds() {
    // when, then
    assertThat(new S3StoreConfig("b", null, null, null, null, null, false).keyPrefix()).isEmpty();
    assertThat(new S3StoreConfig("b", "", null, null, null, null, false).keyPrefix()).isEmpty();
    assertThat(new S3StoreConfig("b", "/", null, null, null, null, false).keyPrefix()).isEmpty();
    assertThat(new S3StoreConfig("b", "a", null, null, null, null, false).keyPrefix())
        .isEqualTo("a/");
    assertThat(new S3StoreConfig("b", "/a/b//", null, null, null, null, false).keyPrefix())
        .isEqualTo("a/b/");
  }
}
