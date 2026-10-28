/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.NullMarked;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a physical tenant's configuration subtree into the bytes the cluster refers to by hash, and
 * back. The names are sorted, so the same subtree always has the same bytes and hash.
 */
@NullMarked
final class PhysicalTenantConfigCodec {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<LinkedHashMap<String, String>> SUBTREE =
      new TypeReference<>() {};

  private PhysicalTenantConfigCodec() {}

  static byte[] encode(final Map<String, String> subtree) {
    return JSON.writeValueAsString(new TreeMap<>(subtree)).getBytes(StandardCharsets.UTF_8);
  }

  static Map<String, String> decode(final byte[] configuration) {
    return JSON.readValue(configuration, SUBTREE);
  }

  static String sha256(final byte[] configuration) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(configuration));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static Map<String, Object> asProperties(final Map<String, String> subtree) {
    return new LinkedHashMap<>(subtree);
  }
}
