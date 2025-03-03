/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;

public class InputStreamHashCalculator {

  public static String streamAndCalculateHash(
      final InputStream inputStream, final InputStreamConsumer inputStreamConsumer)
      throws Exception {
    final MessageDigest md = MessageDigest.getInstance(MessageDigestAlgorithms.SHA_256);

    try (final DigestInputStream stream = new DigestInputStream(inputStream, md)) {
      inputStreamConsumer.consumeStream(stream);
      return HexFormat.of().formatHex(md.digest());
    }
  }

  public static String streamAndCalculateHash(final InputStream inputStream) throws Exception {
    return streamAndCalculateHash(
        inputStream, stream -> stream.transferTo(OutputStream.nullOutputStream()));
  }

  @FunctionalInterface
  public interface InputStreamConsumer {
    void consumeStream(InputStream stream) throws Exception;
  }
}
