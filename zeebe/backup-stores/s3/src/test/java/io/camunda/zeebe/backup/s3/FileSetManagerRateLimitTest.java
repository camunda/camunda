/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.common.BackupReadRateLimiter;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

final class FileSetManagerRateLimitTest {

  private static final int UNCOMPRESSED_FILE_SIZE = 64 * 1024;
  // larger than the 8 MiB threshold above which files are compressed
  private static final int COMPRESSED_FILE_SIZE = 9 * 1024 * 1024;

  private final Map<String, byte[]> uploaded = new ConcurrentHashMap<>();
  private final Map<String, PutObjectRequest> requests = new ConcurrentHashMap<>();
  private final S3AsyncClient client = mockClientStoringUploads();
  // a real limiter whose rate never makes a test wait; the spy records what was paid for
  private final BackupReadRateLimiter limiter = spy(BackupReadRateLimiter.shared(1L << 40));

  @TempDir private Path directory;

  @Test
  void shouldPayForEveryByteOfAnUncompressedFile() throws Exception {
    // given
    final var content = randomBytes(UNCOMPRESSED_FILE_SIZE);
    final var file = Files.write(directory.resolve("segment-1.log"), content);
    final var manager =
        new FileSetManager(
            client, new S3BackupConfig.Builder().withBucketName("b").build(), limiter);

    // when
    manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file))).join();

    // then
    assertThat(uploaded.get("prefix/segment-1.log")).isEqualTo(content);
    assertThat(paidBytes()).isEqualTo(UNCOMPRESSED_FILE_SIZE);
  }

  @Test
  void shouldExtendApiCallTimeoutOfLimitedUploadsByTheTimeTheLimitAdds() throws Exception {
    // given - 25 concurrent uploads (50 connections / 2) share a budget of 1 MiB/s
    final var file =
        Files.write(directory.resolve("segment-1.log"), randomBytes(UNCOMPRESSED_FILE_SIZE));
    final var config =
        new S3BackupConfig.Builder()
            .withBucketName("b")
            .withApiCallTimeout(Duration.ofSeconds(180))
            .build();
    final var manager = new FileSetManager(client, config, nonWaitingLimiter(1024 * 1024));

    // when
    manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file))).join();

    // then - 64 KiB * 25 / 1 MiB/s = 1.5625s on top of the configured timeout
    assertThat(apiCallTimeoutOf("prefix/segment-1.log"))
        .hasValue(Duration.ofSeconds(180).plusMillis(1_563));
  }

  @Test
  void shouldKeepApiCallTimeoutOfUploadsWhenNotLimited() throws Exception {
    // given
    final var file =
        Files.write(directory.resolve("segment-1.log"), randomBytes(UNCOMPRESSED_FILE_SIZE));
    final var config =
        new S3BackupConfig.Builder()
            .withBucketName("b")
            .withApiCallTimeout(Duration.ofSeconds(180))
            .build();
    final var manager = new FileSetManager(client, config, BackupReadRateLimiter.UNLIMITED);

    // when
    manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file))).join();

    // then - the client-wide timeout applies
    assertThat(apiCallTimeoutOf("prefix/segment-1.log")).isEmpty();
  }

  @Test
  void shouldKeepApiCallTimeoutOfCompressedUploads() throws Exception {
    // given - the limited read happens while compressing, before the upload starts
    final var file =
        Files.write(directory.resolve("segment-1.log"), randomBytes(COMPRESSED_FILE_SIZE));
    final var config =
        new S3BackupConfig.Builder()
            .withBucketName("b")
            .withCompressionAlgorithm("zstd")
            .withApiCallTimeout(Duration.ofSeconds(180))
            .build();
    final var manager = new FileSetManager(client, config, nonWaitingLimiter(1024 * 1024));

    // when
    manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file))).join();

    // then
    assertThat(apiCallTimeoutOf("prefix/segment-1.log")).isEmpty();
  }

  /** A limiter that reports the given rate but passes reads through without waiting. */
  private static BackupReadRateLimiter nonWaitingLimiter(final long bytesPerSecond) {
    final var limiter = mock(BackupReadRateLimiter.class);
    when(limiter.bytesPerSecond()).thenReturn(bytesPerSecond);
    when(limiter.wrap(any())).then(returnsFirstArg());
    return limiter;
  }

  private Optional<Duration> apiCallTimeoutOf(final String key) {
    return requests
        .get(key)
        .overrideConfiguration()
        .flatMap(AwsRequestOverrideConfiguration::apiCallTimeout);
  }

  @Test
  void shouldPayForEveryByteReadToCompressAFile() throws Exception {
    // given
    final var file =
        Files.write(directory.resolve("segment-1.log"), randomBytes(COMPRESSED_FILE_SIZE));
    final var config =
        new S3BackupConfig.Builder().withBucketName("b").withCompressionAlgorithm("zstd").build();
    final var manager = new FileSetManager(client, config, limiter);

    // when
    manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file))).join();

    // then
    assertThat(uploaded).containsKey("prefix/segment-1.log");
    assertThat(paidBytes()).isEqualTo(COMPRESSED_FILE_SIZE);
  }

  private long paidBytes() throws InterruptedException {
    final var bytes = ArgumentCaptor.forClass(Long.class);
    verify(limiter, atLeastOnce()).acquire(bytes.capture());
    return bytes.getAllValues().stream().mapToLong(Long::longValue).sum();
  }

  private static byte[] randomBytes(final int size) {
    final var bytes = new byte[size];
    new Random(42).nextBytes(bytes);
    return bytes;
  }

  @SuppressWarnings("unchecked")
  private S3AsyncClient mockClientStoringUploads() {
    final var mock = mock(S3AsyncClient.class);
    when(mock.putObject(any(Consumer.class), any(AsyncRequestBody.class)))
        .thenAnswer(
            invocation -> {
              final var request = PutObjectRequest.builder();
              invocation.<Consumer<PutObjectRequest.Builder>>getArgument(0).accept(request);
              final AsyncRequestBody body = invocation.getArgument(1);
              return drain(body)
                  .thenApply(
                      bytes -> {
                        final var built = request.build();
                        uploaded.put(built.key(), bytes);
                        requests.put(built.key(), built);
                        return PutObjectResponse.builder().build();
                      });
            });
    return mock;
  }

  private static CompletableFuture<byte[]> drain(final AsyncRequestBody body) {
    final var result = new CompletableFuture<byte[]>();
    final var content = new ByteArrayOutputStream();
    body.subscribe(
        new Subscriber<>() {
          @Override
          public void onSubscribe(final Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(final ByteBuffer buffer) {
            final var bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            content.writeBytes(bytes);
          }

          @Override
          public void onError(final Throwable error) {
            result.completeExceptionally(error);
          }

          @Override
          public void onComplete() {
            result.complete(content.toByteArray());
          }
        });
    return result;
  }
}
