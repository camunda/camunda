/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;

public class DocumentHashProcessor {

  public static HashResult hash(final InputStream stream) {
    return hash(stream, MessageDigestAlgorithms.SHA_256);
  }

  private static HashResult hash(final InputStream stream, final String algorithm) {
    final MessageDigest md;
    try {
      md = MessageDigest.getInstance(algorithm);
    } catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    final DigestInputStream digestStream = new DigestInputStream(stream, md);
    final String contentHash = HexFormat.of().formatHex(md.digest());

    return new HashResult(digestStream, contentHash);
  }

  public record HashResult(DigestInputStream inputStream, String contentHash) {}
}
