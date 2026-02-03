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
public class S3NodeIdRepositoryIT {

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
  public static void setUpAll() {
    client =
        S3NodeIdRepository.buildClient(
            new S3ClientConfig(
                Optional.of(new Credentials(S3.getAccessKey(), S3.getSecretKey())),
                Optional.of(Region.of(S3.getRegion())),
                Optional.of(S3.getEndpoint())));
  }

  @BeforeEach
  public void setUp() {
    final var bucketName = UUID.randomUUID().toString();
    taskId = UUID.randomUUID().toString();
    config = new Config(bucketName, EXPIRY_DURATION, Duration.ofMinutes(2));
    client.createBucket(b -> b.bucket(config.bucketName()));
  }

  @Test
  public void shouldInitializeAllFiles() {
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
  public void shouldAcquireFirstLease() {
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
  public void shouldAcquireOneLeaseWhenEtagMatches() {
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
  public void shouldNotAcquireAnExpiredLease() {
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
  public void shouldNotAcquireOneLeaseWhenETagMismatch() {
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
  public void shouldReleaseALeaseCorrectly() {
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
  public void shouldCloseS3Client() throws Exception {
    // given
    final var clientMock = Mockito.mock(S3Client.class);
    repository = new S3NodeIdRepository(clientMock, config, Clock.systemUTC(), true);
    // when
    repository.close();

    // then
    verify(clientMock).close();
  }

  @Test
  public void shouldReturnNullWhenRestoreStatusNotInitialized() {
    // given
    repository = fixed(Clock.systemUTC().millis());

    // when
    final var restoreStatus = repository.getRestoreStatus();

    // then
    assertThat(restoreStatus).isNull();
  }

  @Test
  public void shouldUpdateAndGetRestoreStatus() {
    // given
    repository = fixed(Clock.systemUTC().millis());
    final var restoreStatus = new RestoreStatus(Set.of());

    // when
    repository.updateRestoreStatus(restoreStatus, null);
    final var storedRestoreStatus = repository.getRestoreStatus();

    // then
    assertThat(storedRestoreStatus).isNotNull();
    assertThat(storedRestoreStatus.restoreStatus()).isEqualTo(restoreStatus);
    assertThat(storedRestoreStatus.etag()).isNotEmpty();
  }

  @Test
  public void shouldUpdateRestoreStatusWithRestoredNodes() {
    // given
    repository = fixed(Clock.systemUTC().millis());
    final var initialStatus = new RestoreStatus(Set.of());
    repository.updateRestoreStatus(initialStatus, null);
    final var storedInitial = repository.getRestoreStatus();

    // when
    final var updatedStatus = new RestoreStatus(Set.of(0, 1));
    repository.updateRestoreStatus(updatedStatus, storedInitial.etag());
    final var storedUpdated = repository.getRestoreStatus();

    // then
    assertThat(storedUpdated).isNotNull();
    assertThat(storedUpdated.restoreStatus().restoredNodes()).containsExactlyInAnyOrder(0, 1);
    assertThat(storedUpdated.etag()).isNotEqualTo(storedInitial.etag());
  }

  @Test
  public void shouldFailToUpdateRestoreStatusWhenEtagMismatch() {
    // given
    repository = fixed(Clock.systemUTC().millis());
    final var initialStatus = new RestoreStatus(Set.of());
    repository.updateRestoreStatus(initialStatus, null);

    // when/then
    final var updatedStatus = new RestoreStatus(Set.of(0));
    assertThatThrownBy(() -> repository.updateRestoreStatus(updatedStatus, "invalid-etag"))
        .isInstanceOf(S3Exception.class)
        .hasMessageContaining("At least one of the pre-conditions you specified did not hold");
  }

  private S3NodeIdRepository fixed(final long time) {
    clock = new ControlledInstantSource(Instant.ofEpochMilli(time));
    return new S3NodeIdRepository(client, config, clock, false);
  }
}
