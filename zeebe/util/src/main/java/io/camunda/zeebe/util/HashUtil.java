/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

/** Utility class for generating hash values. */
public final class HashUtil {

  private HashUtil() {}

  /**
   * Computes a 64-bit hash value from the given string using Murmur3 128-bit hashing.
   *
   * <p>Murmur3 is chosen for its excellent performance and well-distributed hash values, making it
   * ideal for use cases such as hash tables, IDs, and partitioning tasks where cryptographic
   * security is not a concern. The probability of collision for the 64-bit hash output is extremely
   * low for typical applications—collisions only become likely (about 50% chance) after
   * approximately 8 billion unique inputs, due to the birthday paradox. Note that Murmur3 is not
   * suitable for cryptographic purposes or adversarial environments.
   *
   * <p><b>Dependency Note:</b> If there is a need to remove external dependency, Murmur3 can be
   * replaced with Java's built-in SHA-256 hash function. SHA-256 provides very low collision
   * probability and is suitable for security-sensitive applications, though it is slower than
   * Murmur3. See the alternative implementation below:
   *
   * <pre>{@code
   * public static long getStringHashValueSHA256(final String stringValue) {
   *     try {
   *         MessageDigest digest = MessageDigest.getInstance("SHA-256");
   *         byte[] hash = digest.digest(stringValue.getBytes(StandardCharsets.UTF_8));
   *         return ByteBuffer.wrap(hash).getLong(); // Uses first 8 bytes as a long
   *     } catch (NoSuchAlgorithmException e) {
   *         throw new RuntimeException(e);
   *     }
   * }
   * }</pre>
   *
   * @param stringValue the input string to hash
   * @return the 64-bit hash value of the input string
   */
  public static long getStringHashValue(final String stringValue) {
    return Hashing.murmur3_128().hashString(stringValue, StandardCharsets.UTF_8).asLong();
  }
}
