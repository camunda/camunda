/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.util.S3TestBackupProvider;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Error;

@Testcontainers
public class S3BackupStoreBatchDeletionTest {
  public static final String ACCESS_KEY = "letmein";
  public static final String SECRET_KEY = "letmein1234";
  public static final int DEFAULT_PORT = 9000;

  @SuppressWarnings("resource")
  @Container
  private static final GenericContainer<?> S3 =
      new GenericContainer<>(DockerImageName.parse("minio/minio"))
          .withCommand("server /data")
          .withExposedPorts(DEFAULT_PORT)
          .withEnv("MINIO_ACCESS_KEY", ACCESS_KEY)
          .withEnv("MINIO_SECRET_KEY", SECRET_KEY)
          .withEnv("MINIO_DOMAIN", "localhost")
          .waitingFor(
              new HttpWaitStrategy()
                  .forPath("/minio/health/ready")
                  .forPort(DEFAULT_PORT)
                  .withStartupTimeout(Duration.ofMinutes(1)));

  private final String bucketName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  private static Stream<Arguments> basePathProvider() {
    return Stream.of(
        Arguments.of(""), Arguments.of(RandomStringUtils.randomAlphabetic(10).toLowerCase()));
  }

  @ParameterizedTest
  @MethodSource("basePathProvider")
  void shouldNotDeleteManifestOnContentException(final String basePath) throws Exception {
    // given
    final var configBuilder =
        new Builder()
            .withBucketName(bucketName)
            .withEndpoint("http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(DEFAULT_PORT)))
            .withRegion(Region.US_EAST_1.id())
            .withCredentials(ACCESS_KEY, SECRET_KEY)
            .forcePathStyleAccess(true);

    if (!basePath.isEmpty()) {
      configBuilder.withBasePath(basePath);
    }

    final var config = configBuilder.build();
    final S3AsyncClient realClient = S3BackupStore.buildClient(config);
    realClient.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();

    final S3AsyncClient spyClient = spy(realClient);
    final var store = new S3BackupStore(config, spyClient);

    final var backupId = new BackupIdentifierImpl(1, 2, 3);
    final var backupId2 = new BackupIdentifierImpl(1, 2, 5);
    final var backup = S3TestBackupProvider.simpleBackupWithId(backupId, false);
    final var backup2 = S3TestBackupProvider.simpleBackupWithId(backupId2, false);

    final var failedSegment = backup.segments().names().stream().findFirst().orElseThrow();
    final var fullSegmentPath = "2/3/1/segments/" + failedSegment;

    interceptSegmentDeletion(realClient, fullSegmentPath, spyClient);

    try {
      store.save(backup).thenCompose(ignore -> store.save(backup2)).join();

      // when
      store
          .delete(List.of(backupId, backupId2))
          // suppress exception thrown due to injected deletion failure
          .exceptionally((throwable) -> null)
          .join();

      // then
      assertThat(store.getStatus(backupId2))
          .succeedsWithin(Duration.ofSeconds(10))
          .extracting(BackupStatus::statusCode)
          .isEqualTo(BackupStatusCode.DOES_NOT_EXIST);

      assertThat(store.getStatus(backupId))
          .succeedsWithin(Duration.ofSeconds(10))
          .extracting(BackupStatus::statusCode)
          .isEqualTo(BackupStatusCode.COMPLETED);
    } finally {
      store.closeAsync().join();
      realClient.close();
    }
  }

  @SuppressWarnings("unchecked")
  private static void interceptSegmentDeletion(
      final S3AsyncClient realClient, final String fullSegmentPath, final S3AsyncClient spyClient) {
    doAnswer(
            invocation -> {
              final Consumer<DeleteObjectsRequest.Builder> requestConsumer =
                  invocation.getArgument(0);
              final DeleteObjectsRequest.Builder builder = DeleteObjectsRequest.builder();
              requestConsumer.accept(builder);
              final DeleteObjectsRequest request = builder.build();

              // Check if any of the objects to delete match the failed segment path
              final boolean containsFailedSegment =
                  request.delete().objects().stream()
                      .anyMatch(obj -> obj.key().contains(fullSegmentPath));

              if (containsFailedSegment) {
                return createFailingDeleteResponse(request, fullSegmentPath);
              }
              return realClient.deleteObjects(requestConsumer);
            })
        .when(spyClient)
        .deleteObjects(any(Consumer.class));
  }

  private static CompletableFuture<DeleteObjectsResponse> createFailingDeleteResponse(
      final DeleteObjectsRequest request, final String fullSegmentPath) {
    // Return a response with errors for objects matching the failed segment path
    final var errors =
        request.delete().objects().stream()
            .filter(obj -> obj.key().contains(fullSegmentPath))
            .map(
                obj ->
                    S3Error.builder()
                        .key(obj.key())
                        .code("AccessDenied")
                        .message("Access Denied")
                        .build())
            .toList();

    return CompletableFuture.completedFuture(
        DeleteObjectsResponse.builder().errors(errors).build());
  }
}
