/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static io.camunda.zeebe.backup.common.RateLimitedInputStream.MAX_BYTES_PER_READ;
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
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
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

  /**
   * The tests of this class run concurrently. Tests that compress create temporary files, which the
   * test that checks for leftover temporary files must not see.
   */
  private static final String COMPRESSION_TEMP_FILES = "zb-backup-compress temporary files";

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
  @ResourceLock(value = COMPRESSION_TEMP_FILES, mode = ResourceAccessMode.READ)
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
    // given - another backup of the broker has used up the budget of the next 2.5 seconds with a
    // single read, as only the reads after it have to wait
    final var limiter = BackupReadRateLimiter.of(MAX_BYTES_PER_READ * 2 / 5);
    try (final var otherBackup =
        limiter.wrap(new ByteArrayInputStream(new byte[MAX_BYTES_PER_READ]))) {
      assertThat(otherBackup.read(new byte[MAX_BYTES_PER_READ])).isEqualTo(MAX_BYTES_PER_READ);
    }
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
  @ResourceLock(COMPRESSION_TEMP_FILES)
  void shouldDeleteThePartlyCompressedFileWhenCompressionFails() throws Exception {
    // given - reading the file fails half way, e.g. because the read was interrupted while it
    // waited for the read limit
    final var file =
        Files.write(directory.resolve("segment-1.log"), randomBytes(COMPRESSED_FILE_SIZE));
    final var config =
        new S3BackupConfig.Builder().withBucketName("b").withCompressionAlgorithm("zstd").build();
    final var manager =
        new FileSetManager(client, config, failingLimiter(COMPRESSED_FILE_SIZE / 2));
    final var compressedFilesBefore = compressedTempFiles();

    // when
    final var saved = manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file)));

    // then
    assertThat(saved)
        .failsWithin(Duration.ofSeconds(30))
        .withThrowableThat()
        .withRootCauseInstanceOf(InterruptedIOException.class);
    assertThat(compressedTempFiles()).isSubsetOf(compressedFilesBefore);
    assertThat(uploaded).isEmpty();
  }

  @Test
  void shouldStopUploadsThatWaitForTheReadLimitWhenClosed() throws Exception {
    // given - an upload that would need two minutes at this rate, and has started to read
    final var file = Files.write(directory.resolve("segment-1.log"), randomBytes((int) MIB));
    final var manager =
        new FileSetManager(
            client,
            new S3BackupConfig.Builder().withBucketName("b").build(),
            countingLimiter(BackupReadRateLimiter.of(8 * 1024)));
    final var saved = manager.save("prefix/", new NamedFileSetImpl(Map.of("segment-1.log", file)));
    Awaitility.await("upload reads through the limiter")
        .atMost(Duration.ofSeconds(30))
        .until(() -> bytesReadThroughLimiter.get() > 0);

    // when
    final var closed = CompletableFuture.runAsync(manager::close);

    // then - closing does not wait for the grace period of a minute that the upload cannot use
    assertThat(closed).succeedsWithin(Duration.ofSeconds(10));
    assertThat(saved)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .withRootCauseInstanceOf(InterruptedException.class);
    assertThat(uploaded).isEmpty();
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
  void shouldLeaveTheSdkTimeoutRoomForEveryWaitOfALargeFile() {
    // given - a 3 GiB file; each of its 64 KiB reads may wait for the read limit up to the max
    // pause
    final long fileSize = 3L * 1024 * MIB;
    final var apiCallTimeout = Duration.ofSeconds(180);

    // when
    final var sdkTimeout = FileSetManager.sdkApiCallTimeout(apiCallTimeout, fileSize);

    // then - the upload only times out after the configured timeout plus all of its waits
    final long reads = fileSize / MAX_BYTES_PER_READ;
    assertThat(sdkTimeout)
        .isGreaterThanOrEqualTo(
            apiCallTimeout.plus(BackupReadRateLimiter.MAX_READ_PAUSE.multipliedBy(reads)));
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
  @ResourceLock(value = COMPRESSION_TEMP_FILES, mode = ResourceAccessMode.READ)
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

  /** A limiter whose streams fail once the given number of bytes was read from them. */
  private static BackupReadRateLimiter failingLimiter(final long failAfterBytes)
      throws IOException {
    final var realLimiter = BackupReadRateLimiter.of(1L << 40);
    final var limiter = mock(BackupReadRateLimiter.class);
    when(limiter.wrap(any()))
        .then(
            invocation ->
                realLimiter.wrap(
                    new FilterInputStream(invocation.getArgument(0)) {
                      private long read;

                      @Override
                      public int read(final byte[] buffer, final int offset, final int length)
                          throws IOException {
                        if (read >= failAfterBytes) {
                          throw new InterruptedIOException("Interrupted while limiting reads");
                        }
                        final int count = super.read(buffer, offset, length);
                        read += Math.max(0, count);
                        return count;
                      }
                    }));
    return limiter;
  }

  private static Set<Path> compressedTempFiles() throws IOException {
    try (final var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      return files
          .filter(file -> file.getFileName().toString().startsWith("zb-backup-compress-"))
          .collect(Collectors.toSet());
    }
  }

  /** A limiter that never waits and counts the bytes that are read through its streams. */
  private BackupReadRateLimiter countingLimiter() throws IOException {
    return countingLimiter(BackupReadRateLimiter.of(1L << 40));
  }

  /** Counts the bytes that are read through the streams of the given limiter. */
  private BackupReadRateLimiter countingLimiter(final BackupReadRateLimiter realLimiter)
      throws IOException {
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
