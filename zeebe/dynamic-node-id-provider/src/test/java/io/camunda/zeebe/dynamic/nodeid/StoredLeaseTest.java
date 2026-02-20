/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.dynamic.nodeid.Lease.VersionMappings;
import io.camunda.zeebe.dynamic.nodeid.repository.Metadata;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class StoredLeaseTest {

  private final NodeInstance nodeInstance = new NodeInstance(2, Version.of(2L));
  private final ControlledInstantSource clock = new ControlledInstantSource(Instant.now());
  private final String taskId = "newTaskId";
  private final Duration expiryDuration = Duration.ofSeconds(15);

  long expiryFromNow() {
    return clock.millis() + expiryDuration.toMillis();
  }

  @Nested
  class FactoryMethod {
    @Test
    void shouldReturnUnusableWhenMetadataIsNotAcquirable() {
      // given
      final var metadata = new Metadata(Optional.empty(), Version.of(1), false);

      // when
      final var stored = StoredLease.of(2, null, metadata, "eTag");

      // then
      assertThat(stored).isInstanceOf(StoredLease.Unusable.class);
      assertThat(stored.node().id()).isEqualTo(2);
      assertThat(stored.version()).isEqualTo(Version.of(1));
      assertThat(stored.isAcquirable()).isFalse();
    }

    @Test
    void shouldReturnUninitializedWhenLeaseIsNullAndMetadataIsAcquirable() {
      // given
      final var metadata = new Metadata(Optional.empty(), Version.of(1), true);

      // when
      final var stored = StoredLease.of(2, null, metadata, "eTag");

      // then
      assertThat(stored).isInstanceOf(StoredLease.Uninitialized.class);
      assertThat(stored.node().id()).isEqualTo(2);
      assertThat(stored.version()).isEqualTo(Version.of(1));
      assertThat(stored.isAcquirable()).isTrue();
    }

    @Test
    void shouldReturnInitializedWhenLeaseIsNotNull() {
      // given
      final var lease =
          new Lease(taskId, expiryFromNow(), nodeInstance, VersionMappings.of(nodeInstance));
      final var metadata = new Metadata(Optional.of(taskId), nodeInstance.version(), true);

      // when
      final var stored = StoredLease.of(nodeInstance.id(), lease, metadata, "eTag");

      // then
      assertThat(stored).isInstanceOf(StoredLease.Initialized.class);
      assertThat(stored.isAcquirable()).isTrue();
    }
  }

  @Nested
  class Uninitialized {
    @Test
    void shouldAlwaysContainNodeId() {
      assertThatThrownBy(() -> new StoredLease.Uninitialized(null, "asdasd"))
          .hasMessageContaining("node cannot be null");
    }

    @Test
    void shouldAlwaysContainETag() {
      assertThatThrownBy(() -> new StoredLease.Uninitialized(nodeInstance, ""))
          .hasMessageContaining("eTag cannot be empty");
      assertThatThrownBy(() -> new StoredLease.Uninitialized(nodeInstance, null))
          .hasMessageContaining("eTag cannot be null");
    }

    @Test
    void canBeAcquired() {
      // given
      final var stored = new StoredLease.Uninitialized(nodeInstance, "eTagExample");

      // when
      final var toAcquire = stored.acquireInitialLease(taskId, clock, expiryDuration);

      // then
      assertThat(toAcquire)
          .isPresent()
          .hasValueSatisfying(
              lease ->
                  assertThat(lease)
                      .returns(new NodeInstance(2, Version.of(3L)), Lease::nodeInstance)
                      .returns(taskId, Lease::taskId)
                      .returns(
                          VersionMappings.of(nodeInstance.nextVersion()),
                          Lease::knownVersionMappings)
                      .returns(expiryFromNow(), Lease::timestamp));
    }
  }

  @Nested
  class Initialized {

    @Test
    void shouldAlwaysContainMetadata() {
      assertThatThrownBy(() -> new StoredLease.Initialized(null, 0L, 0, "asd"))
          .hasMessageContaining("metadata cannot be null");
      assertThatThrownBy(
              () ->
                  new StoredLease.Initialized(
                      null,
                      new Lease(
                          "asdasd",
                          123,
                          new NodeInstance(0, Version.of(1)),
                          VersionMappings.empty()),
                      "asd"))
          .hasMessageContaining("metadata cannot be null");
    }

    @Test
    void shouldAlwaysContainETag() {
      assertThatThrownBy(
              () ->
                  new StoredLease.Initialized(
                      new Metadata(Optional.of("asd"), Version.of(1)), 1L, 0, null))
          .hasMessageContaining("eTag cannot be null");
    }

    @Test
    void shouldNotAcquireAValidLease() {
      // given
      final var stored =
          new StoredLease.Initialized(
              new Metadata(Optional.of(taskId), nodeInstance.version()),
              expiryFromNow(),
              nodeInstance.id(),
              "eTagExample");

      final var newTaskId = "newTaskId";
      final var acquired = stored.acquireInitialLease(newTaskId, clock, expiryDuration);

      // then
      assertThat(acquired).isEmpty();
    }

    @Test
    void shouldAcquireWithANewVersionWhenExpired() {
      // given
      final var stored =
          new StoredLease.Initialized(
              new Metadata(Optional.of(taskId), nodeInstance.version()),
              expiryFromNow(),
              nodeInstance.id(),
              "eTagExample");

      // when
      clock.advance(expiryDuration.plusMillis(1));
      final var newTaskId = "newTaskId";
      final var acquired = stored.acquireInitialLease(newTaskId, clock, expiryDuration);

      // then
      assertThat(acquired)
          .isPresent()
          .hasValueSatisfying(
              lease ->
                  assertThat(lease)
                      .returns(nodeInstance.nextVersion(), Lease::nodeInstance)
                      .returns(newTaskId, Lease::taskId)
                      .returns(
                          VersionMappings.of(nodeInstance.nextVersion()),
                          Lease::knownVersionMappings)
                      .returns(expiryFromNow(), Lease::timestamp));
    }
  }

  @Nested
  class Unusable {
    @Test
    void shouldAlwaysContainNodeId() {
      assertThatThrownBy(() -> new StoredLease.Unusable(null, "asdasd"))
          .hasMessageContaining("node cannot be null");
    }

    @Test
    void shouldAlwaysContainETag() {
      assertThatThrownBy(() -> new StoredLease.Unusable(nodeInstance, ""))
          .hasMessageContaining("eTag cannot be empty");
      assertThatThrownBy(() -> new StoredLease.Unusable(nodeInstance, null))
          .hasMessageContaining("eTag cannot be null");
    }

    @Test
    void shouldNotBeAcquirable() {
      // given
      final var stored = new StoredLease.Unusable(nodeInstance, "eTagExample");

      // when/then
      assertThat(stored.isAcquirable()).isFalse();
      assertThat(stored.acquireInitialLease(taskId, clock, expiryDuration)).isEmpty();
    }

    @Test
    void shouldNotBeInitialized() {
      // given
      final var stored = new StoredLease.Unusable(nodeInstance, "eTagExample");

      // when/then
      assertThat(stored.isInitialized()).isFalse();
    }

    @Test
    void shouldNotBeValid() {
      // given
      final var stored = new StoredLease.Unusable(nodeInstance, "eTagExample");

      // when/then
      assertThat(stored.isStillValid(clock.millis())).isFalse();
    }
  }
}
