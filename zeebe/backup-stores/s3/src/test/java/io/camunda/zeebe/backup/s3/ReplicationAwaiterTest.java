/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReplicationFailed;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReplicationTimedOut;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.ConfigurationException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetBucketReplicationResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ReplicationConfiguration;
import software.amazon.awssdk.services.s3.model.ReplicationRule;
import software.amazon.awssdk.services.s3.model.ReplicationStatus;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
final class ReplicationAwaiterTest {

  private static final Duration POLL_INTERVAL = Duration.ofMillis(10);
  private static final Duration WAIT = Duration.ofSeconds(10);

  @Mock private S3AsyncClient client;

  /** Replication statuses to report per key, one per HEAD; the last one repeats. */
  private final Map<String, Queue<Optional<ReplicationStatus>>> statuses =
      new ConcurrentHashMap<>();

  /** Number of HEAD requests per key. */
  private final Map<String, Integer> heads = new ConcurrentHashMap<>();

  /** Keys whose HEAD request fails with the given exception. */
  private final Map<String, Throwable> failingHeads = new ConcurrentHashMap<>();

  @BeforeEach
  void setUp() {
    lenient()
        .when(client.headObject(any(Consumer.class)))
        .thenAnswer(
            invocation -> {
              final Consumer<HeadObjectRequest.Builder> consumer = invocation.getArgument(0);
              final var builder = HeadObjectRequest.builder();
              consumer.accept(builder);
              final var key = builder.build().key();
              heads.merge(key, 1, Integer::sum);
              final var failure = failingHeads.get(key);
              if (failure != null) {
                return CompletableFuture.failedFuture(failure);
              }
              final var queued = statuses.get(key);
              final var status = queued.size() > 1 ? queued.poll() : queued.peek();
              final var response = HeadObjectResponse.builder();
              status.ifPresent(response::replicationStatus);
              return CompletableFuture.completedFuture(response.build());
            });
  }

  @Test
  void shouldCompleteOnceAllObjectsAreReplicated() {
    // given
    final var awaiter = awaiter(WAIT);
    reportStatuses(
        "a", ReplicationStatus.PENDING, ReplicationStatus.PENDING, ReplicationStatus.COMPLETED);
    reportStatuses("b", ReplicationStatus.PENDING, ReplicationStatus.COMPLETED);

    // when
    final var replicated = awaiter.awaitReplication(List.of("a", "b"), awaiter.deadline());

    // then
    assertThat(replicated).succeedsWithin(WAIT);
    assertThat(heads).containsEntry("a", 3).containsEntry("b", 2);
  }

  @Test
  void shouldAcceptBothSpellingsOfCompletedStatus() {
    // given
    final var awaiter = awaiter(WAIT);
    reportStatuses("a", ReplicationStatus.COMPLETE);
    reportStatuses("b", ReplicationStatus.COMPLETED);

    // when
    final var replicated = awaiter.awaitReplication(List.of("a", "b"), awaiter.deadline());

    // then
    assertThat(replicated).succeedsWithin(WAIT);
  }

  @Test
  void shouldStopPollingObjectsThatAreReplicated() {
    // given
    final var awaiter = awaiter(WAIT);
    reportStatuses("replicated", ReplicationStatus.COMPLETED);
    reportStatuses(
        "slow", ReplicationStatus.PENDING, ReplicationStatus.PENDING, ReplicationStatus.COMPLETED);

    // when
    final var replicated =
        awaiter.awaitReplication(List.of("replicated", "slow"), awaiter.deadline());

    // then
    assertThat(replicated).succeedsWithin(WAIT);
    assertThat(heads).containsEntry("replicated", 1).containsEntry("slow", 3);
  }

  @Test
  void shouldFailWhenReplicationOfAnObjectFails() {
    // given
    final var awaiter = awaiter(WAIT);
    reportStatuses("a", ReplicationStatus.COMPLETED);
    reportStatuses("b", ReplicationStatus.PENDING, ReplicationStatus.FAILED);

    // when
    final var replicated = awaiter.awaitReplication(List.of("a", "b"), awaiter.deadline());

    // then
    assertThat(replicated)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(BackupReplicationFailed.class)
        .withMessageContaining("'b'");
  }

  @Test
  void shouldFailWithTheCauseWhenCheckingAnObjectFails() {
    // given
    final var awaiter = awaiter(WAIT);
    reportStatuses("a", ReplicationStatus.COMPLETED);
    final var headFailure = S3Exception.builder().message("access denied").statusCode(403).build();
    failingHeads.put("b", headFailure);

    // when
    final var replicated = awaiter.awaitReplication(List.of("a", "b"), awaiter.deadline());

    // then
    assertThat(replicated)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isSameAs(headFailure);
  }

  @Test
  void shouldFailRightAwayWhenAnObjectHasNoReplicationStatus() {
    // given
    final var awaiter = awaiter(WAIT);
    reportNoStatus("a");

    // when
    final var replicated = awaiter.awaitReplication(List.of("a"), awaiter.deadline());

    // then
    assertThat(replicated)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(BackupReplicationFailed.class)
        .withMessageContaining("no replication status");
    assertThat(heads).containsEntry("a", 1);
  }

