/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher;

@ExtendWith(MockitoExtension.class)
final class S3BackupStoreDeleteTest {

  @Mock private S3AsyncClient client;

  private S3BackupStore store;
  private final BackupIdentifierImpl id = new BackupIdentifierImpl(1, 2, 3L);

  @BeforeEach
  void setUp() {
    final var config = new S3BackupConfig.Builder().withBucketName("test-bucket").build();
    store = new S3BackupStore(config, client);
  }

  @Test
  void shouldNotDeleteManifestWhenContentsDeletionFails() {
    // given - manifest absent on both new and legacy paths -> DOES_NOT_EXIST status passes guard
    when(client.getObject(any(Consumer.class), any(AsyncResponseTransformer.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                NoSuchKeyException.builder().message("no such key").build()));
    // contents new path: 1 object; legacy path: empty
    stubPaginatorCalls(
        publisherOf(S3Object.builder().key("contents/2/3/1/snapshot/file1").build()),
        emptyPublisher());
    // contents deletion fails
    when(client.deleteObjects(any(Consumer.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new RuntimeException("simulated contents deletion failure")));

    // when
    assertThatThrownBy(() -> store.delete(id).join())
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining("simulated contents deletion failure");

    // then - deleteObjects called once for contents only; manifest objects never reached
    verify(client, times(1)).deleteObjects(any(Consumer.class));
  }

  @Test
  void shouldSplitDeleteIntoMultipleBatches() {
    // given - manifest absent -> DOES_NOT_EXIST status passes guard
    when(client.getObject(any(Consumer.class), any(AsyncResponseTransformer.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                NoSuchKeyException.builder().message("no such key").build()));
    // contents new path: 1001 objects across two pages; all other paths: empty
    final var objects =
        IntStream.range(0, 2001)
            .mapToObj(i -> S3Object.builder().key("contents/2/3/1/file" + i).build())
            .toList();
    stubPaginatorCalls(publisherOf(objects), emptyPublisher(), emptyPublisher(), emptyPublisher());
    // both content batches succeed
    when(client.deleteObjects(any(Consumer.class)))
        .thenReturn(CompletableFuture.completedFuture(DeleteObjectsResponse.builder().build()));

    // when
    store.delete(id).join();

    // then - deleteObjects called thrice: two batches of 1000 then batch of 1
    verify(client, times(3)).deleteObjects(any(Consumer.class));
  }

  /** Stubs successive listObjectsV2Paginator calls to return the given publishers in order. */
  private void stubPaginatorCalls(final ListObjectsV2Publisher... publishers) {
    var stub = when(client.listObjectsV2Paginator(any(Consumer.class)));
    for (final var publisher : publishers) {
      stub = stub.thenReturn(publisher);
    }
  }

  private static ListObjectsV2Publisher publisherOf(final S3Object... objects) {
    return publisherOf(List.of(objects));
  }

  private static ListObjectsV2Publisher publisherOf(final List<S3Object> objects) {
    final var publisher = mock(ListObjectsV2Publisher.class);
    when(publisher.contents()).thenReturn(sdkPublisherOf(objects));
    return publisher;
  }

  private static ListObjectsV2Publisher emptyPublisher() {
    return publisherOf(List.of());
  }

  private static SdkPublisher<S3Object> sdkPublisherOf(final List<S3Object> objects) {
    return subscriber ->
        subscriber.onSubscribe(
            new Subscription() {
              private boolean emitted = false;

              @Override
              public void request(final long n) {
                if (!emitted) {
                  emitted = true;
                  objects.forEach(subscriber::onNext);
                  subscriber.onComplete();
                }
              }

              @Override
              public void cancel() {}
            });
  }
}
