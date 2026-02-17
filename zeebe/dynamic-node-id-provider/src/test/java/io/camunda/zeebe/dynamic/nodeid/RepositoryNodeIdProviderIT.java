/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.NodeId;
import io.camunda.zeebe.dynamic.nodeid.Lease.VersionMappings;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.Config;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig.Credentials;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers
@Timeout(value = 120)
class RepositoryNodeIdProviderIT {

  private static final Duration EXPIRY_DURATION = Duration.ofSeconds(5);

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices(Service.S3)
          .withEnv("LS_LOG", "trace");

  @AutoClose private static S3Client client;
  S3NodeIdRepository.Config config;
  @AutoClose private S3NodeIdRepository repository;
  @AutoClose private RepositoryNodeIdProvider nodeIdProvider;
  private ControlledInstantSource clock;
  private int clusterSize;
  private String taskId;
  private volatile boolean leaseFailed = false;

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
    final var initialInstant =
        LocalDateTime.of(2025, 11, 1, 13, 46, 22).atZone(ZoneId.of("UTC")).toInstant();
    clock = new ControlledInstantSource(initialInstant);
    repository = new S3NodeIdRepository(client, config, clock, false);
  }

  @Test
  void shouldAcquireALease() throws ExecutionException, InterruptedException, TimeoutException {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);

    // when
    nodeIdProvider = ofSize(clusterSize);

    // then
    assertLeaseIsReady();

    final var acquiredLease = nodeIdProvider.getCurrentLease();
    final var nodeId = acquiredLease.lease().nodeInstance().id();
    assertThat(nodeId).isEqualTo(0);
    assertThat(acquiredLease.metadata().task()).isEqualTo(Optional.of(taskId));
    assertThat(acquiredLease.lease().timestamp())
        .isEqualTo(clock.instant().plus(EXPIRY_DURATION).toEpochMilli());
    assertThat(repository.getLease(nodeId)).isEqualTo(acquiredLease);
  }

  @Test
  void shouldAcquireLeaseIfClusterSizeChangedAfterInitialization() {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);
    final var lease0 = repository.getLease(0);
    final var newLease =
        lease0
            .acquireInitialLease(taskId, clock, Duration.ofMinutes(5))
            .orElseThrow(); // never expires
    repository.acquire(newLease, lease0.eTag());

    // when
    // nodeIdProvider is configured with different cluster size
    nodeIdProvider = ofSize(1);
    assertLeaseIsReady();

    // then
    final var acquiredLease = nodeIdProvider.getCurrentLease();
    final var nodeId = acquiredLease.lease().nodeInstance().id();
    assertThat(nodeId).isGreaterThan(0); // should acquire a lease for nodeid 1 or 2.
  }

  @Test
  void shouldAcquireAnExpiredLease()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);
    // acquire a lease in the past
    final var lease = repository.getLease(0);
    final var previousEtag = lease.eTag();
    final var expiredLeaseTimestamp = clock.millis();
    final var expiredLease =
        repository.acquire(
            new Lease(
                taskId + "old",
                expiredLeaseTimestamp + EXPIRY_DURATION.toMillis(),
                new NodeInstance(0, lease.version().next()),
                VersionMappings.empty()),
            previousEtag);
    assertThat(expiredLease).isNotNull();
    clock.advance(EXPIRY_DURATION.plusSeconds(1));

    // when
    nodeIdProvider = ofSize(clusterSize);

    // then
    assertLeaseIsReady();

    final var acquiredLease = nodeIdProvider.getCurrentLease();
    final var nodeId = acquiredLease.lease().nodeInstance().id();
    assertThat(nodeId).isEqualTo(0);
    final var currentLease = repository.getLease(nodeId);
    assertThat(currentLease)
        .asInstanceOf(type(StoredLease.Initialized.class))
        .isEqualTo(acquiredLease)
        .isNotEqualTo(expiredLease);
  }

  @Test
  void shouldBlockInitializationIfAllLeasesAreTaken()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);
    // we acquire all leases
    final var acquiredLeases =
        IntStream.range(0, clusterSize)
            .mapToObj(
                i -> {
                  final var lease = repository.getLease(i);
                  return repository.acquire(
                      new Lease(
                          taskId,
                          clock.millis() + 2000L,
                          new NodeInstance(i, lease.version().next()),
                          VersionMappings.empty()),
                      lease.eTag());
                })
            .toList();
    assertThat(acquiredLeases)
        .allSatisfy(l -> assertThat(l).isInstanceOf(StoredLease.Initialized.class));

    // when
    nodeIdProvider = ofSize(clusterSize, false);

    // then
    // verify that the lease status is not ready continuously
    Awaitility.await()
        .during(EXPIRY_DURATION)
        .untilAsserted(() -> assertThat(nodeIdProvider.getCurrentLease()).isNull());
    // all leases are unchanged
    final var currentLeases =
        IntStream.range(0, clusterSize).mapToObj(repository::getLease).toList();
    assertThat(acquiredLeases).isEqualTo(currentLeases);
  }

  @Test
  void shouldRenewTheLeaseBeforeExpiration()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);
    repository = Mockito.spy(repository);

    // when
    nodeIdProvider = ofSize(clusterSize);
    assertLeaseIsReady();
    final var firstLease = nodeIdProvider.getCurrentLease();
    // move the clock forward
    clock.setInstant(clock.instant().plusSeconds(2));

    // then
    verify(repository, timeout(EXPIRY_DURATION.toMillis()).atLeast(2))
        .acquire(argThat(lease -> lease.nodeInstance().id() == 0), any());
    Awaitility.await("until lease is renewed")
        .untilAsserted(() -> assertThat(nodeIdProvider.getCurrentLease()).isNotEqualTo(firstLease));
    final var renewedLease = nodeIdProvider.getCurrentLease();
    assertThat(renewedLease).isNotEqualTo(firstLease);
    assertThat(renewedLease.lease().timestamp()).isGreaterThan(firstLease.lease().timestamp());
    assertThat(renewedLease.node()).isEqualTo(firstLease.node());
  }

  @Test
  void shouldUpdateKnownMemberVersionWhenRenewing() {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);
    repository = Mockito.spy(repository);

    nodeIdProvider = ofSize(clusterSize);
    assertLeaseIsReady();
    final var firstLease = nodeIdProvider.getCurrentLease();

    // when
    final var expectedMappings = Map.of(1, Version.of(2), 2, Version.of(2), 3, Version.of(1));
    nodeIdProvider.setMembers(getMembers(expectedMappings));
    // move the clock forward
    clock.setInstant(clock.instant().plusSeconds(2));

    // then
    Awaitility.await("until lease is renewed")
        .untilAsserted(() -> assertThat(nodeIdProvider.getCurrentLease()).isNotEqualTo(firstLease));
    final StoredLease.Initialized renewedLease =
        (StoredLease.Initialized) repository.getLease(firstLease.node().id());
    assertThat(renewedLease.lease().knownVersionMappings().mappingsByNodeId())
        .containsExactlyInAnyOrderEntriesOf(expectedMappings);
  }

  private Set<Member> getMembers(final Map<Integer, Version> nodeIdVersions) {
    return nodeIdVersions.entrySet().stream()
        .map(
            entry ->
                new Member(
                    new MemberConfig()
                        .setId(NodeId.from(entry.getKey().toString()))
                        .setNodeVersion(entry.getValue().version())))
        .collect(Collectors.toSet());
  }

  @Test
  void shouldInvokeFailureListenerWhenFailsToRenew()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);
    repository = Mockito.spy(repository);

    // Inject failure: first call succeeds, subsequent calls throw exception

    // when
    nodeIdProvider = ofSize(clusterSize);
    assertLeaseIsReady();

    // then
    doThrow(new IllegalStateException("Injected failure")).when(repository).acquire(any(), any());
    Awaitility.await("Until failure listener has been invoked")
        .untilAsserted(() -> assertThat(leaseFailed).isTrue());
    assertThat(nodeIdProvider.getCurrentLease()).isNull();
  }

  @Test
  void shouldReleaseGracefullyWhenClosed() {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);
    nodeIdProvider = ofSize(clusterSize);
    assertLeaseIsReady();
    final var lease = nodeIdProvider.getCurrentLease();

    // when
    assertThatNoException().isThrownBy(nodeIdProvider::close);

    // then
    // clock is not moved, so the lease can only be acquired if released
    final var releasedLease = repository.getLease(lease.lease().nodeInstance().id());
    assertThat(releasedLease).isInstanceOf(StoredLease.Uninitialized.class);
    assertThat(releasedLease.node()).isEqualTo(lease.node());
    nodeIdProvider = ofSize(clusterSize);
    assertLeaseIsReady();
    assertThat(nodeIdProvider.getCurrentLease().lease().nodeInstance())
        .isEqualTo(lease.lease().nodeInstance().nextVersion());
  }

  @Test
  public void shouldReturnTrueWhenPreviousNodeGracefullyShutdown() {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);
    nodeIdProvider = ofSize(clusterSize);
    assertLeaseIsReady();
    final var lease = nodeIdProvider.getCurrentLease();

    // when - gracefully shutdown first provider
    assertThatNoException().isThrownBy(nodeIdProvider::close);

    // verify the lease was gracefully released
    final var releasedLease = repository.getLease(lease.lease().nodeInstance().id());
    assertThat(releasedLease).isInstanceOf(StoredLease.Uninitialized.class);

    // when - second provider acquires the gracefully released lease
    nodeIdProvider = ofSize(clusterSize);
    assertLeaseIsReady();

    // then
    assertThat(nodeIdProvider.previousNodeGracefullyShutdown())
        .succeedsWithin(Duration.ofSeconds(30))
        .isEqualTo(true);
  }

  @Test
  public void shouldReturnFalseWhenPreviousLeaseExpired() {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);

    // Acquire a lease that will expire
    createExpiredLeaseForNodeId(0);

    // when - new provider acquires the expired lease
    nodeIdProvider = ofSize(clusterSize);
    assertLeaseIsReady();

    // then
    assertThat(nodeIdProvider.previousNodeGracefullyShutdown())
        .succeedsWithin(Duration.ofSeconds(1))
        .isEqualTo(false);
  }

  @Test
  void shouldMarkReadyIfPreviousLeaseWasGracefullyReleased() {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);

    // when - no previous expired lease
    nodeIdProvider = ofSize(clusterSize, true);

    // then
    assertThat(nodeIdProvider.awaitReadiness()).succeedsWithin(EXPIRY_DURATION).isEqualTo(true);
  }

  @Test
  void shouldMarkReadyOnlyWhenAllOtherNodesAreUptoDate() throws Exception {
    // given
    clusterSize = 3;
    repository.initialize(clusterSize);

    // First node always get an expired lease
    createExpiredLeaseForNodeId(0);
    nodeIdProvider = ofSize(clusterSize, true);

    try (final var nodeIdProviderOtherOne = ofSize(clusterSize);
        final var nodeIdProviderOtherTwo = ofSize(clusterSize)) {
      // other nodes have outdated version info
      assertThat(nodeIdProvider.awaitReadiness()).isNotCompleted();

      // when
      final NodeInstance nodeInstance = nodeIdProvider.currentNodeInstance();
      // Only the version of this node is updated in all other nodes for this node to be ready
      final var expectedMappings = Map.of(nodeInstance.id(), nodeInstance.version());
      nodeIdProviderOtherOne.setMembers(getMembers(expectedMappings));
      nodeIdProviderOtherTwo.setMembers(getMembers(expectedMappings));

      // then
      assertThat(nodeIdProvider.awaitReadiness()).succeedsWithin(EXPIRY_DURATION).isEqualTo(true);
    }
  }

  private void assertLeaseIsReady() {
    assertLeaseIs(true);
  }

  private void assertLeaseIsNotReady() {
    assertLeaseIs(false);
  }

  private void assertLeaseIs(final boolean status) {
    Awaitility.await("Until the lease is acquired")
        .untilAsserted(
            () ->
                assertThat(nodeIdProvider.isValid())
                    .succeedsWithin(Duration.ofSeconds(1))
                    .isEqualTo(status));
  }

  RepositoryNodeIdProvider ofSize(final int clusterSize) {
    return ofSize(clusterSize, true);
  }

  RepositoryNodeIdProvider ofSize(final int clusterSize, final boolean awaitInitialization) {
    final var provider =
        new RepositoryNodeIdProvider(
            repository,
            clock,
            EXPIRY_DURATION,
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            taskId,
            () -> leaseFailed = true);
    final var future = provider.initialize(clusterSize);
    if (awaitInitialization) {
      future.join();
    }
    return provider;
  }

  private void createExpiredLeaseForNodeId(final int nodeId) {
    final var lease = repository.getLease(nodeId);
    final var expiredLeaseTimestamp = clock.millis();
    final var expiredLease =
        repository.acquire(
            new Lease(
                taskId + "-old",
                expiredLeaseTimestamp + EXPIRY_DURATION.toMillis(),
                new NodeInstance(0, lease.version().next()),
                VersionMappings.empty()),
            lease.eTag());
    assertThat(expiredLease).isNotNull().isInstanceOf(StoredLease.Initialized.class);

    // when - advance clock to expire the lease
    clock.advance(EXPIRY_DURATION.plusSeconds(1));

    // verify the lease is expired (still initialized but not valid)
    final var storedLease = repository.getLease(0);
    assertThat(storedLease).isInstanceOf(StoredLease.Initialized.class);
    assertThat(storedLease.isStillValid(clock.millis())).isFalse();
  }
}