  @Test
  void shouldTimeOutWhenObjectsStayPending() {
    // given
    final var awaiter = awaiter(Duration.ofMillis(100));
    reportStatuses("a", ReplicationStatus.COMPLETED);
    reportStatuses("b", ReplicationStatus.PENDING);

    // when
    final var replicated = awaiter.awaitReplication(List.of("a", "b"), awaiter.deadline());

    // then
    assertThat(replicated)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(BackupReplicationTimedOut.class)
        .withMessageContaining("1 objects")
        .withMessageContaining("[b]");
  }

  @Test
  void shouldCheckStatusOnceMoreWhenDeadlineHasAlreadyPassed() {
    // given
    final var awaiter = awaiter(WAIT);
    reportStatuses("a", ReplicationStatus.COMPLETED);

    // when
    final var replicated =
        awaiter.awaitReplication(List.of("a"), Instant.now().minus(Duration.ofMinutes(1)));

    // then
    assertThat(replicated).succeedsWithin(WAIT);
  }

  @Test
  void shouldAcceptBucketWithReplicationRules() {
    // given
    final var awaiter = awaiter(WAIT);
    when(client.getBucketReplication(any(Consumer.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                GetBucketReplicationResponse.builder()
                    .replicationConfiguration(
                        ReplicationConfiguration.builder()
                            .rules(ReplicationRule.builder().id("rule").build())
                            .build())
                    .build()));

    // when
    final var verified = awaiter.verifyReplicationConfigured();

    // then
    assertThat(verified).succeedsWithin(WAIT);
  }

  @Test
  void shouldRejectBucketWithoutReplicationConfiguration() {
    // given
    final var awaiter = awaiter(WAIT);
    when(client.getBucketReplication(any(Consumer.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                S3Exception.builder()
                    .statusCode(404)
                    .awsErrorDetails(
                        AwsErrorDetails.builder()
                            .errorCode(ReplicationAwaiter.REPLICATION_CONFIGURATION_NOT_FOUND)
                            .build())
                    .build()));

    // when
    final var verified = awaiter.verifyReplicationConfigured();

    // then
    assertThat(verified)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(ConfigurationException.class)
        .withMessageContaining("has no replication configured");
  }

  @Test
  void shouldRejectBucketWithoutReplicationConfigurationWhenTheFailureIsWrapped() {
    // given the async client reports the failure wrapped in a CompletionException
    final var awaiter = awaiter(WAIT);
    final var notFound =
        S3Exception.builder()
            .statusCode(404)
            .awsErrorDetails(
                AwsErrorDetails.builder()
                    .errorCode(ReplicationAwaiter.REPLICATION_CONFIGURATION_NOT_FOUND)
                    .build())
            .build();
    when(client.getBucketReplication(any(Consumer.class)))
        .thenReturn(CompletableFuture.failedFuture(new CompletionException(notFound)));

    // when
    final var verified = awaiter.verifyReplicationConfigured();

    // then
    assertThat(verified)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(ConfigurationException.class)
        .withMessageContaining("has no replication configured");
  }

  @Test
  void shouldRejectReplicationConfigurationWithoutRules() {
    // given
    final var awaiter = awaiter(WAIT);
    when(client.getBucketReplication(any(Consumer.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                GetBucketReplicationResponse.builder()
                    .replicationConfiguration(ReplicationConfiguration.builder().build())
                    .build()));

    // when
    final var verified = awaiter.verifyReplicationConfigured();

    // then
    assertThat(verified)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(ConfigurationException.class)
        .withMessageContaining("has no replication configured");
  }

  @Test
  void shouldRejectBucketWhenReplicationConfigurationCannotBeRead() {
    // given
    final var awaiter = awaiter(WAIT);
    when(client.getBucketReplication(any(Consumer.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                S3Exception.builder()
                    .statusCode(403)
                    .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").build())
                    .build()));

    // when
    final var verified = awaiter.verifyReplicationConfigured();

    // then
    assertThat(verified)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(ConfigurationException.class)
        .withMessageContaining("Failed to read the replication configuration");
  }

  private ReplicationAwaiter awaiter(final Duration timeout) {
    final var config =
        new S3BackupConfig.Builder()
            .withBucketName("bucket")
            .withWaitForReplication(true)
            .withReplicationPollInterval(POLL_INTERVAL)
            .withReplicationTimeout(timeout)
            .build();
    return new ReplicationAwaiter(client, config);
  }

  private void reportStatuses(final String key, final ReplicationStatus... keyStatuses) {
    final var queue = new ArrayDeque<Optional<ReplicationStatus>>();
    for (final var status : keyStatuses) {
      queue.add(Optional.of(status));
    }
    statuses.put(key, queue);
  }

  private void reportNoStatus(final String key) {
    statuses.put(key, new ArrayDeque<>(List.of(Optional.empty())));
  }
}
