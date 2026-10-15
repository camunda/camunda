/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.configuration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * The messages exchanged between a coordinator and the system partition leader to read and
 * compare-and-set the cluster configuration stored in the system partition.
 */
final class ClusterConfigurationStoreProtocol {
  static final String READ_SUBJECT = "system-partition-cluster-configuration-read";
  static final String CAS_SUBJECT = "system-partition-cluster-configuration-cas";

  private ClusterConfigurationStoreProtocol() {}

  enum Status {
    OK,
    /** The receiver is not the system partition leader; the sender tries another member. */
    NOT_LEADER,
    /** The stored next change id differs from the expected one. */
    REJECTED,
    FAILED
  }

  /**
   * @param expectedNextId the next change id the stored configuration must have, or {@link
   *     io.camunda.zeebe.dynamic.config.ClusterConfigurationStore#ABSENT} if none must be stored
   * @param configuration the encoded configuration to store
   */
  record CasRequest(long expectedNextId, byte[] configuration) {

    byte[] encode() {
      return ByteBuffer.allocate(Long.BYTES + configuration.length)
          .putLong(expectedNextId)
          .put(configuration)
          .array();
    }

    static CasRequest decode(final byte[] bytes) {
      final var buffer = ByteBuffer.wrap(bytes);
      final var expectedNextId = buffer.getLong();
      final var configuration = new byte[buffer.remaining()];
      buffer.get(configuration);
      return new CasRequest(expectedNextId, configuration);
    }
  }

  /**
   * @param payload the encoded configuration of a successful read (empty if none is stored), or an
   *     error message otherwise
   */
  record Response(Status status, byte[] payload) {

    static Response ok(final byte[] payload) {
      return new Response(Status.OK, payload);
    }

    static Response error(final Status status, final String message) {
      return new Response(status, message.getBytes(StandardCharsets.UTF_8));
    }

    String message() {
      return new String(payload, StandardCharsets.UTF_8);
    }

    byte[] encode() {
      return ByteBuffer.allocate(1 + payload.length)
          .put((byte) status.ordinal())
          .put(payload)
          .array();
    }

    static Response decode(final byte[] bytes) {
      final var buffer = ByteBuffer.wrap(bytes);
      final var status = Status.values()[buffer.get()];
      final var payload = new byte[buffer.remaining()];
      buffer.get(payload);
      return new Response(status, payload);
    }
  }
}
