/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.agrona.DirectBuffer;

/** Generates a checksum for deployment resources. */
public final class ChecksumGenerator {
  private final MessageDigest md5;

  {
    try {
      // Only used as a hash function, so MD5 is sufficient and does not pose a security risk.
      md5 = MessageDigest.getInstance("MD5");
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Generates a checksum for the given resource. */
  public byte[] checksum(final byte[] resource) {
    return md5.digest(resource);
  }

  /** Generates a checksum for the given resource. */
  public DirectBuffer checksum(final DirectBuffer resource) {
    final var bytes = BufferUtil.bufferAsArray(resource);
    final var checksum = checksum(bytes);
    return BufferUtil.wrapArray(checksum);
  }
}
