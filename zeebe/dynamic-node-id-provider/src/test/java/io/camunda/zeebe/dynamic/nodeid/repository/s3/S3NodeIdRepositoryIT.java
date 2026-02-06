/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.repository.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.dynamic.nodeid.ControlledInstantSource;
import io.camunda.zeebe.dynamic.nodeid.Lease;
import io.camunda.zeebe.dynamic.nodeid.Lease.VersionMappings;
import io.camunda.zeebe.dynamic.nodeid.NodeInstance;
import io.camunda.zeebe.dynamic.nodeid.StoredRestoreStatus.RestoreStatus;
import io.camunda.zeebe.dynamic.nodeid.Version;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.Config;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig.Credentials;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Testcontainers
class S3NodeIdRepositoryIT {

  private static final Duration EXPIRY_DURATION = Duration.ofSeconds(5);

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices(Service.S3)
          .withEnv("LS_LOG", "trace");

  @AutoClose private static S3Client client;
  S3NodeIdRepository.Config config;
  @AutoClose private S3NodeIdRepository repository;
  private String taskId;
  private ControlledInstantSource clock;

  @BeforeAll
  static void setUpAll() {
    client =
        S3NodeIdRepository.buildClient(
            new S3ClientConfig(
                Optional.of(new Credentials(S3.getAccessKey(), S3.getSecretKey())),
                Optional.of(Region.of(S3.getRegion())),
                Optional.of(S3.getEndpoint())));
  }

  @BeforeEach
  void setUp() {
    final var bucketName = UUID.randomUUID().toString();
    taskId = UUID.randomUUID().toString();
    config = new Config(bucketName, EXPIRY_DURATION, Duration.ofMinutes(2));
    client.createBucket(b -> b.bucket(config.bucketName()));
  }

  @Test
  void shouldInitializeAllFiles() {
    // given
    repository = fixed(Clock.systemUTC().millis());

    // when
    repository.initialize(2);

    // then
    for (int i = 0; i < 2; i++) {
      final var lease = repository.getLease(i);
      assertThat(lease).isInstanceOf(StoredLease.Uninitialized.class);
      assertThat(lease.eTag()).isNotEmpty();
    }
  }

  @Test
  void shouldAcquireFirstLease() {
    // given
    repository = fixed(Clock.systemUTC().millis());
    repository.initialize(2);

    // when
    final var uninitialized = repository.getLease(0);
    final var toAcquire = uninitialized.acquireInitialLease(taskId, clock, EXPIRY_DURATION);
    assertThat(toAcquire).isPresent();
    assertThat(uninitialized.eTag()).isNotEmpty();
    final var acquired = repository.acquire(toAcquire.get(), uninitialized.eTag());

    // then
    final var expectedNodeInstance = new NodeInstance(0, Version.of(1));
    assertThat(acquired.node()).isEqualTo(expectedNodeInstance);
    assertThat(acquired.eTag()).isNotEqualTo(uninitialized.eTag());
    assertThat(acquired.lease())
        .isEqualTo(
            new Lease(
                taskId,
                clock.millis() + EXPIRY_DURATION.toMillis(),
                expectedNodeInstance,
                VersionMappings.of(expectedNodeInstance)));
  }

  @Test
  void shouldAcquireOneLeaseWhenEtagMatches() {
    // given
    final var now = Clock.systemUTC().millis();
    repository = fixed(now);
    repository.initialize(3);
    final var id = 2;
    // when
    final var lease = repository.getLease(id);

    final var toAcquire = lease.acquireInitialLease(taskId, clock, EXPIRY_DURATION).get();
    final var acquired = repository.acquire(toAcquire, lease.eTag());
    final var fromGet = repository.getLease(id);

    // then
    assertThat(fromGet).isEqualTo(acquired);
    assertThat(acquired.lease()).isEqualTo(toAcquire);
    final var expectedNodeInstance = new NodeInstance(id, Version.of(1));
    assertThat(acquired.node()).isEqualTo(expectedNodeInstance);
    assertThat(acquired.lease())
        .isEqualTo(
            new Lease(
                taskId,
                clock.millis() + EXPIRY_DURATION.toMillis(),
                expectedNodeInstance,
                VersionMappings.of(expectedNodeInstance)));
    assertThat(acquired.eTag()).isNotEmpty();
    final var metadata = acquired.metadata();
    assertThat(metadata.asMap()).isNotEmpty();
    assertThat(metadata.task())
        .isPresent()
        .hasValueSatisfying(t -> assertThat(t).isEqualTo(taskId));
    assertThat(metadata.version()).isEqualTo(Version.of(1));
  }

