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

  /** The ceiling Elasticsearch and OpenSearch keep their default routing shards under. */
  private static final int MAX_ROUTING_SHARDS = 1024;

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

  /**
   * Computes the shard Elasticsearch and OpenSearch assign a document to, given its routing value.
   * Reimplementing their calculation here lets a caller pick routing values that land on a chosen
   * shard. Both engines were verified to agree with this implementation.
   *
   * <p>They hash the routing value with 32-bit Murmur3 (seed 0) over its <em>UTF-16</em> chars, two
   * bytes per char, rather than over its UTF-8 bytes. The hash is then taken modulo the number of
   * routing shards and divided by the routing factor, see {@link
   * #getDefaultNumberOfRoutingShards(int)}.
   *
   * <p>The result holds for an index left on the defaults this assumes: one created with an
   * explicit {@code index.number_of_routing_shards}, or with an {@code
   * index.routing_partition_size} above 1 (which spreads a single routing value over several
   * shards), is assigned differently.
   *
   * @param routing the routing value of the document
   * @param numberOfShards the number of primary shards of the index, at least 1
   * @return the zero-based index of the shard the document is assigned to
   */
  public static int getShardForRouting(final String routing, final int numberOfShards) {
    if (numberOfShards < 1) {
      throw new IllegalArgumentException(
          "Expected the number of shards to be at least 1, but was " + numberOfShards);
    }

    final int routingShards = getDefaultNumberOfRoutingShards(numberOfShards);
    final int hash = Hashing.murmur3_32_fixed().hashUnencodedChars(routing).asInt();
    return Math.floorMod(hash, routingShards) / (routingShards / numberOfShards);
  }

  /**
   * Returns the number of routing shards Elasticsearch and OpenSearch give an index that does not
   * set {@code index.number_of_routing_shards} itself.
   *
   * <p>They hash into this number rather than into the number of shards, so that an index can later
   * be split without documents having to move: doubling the shards halves the routing factor, which
   * keeps every document on a shard derived from the one it already sits on. The default leaves
   * room for as many such splits as fit under 1024 shards.
   *
   * @param numberOfShards the number of primary shards of the index, at least 1
   * @return the number of routing shards, a power-of-two multiple of {@code numberOfShards}
   */
  private static int getDefaultNumberOfRoutingShards(final int numberOfShards) {
    int routingShards = numberOfShards;
    while (routingShards * 2 <= MAX_ROUTING_SHARDS) {
      routingShards *= 2;
    }

    return routingShards;
  }
}
