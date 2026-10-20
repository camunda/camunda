/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/**
 * Rendezvous hashing: a log belongs to the worker with the highest hash of worker and log. Workers
 * that see the same live workers agree on every log's owner, and when a worker comes or goes, only
 * the logs that it gets or had move. The hash is SHA-256, the same in every process and language.
 */
final class Rendezvous {

  private Rendezvous() {}

  /**
   * The owner of a log among workers; of equal hashes, the worker whose id sorts last.
   *
   * @throws IllegalArgumentException if there are no workers
   */
  static String owner(final Collection<String> workers, final LogId log) {
    String owner = null;
    long best = 0;
    for (final var worker : workers) {
      final long score = score(worker, log);
      if (owner == null
          || Long.compareUnsigned(score, best) > 0
          || score == best && worker.compareTo(owner) > 0) {
        owner = worker;
        best = score;
      }
    }
    if (owner == null) {
      throw new IllegalArgumentException(
          "Expected at least one worker to own log %s, but got none".formatted(log));
    }
    return owner;
  }

  /** The first 8 bytes of SHA-256 over the UTF-8 of {@code {worker}\n{log}}, unsigned. */
  static long score(final String worker, final LogId log) {
    try {
      final var digest = MessageDigest.getInstance("SHA-256");
      final var hash = digest.digest((worker + "\n" + log).getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.wrap(hash).getLong();
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("Expected every JVM to have SHA-256, but this one hasn't", e);
    }
  }
}