  @Test
  void shouldNotAcquireAnExpiredLease() {
    // given
    final var now = Clock.systemUTC().millis();
    repository = fixed(now);
    repository.initialize(3);
    final var id = 2;

    // when
    final var lease = repository.getLease(id);

    final var toAcquire =
        new Lease(
            taskId, now - 10000L, new NodeInstance(id, Version.of(1)), VersionMappings.empty());
    assertThatThrownBy(() -> repository.acquire(toAcquire, lease.eTag()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not valid anymore");
  }

  @Test
  void shouldNotAcquireOneLeaseWhenETagMismatch() {
    // given
    final var now = Clock.systemUTC().millis();
    repository = fixed(now);
    repository.initialize(1);
    final var id = 0;
    // when
    final var lease = repository.getLease(id);

    final var toAcquire = lease.acquireInitialLease(taskId, clock, EXPIRY_DURATION).get();

    // then
    assertThatThrownBy(() -> repository.acquire(toAcquire, "10298301928309128"))
        .isInstanceOf(S3Exception.class)
        .hasMessageContaining("At least one of the pre-conditions you specified did not hold");
  }

  @Test
  void shouldReleaseALeaseCorrectly() {
    // given
    final var now = Clock.systemUTC().millis();
    repository = fixed(now);
    repository.initialize(3);
    final var id = 2;
    final var lease = repository.getLease(id);
    final var toAcquire = lease.acquireInitialLease(taskId, clock, EXPIRY_DURATION).get();
    final var acquired = repository.acquire(toAcquire, lease.eTag());

    // when
    repository.release(acquired);

    // then
    final var afterRelease = repository.getLease(id);
    assertThat(afterRelease)
        .isInstanceOf(StoredLease.Uninitialized.class)
        .returns(acquired.node(), StoredLease::node);
  }

  @Test
  void shouldCloseS3Client() throws Exception {
    // given
    final var clientMock = Mockito.mock(S3Client.class);
    repository = new S3NodeIdRepository(clientMock, config, Clock.systemUTC(), true);
    // when
    repository.close();

    // then
    verify(clientMock).close();
  }

  @Test
  void shouldReturnNullWhenRestoreStatusNotInitialized() {
    // given
    final long restoreId = 123L;
    repository = fixed(Clock.systemUTC().millis());

    // when
    final var restoreStatus = repository.getRestoreStatus(restoreId);

    // then
    assertThat(restoreStatus).isNull();
  }

  @Test
  void shouldUpdateAndGetRestoreStatus() {
    // given
    final long restoreId = 123L;
    repository = fixed(Clock.systemUTC().millis());
    final var restoreStatus = new RestoreStatus(restoreId, Set.of());

    // when
    repository.updateRestoreStatus(restoreStatus, null);
    final var storedRestoreStatus = repository.getRestoreStatus(restoreId);

    // then
    assertThat(storedRestoreStatus).isNotNull();
    assertThat(storedRestoreStatus.restoreStatus()).isEqualTo(restoreStatus);
    assertThat(storedRestoreStatus.etag()).isNotEmpty();
  }

  @Test
  void shouldUpdateRestoreStatusWithRestoredNodes() {
    // given
    final long restoreId = 123L;
    repository = fixed(Clock.systemUTC().millis());
    final var initialStatus = new RestoreStatus(restoreId, Set.of());
    repository.updateRestoreStatus(initialStatus, null);
    final var storedInitial = repository.getRestoreStatus(restoreId);

    // when
    final var updatedStatus = new RestoreStatus(restoreId, Set.of(0, 1));
    repository.updateRestoreStatus(updatedStatus, storedInitial.etag());
    final var storedUpdated = repository.getRestoreStatus(restoreId);

    // then
    assertThat(storedUpdated).isNotNull();
    assertThat(storedUpdated.restoreStatus().restoredNodes()).containsExactlyInAnyOrder(0, 1);
    assertThat(storedUpdated.etag()).isNotEqualTo(storedInitial.etag());
  }

  @Test
  void shouldFailToUpdateRestoreStatusWhenEtagMismatch() {
    // given
    final long restoreId = 123L;
    repository = fixed(Clock.systemUTC().millis());
    final var initialStatus = new RestoreStatus(restoreId, Set.of());
    repository.updateRestoreStatus(initialStatus, null);

    // when/then
    final var updatedStatus = new RestoreStatus(restoreId, Set.of(0));
    assertThatThrownBy(() -> repository.updateRestoreStatus(updatedStatus, "invalid-etag"))
        .isInstanceOf(S3Exception.class)
        .hasMessageContaining("At least one of the pre-conditions you specified did not hold");
  }

  @Test
  void shouldGetAndUpdateMultipleRestoreId() {
    // given
    final long restoreId1 = 123L;
    final long restoreId2 = 456L;
    repository = fixed(Clock.systemUTC().millis());
    final var status1 = new RestoreStatus(restoreId1, Set.of(1));
    final var status2 = new RestoreStatus(restoreId2, Set.of(2));
    repository.updateRestoreStatus(status1, null);
    repository.updateRestoreStatus(status2, null);

    // when
    final var storedStatus1 = repository.getRestoreStatus(restoreId1);
    final var storedStatus2 = repository.getRestoreStatus(restoreId2);

    // then
    assertThat(storedStatus1).isNotNull();
    assertThat(storedStatus1.restoreStatus()).isEqualTo(status1);
    assertThat(storedStatus2).isNotNull();
    assertThat(storedStatus2.restoreStatus()).isEqualTo(status2);
  }

  private S3NodeIdRepository fixed(final long time) {
    clock = new ControlledInstantSource(Instant.ofEpochMilli(time));
    return new S3NodeIdRepository(client, config, clock, false);
  }
}
