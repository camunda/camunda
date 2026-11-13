/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.common.BackupReadRateLimiter;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

final class FileSetManagerRateLimitTest {

  private static final int UNCOMPRESSED_FILE_SIZE = 64 * 1024;
  // larger than the 8 MiB threshold above which files are compressed
  private static final int COMPRESSED_FILE_SIZE = 9 * 1024 * 1024;
  private static final long MIB = 1024 * 1024;

  private final Map<String, byte[]> uploaded = new ConcurrentHashMap<>();
  private final Map<String, PutObjectRequest> requests = new ConcurrentHashMap<>();
  private final S3AsyncClient client = mockClientStoringUploads();
  private final AtomicLong bytesReadThroughLimiter = new AtomicLong();

  @TempDir private Path directory;

  @Test
  void shouldReadEveryByteOfAnUncompressedFileThroughTheLimiter() throws Exception {
    // given
    final var content = randomBytes(UNCOMPRESSED_FILE_SIZE);
    final var file = Files.write(directory.resolve("segment-1.log"), content);
    final var manager =
        new FileSetManager(
            client, new S3BackupConfig.Builder().withBucketName("b").build(), countingLimiter());

    // when
    manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file))).join();

    // then
    assertThat(uploaded.get("prefix/segment-1.log")).isEqualTo(content);
    assertThat(bytesReadThroughLimiter).hasValue(UNCOMPRESSED_FILE_SIZE);
  }

  @Test
  void shouldReadEveryByteToCompressAFileThroughTheLimiter() throws Exception {
    // given
    final var file =
        Files.write(directory.resolve("segment-1.log"), randomBytes(COMPRESSED_FILE_SIZE));
    final var config =
        new S3BackupConfig.Builder().withBucketName("b").withCompressionAlgorithm("zstd").build();
    final var manager = new FileSetManager(client, config, countingLimiter());

    // when
    manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file))).join();

    // then
    assertThat(uploaded).containsKey("prefix/segment-1.log");
    assertThat(bytesReadThroughLimiter).hasValue(COMPRESSED_FILE_SIZE);
  }

  @Test
  void shouldNotFailLimitedUploadThatWaitsLongerThanTheApiCallTimeoutForTheReadLimit()
      throws Exception {
    // given - another backup of the broker has used up the budget of the next 2.5 seconds; it read
    // all of it at once, as only the reads after it have to wait
    final var limiter = BackupReadRateLimiter.of(MIB);
    final var otherBackup = new byte[(int) (2.5 * MIB)];
    assertThat(limiter.wrap(new ByteArrayInputStream(otherBackup)).read(otherBackup))
        .isEqualTo(otherBackup.length);
    final var content = randomBytes(UNCOMPRESSED_FILE_SIZE);
    final var file = Files.write(directory.resolve("segment-1.log"), content);
    final var manager = new FileSetManager(client, configWithApiCallTimeout(1), limiter);

    // when
    final var saved = manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file)));

    // then - the upload takes more than twice the timeout, but only because it waits its turn
    assertThat(saved).succeedsWithin(Duration.ofSeconds(30));
    assertThat(uploaded.get("prefix/segment-1.log")).isEqualTo(content);
  }

  @Test
  void shouldFailLimitedUploadThatExceedsTheApiCallTimeoutWithoutWaitingForTheReadLimit()
      throws Exception {
    // given - an upload that never completes, and a limit that never makes it wait
    final var stuckUpload = new CompletableFuture<PutObjectResponse>();
    final var stuckClient = mock(S3AsyncClient.class);
    when(stuckClient.putObject(anyPutObjectRequest(), any(AsyncRequestBody.class)))
        .thenReturn(stuckUpload);
    final var file =
        Files.write(directory.resolve("segment-1.log"), randomBytes(UNCOMPRESSED_FILE_SIZE));
    final var manager =
        new FileSetManager(
            stuckClient,
            new S3BackupConfig.Builder()
                .withBucketName("b")
                .withApiCallTimeout(Duration.ofMillis(100))
                .build(),
            BackupReadRateLimiter.of(1L << 40));

    // when
    final var saved = manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file)));

    // then
    assertThat(saved)
        .failsWithin(Duration.ofSeconds(30))
        .withThrowableThat()
        .withRootCauseInstanceOf(ApiCallTimeoutException.class);
    assertThat(stuckUpload).isCancelled();
  }

  @Test
  void shouldReplaceTheSdkTimeoutOfLimitedUploads() throws Exception {
    // given
    final var file =
        Files.write(directory.resolve("segment-1.log"), randomBytes(UNCOMPRESSED_FILE_SIZE));
    final var manager =
        new FileSetManager(
            client, configWithApiCallTimeout(180), BackupReadRateLimiter.of(1L << 40));

    // when
    manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file))).join();

    // then - the SDK would count waits for the read limit, so its timeout must never be the one
    // that ends the upload
    assertThat(apiCallTimeoutOf("prefix/segment-1.log"))
        .hasValueSatisfying(
            timeout -> assertThat(timeout).isGreaterThanOrEqualTo(Duration.ofDays(1)));
  }

  @Test
  void shouldKeepApiCallTimeoutOfUploadsWhenNotLimited() throws Exception {
    // given
    final var file =
        Files.write(directory.resolve("segment-1.log"), randomBytes(UNCOMPRESSED_FILE_SIZE));
    final var manager =
        new FileSetManager(
            client, configWithApiCallTimeout(180), BackupReadRateLimiter.unlimited());

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
    final var manager = new FileSetManager(client, config, BackupReadRateLimiter.of(1L << 40));

    // when
    manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file))).join();

    // then
    assertThat(apiCallTimeoutOf("prefix/segment-1.log")).isEmpty();
  }

  @Test
  void shouldReturnTheResultOfAnUploadThatCompletesInTime() {
    // given
    final var upload = CompletableFuture.completedFuture("done");

    // when
    final var result = FileSetManager.joinWithin(upload, Duration.ofSeconds(30), () -> ZERO);

    // then
    assertThat(result).isEqualTo("done");
  }

  @Test
  void shouldFailWithTheCauseOfAFailedUpload() {
    // given
    final var failure = new IllegalStateException("upload failed");
    final var upload = CompletableFuture.<String>failedFuture(failure);

    // when - then
    assertThatThrownBy(() -> FileSetManager.joinWithin(upload, Duration.ofSeconds(30), () -> ZERO))
        .isInstanceOf(CompletionException.class)
        .hasCause(failure);
  }

  @Test
  void shouldTimeOutAnUploadThatDoesNotWaitForTheReadLimit() {
    // given
    final var upload = new CompletableFuture<String>();
    final long start = System.nanoTime();

    // when - then
    assertThatThrownBy(() -> FileSetManager.joinWithin(upload, Duration.ofMillis(50), () -> ZERO))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(ApiCallTimeoutException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(ofMillis(50));
    assertThat(upload).isCancelled();
  }

  @Test
  void shouldNotCountWaitsForTheReadLimitTowardsTheTimeout() {
    // given - an upload that starts to wait for the read limit after its timeout started
    final var upload = new CompletableFuture<String>();
    final var waited = new AtomicReference<>(ZERO);
    final long start = System.nanoTime();

    // when - then
    assertThatThrownBy(
            () ->
                FileSetManager.joinWithin(
                    upload, Duration.ofMillis(50), () -> waited.getAndSet(ofMillis(250))))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(ApiCallTimeoutException.class)
        .hasMessageContaining("PT0.25S");
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(ofMillis(300));
  }

  @Test
  void shouldReturnTheResultOfAnUploadThatIsOnlySlowBecauseOfTheReadLimit() {
    // given - an upload that takes three times its timeout, most of it waiting for the read limit
    final var upload =
        CompletableFuture.supplyAsync(
            () -> "done", CompletableFuture.delayedExecutor(150, TimeUnit.MILLISECONDS));

    // when
    final var result =
        FileSetManager.joinWithin(upload, Duration.ofMillis(50), () -> Duration.ofSeconds(30));

    // then
    assertThat(result).isEqualTo("done");
  }

  private static S3BackupConfig configWithApiCallTimeout(final int seconds) {
    return new S3BackupConfig.Builder()
        .withBucketName("b")
        .withApiCallTimeout(Duration.ofSeconds(seconds))
        .build();
  }

  /** A limiter that never waits and counts the bytes that are read through its streams. */
  private BackupReadRateLimiter countingLimiter() {
    final var realLimiter = BackupReadRateLimiter.of(1L << 40);
    final var limiter = mock(BackupReadRateLimiter.class);
    when(limiter.wrap(any()))
        .then(
            invocation ->
                realLimiter.wrap(
                    new FilterInputStream(invocation.getArgument(0)) {
                      @Override
                      public int read(final byte[] buffer, final int offset, final int length)
                          throws IOException {
                        final int count = super.read(buffer, offset, length);
                        if (count > 0) {
                          bytesReadThroughLimiter.addAndGet(count);
                        }
                        return count;
                      }

                      @Override
                      public int read() throws IOException {
                        final int value = super.read();
                        if (value != -1) {
                          bytesReadThroughLimiter.incrementAndGet();
                        }
                        return value;
                      }
                    }));
    return limiter;
  }

  private Optional<Duration> apiCallTimeoutOf(final String key) {
    return requests
        .get(key)
        .overrideConfiguration()
        .flatMap(AwsRequestOverrideConfiguration::apiCallTimeout);
  }

  @SuppressWarnings("unchecked")
  private static Consumer<PutObjectRequest.Builder> anyPutObjectRequest() {
    return any(Consumer.class);
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
