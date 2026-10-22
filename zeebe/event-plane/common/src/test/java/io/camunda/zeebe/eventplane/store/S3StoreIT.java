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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/** The {@link S3Store} against LocalStack, as the S3 backup store's tests do. */
@Testcontainers
final class S3StoreIT extends ObjectStoreContractTest {

  private static final String BUCKET = "event-plane-" + UUID.randomUUID();

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices(Service.S3);

  @BeforeAll
  static void createBucket() {
    try (final var client = S3Store.buildClient(config(null))) {
      client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }
  }

  @Override
  protected ObjectStore openStore() {
    return new S3Store(config(UUID.randomUUID().toString()));
  }

  @Test
  void shouldHaveTheBucketAndBasePathAsItsRoot() throws IOException {
    // given
    try (final var base = new S3Store(config("a/b"));
        final var slashes = new S3Store(config("/a/b/"));
        final var none = new S3Store(config(null))) {
      // when, then
      assertThat(base.root()).isEqualTo(URI.create("s3://" + BUCKET + "/a/b/"));
      assertThat(slashes.root()).isEqualTo(base.root());
      assertThat(none.root()).isEqualTo(URI.create("s3://" + BUCKET + "/"));
      assertThat(base.uri("c/t/3.json"))
          .isEqualTo(URI.create("s3://" + BUCKET + "/a/b/c/t/3.json"));
    }
  }

  @Test
  void shouldKeepEveryKeyBelowTheBasePath() throws IOException {
    // given two stores in one bucket, with different base paths
    try (final var one = new S3Store(config("shared/one"));
        final var two = new S3Store(config("shared/two"));
        final var all = new S3Store(config("shared"))) {
      // when
      one.put("c/1", bytes("1"));
      two.put("c/2", bytes("2"));

      // then
      assertThat(keys(one.list("", null, 10))).containsExactly("c/1");
      assertThat(keys(two.list("", null, 10))).containsExactly("c/2");
      assertThat(keys(all.list("", null, 10))).containsExactly("one/c/1", "two/c/2");
      assertThat(all.listPrefixes("")).containsExactly("one/", "two/");
      assertThat(one.get("c/2")).isEmpty();
      assertThat(all.get("two/c/2")).hasValue(bytes("2"));
    }
  }

  @Test
  void shouldPageThroughListings() throws IOException {
    // given a store that asks for 2 keys at a time, and 7 objects in 5 directories
    try (final var small = new S3Store(config(UUID.randomUUID().toString()), 2)) {
      final var expected = new ArrayList<String>();
      for (int i = 0; i < 5; i++) {
        for (int j = 0; j < (i < 2 ? 2 : 1); j++) {
          final var key = "d%d/object-%d".formatted(i, j);
          expected.add(key);
          small.put(key, bytes("x"));
        }
      }

      // when, then
      assertThat(keys(small.list("", null, 100))).isEqualTo(expected);
      assertThat(keys(small.list("", null, 5))).isEqualTo(expected.subList(0, 5));
      assertThat(keys(small.list("", "d1/object-0", 100))).isEqualTo(expected.subList(3, 7));
      assertThat(keys(small.list("d", "d1/object-1", 3))).isEqualTo(expected.subList(4, 7));
      assertThat(small.listPrefixes("")).containsExactly("d0/", "d1/", "d2/", "d3/", "d4/");
    }
  }

  @Test
  void shouldFailWithAnObjectStoreExceptionWhenTheBucketIsMissing() throws IOException {
    // given
    final var missing =
        new S3StoreConfig(
            "not-a-bucket-" + UUID.randomUUID(),
            null,
            S3.getRegion(),
            S3.getEndpoint(),
            S3.getAccessKey(),
            S3.getSecretKey(),
            true);

    try (final var store = new S3Store(missing)) {
      // when, then
      assertThatThrownBy(() -> store.put("a", bytes("x")))
          .isInstanceOfSatisfying(
              ObjectStoreException.class,
              e -> {
                assertThat(e.operation()).isEqualTo("put");
                assertThat(e.object()).isEqualTo(store.uri("a"));
              });
      assertThatThrownBy(() -> store.get("a")).isInstanceOf(ObjectStoreException.class);
      assertThatThrownBy(() -> store.list("", null, 10)).isInstanceOf(ObjectStoreException.class);
      assertThatThrownBy(() -> store.listPrefixes("")).isInstanceOf(ObjectStoreException.class);
    }
  }

  @Test
  void shouldOpenFromAStoreConfiguration() throws IOException {
    // given
    final var config = StoreConfig.s3(config(UUID.randomUUID().toString()));

    // when
    try (final var opened = ObjectStores.open(config)) {
      opened.put("a", bytes("x"));

      // then
      assertThat(opened).isInstanceOf(S3Store.class);
      assertThat(opened.get("a")).hasValue(bytes("x"));
    }
  }

  private static S3StoreConfig config(final String basePath) {
    return new S3StoreConfig(
        BUCKET,
        basePath,
        S3.getRegion(),
        S3.getEndpoint(),
        S3.getAccessKey(),
        S3.getSecretKey(),
        true);
  }
}
