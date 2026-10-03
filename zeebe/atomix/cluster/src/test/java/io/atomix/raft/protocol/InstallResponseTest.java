/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.RaftError;
import io.atomix.raft.RaftError.Type;
import io.atomix.raft.partition.impl.RaftNamespaces;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import org.junit.jupiter.api.Test;

/** Members of different versions exchange install responses during a rolling update. */
final class InstallResponseTest {

  private static final Namespace OLD_FORMAT = namespaceWith(OldInstallResponse.class);
  private static final Namespace NEW_FORMAT = namespaceWith(InstallResponse.class);

  @Test
  void shouldRoundTripSnapshotNotNeeded() {
    // given
    final var response =
        InstallResponse.builder()
            .withStatus(Status.OK)
            .withPreferredChunkSize(42)
            .withSnapshotNotNeeded()
            .build();

    // when
    final InstallResponse deserialized =
        RaftNamespaces.RAFT_PROTOCOL.deserialize(RaftNamespaces.RAFT_PROTOCOL.serialize(response));

    // then
    assertThat(deserialized.snapshotNotNeeded()).isTrue();
    assertThat(deserialized.preferredChunkSize()).isEqualTo(42);
  }

  @Test
  void shouldNotSetSnapshotNotNeededWhenSentByOldVersion() {
    // given
    final var response = new OldInstallResponse(Status.OK, null, 42);

    // when
    final InstallResponse deserialized = NEW_FORMAT.deserialize(OLD_FORMAT.serialize(response));

    // then
    assertThat(deserialized.snapshotNotNeeded()).isFalse();
    assertThat(deserialized.preferredChunkSize()).isEqualTo(42);
  }

  @Test
  void shouldBeReadableByOldVersion() {
    // given
    final var response =
        InstallResponse.builder()
            .withStatus(Status.OK)
            .withPreferredChunkSize(42)
            .withSnapshotNotNeeded()
            .build();

    // when
    final OldInstallResponse deserialized = OLD_FORMAT.deserialize(NEW_FORMAT.serialize(response));

    // then
    assertThat(deserialized.status()).isEqualTo(Status.OK);
    assertThat(deserialized.preferredChunkSize).isEqualTo(42);
  }

  private static Namespace namespaceWith(final Class<?> installResponseClass) {
    return new Namespace.Builder()
        .register(Namespaces.BASIC)
        .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
        .register(installResponseClass)
        .register(Status.class)
        .register(RaftError.class)
        .register(Type.class)
        .name("install-response")
        .build();
  }

  /** {@link InstallResponse} as it was before it had the snapshotNotNeeded field. */
  private static final class OldInstallResponse extends AbstractRaftResponse {
    private final int preferredChunkSize;

    private OldInstallResponse(
        final Status status, final RaftError error, final int preferredChunkSize) {
      super(status, error);
      this.preferredChunkSize = preferredChunkSize;
    }
  }
}
