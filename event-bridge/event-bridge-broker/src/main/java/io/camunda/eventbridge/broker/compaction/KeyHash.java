/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

/**
 * A 128-bit key hash for the cleaner's latest-per-key map, computed with the 128-bit x64 variant of
 * MurmurHash3.
 *
 * <h3>Why a self-contained 128-bit hash</h3>
 *
 * <p>The cleaner keys its latest-per-key map by a 128-bit digest of the record key rather than by
 * the key bytes themselves (ADR 0001, decision 6.2). No dependency already on this module's
 * classpath provides a 128-bit hash — Agrona's {@code Hashing} offers only 32/64-bit integer
 * mixers, and the module intentionally carries no general hashing library. Rather than add a
 * dependency (which the task requires flagging), the hash is implemented here: MurmurHash3 is a
 * well-known, public-domain, non-cryptographic algorithm whose 128-bit output has excellent
 * distribution, and it is pinned by test vectors so a refactor cannot silently change the digest.
 *
 * <h3>Collision rationale</h3>
 *
 * <p>A collision — two distinct keys mapping to the same 128-bit digest — would let the cleaner
 * treat one key's latest record as superseded by the other and drop it, i.e. silent data loss. With
 * a well-distributed 128-bit hash the birthday bound puts a 50% collision probability at ~2^64
 * distinct live keys; for any realistic keyspace (even trillions of keys) the probability is
 * ~n²/2^129, which is astronomically small — far below the probability of undetected disk or memory
 * corruption the surrounding storage already tolerates. This matches the ADR's "collision
 * probability negligible" and is the reason the digest is 128-bit rather than 64-bit.
 *
 * <p>Threading: stateless; all methods are pure and safe to call concurrently.
 */
public final class KeyHash {

  private static final long C1 = 0x87c37b91114253d5L;
  private static final long C2 = 0x4cf5ad432745937fL;

  private KeyHash() {}

  /**
   * Computes the 128-bit MurmurHash3 (x64) digest of the given key bytes with seed 0.
   *
   * @param key the key bytes (must not be {@code null}; an empty array is permitted and hashes
   *     deterministically)
   * @return the 128-bit digest as a {@link Hash128}
   */
  public static Hash128 hash(final byte[] key) {
    return hash(key, 0, key.length);
  }

  /**
   * Computes the 128-bit MurmurHash3 (x64) digest of a region of the given array with seed 0.
   *
   * @param data the backing array
   * @param offset start offset within the array
   * @param length number of bytes to hash
   * @return the 128-bit digest as a {@link Hash128}
   */
  public static Hash128 hash(final byte[] data, final int offset, final int length) {
    long h1 = 0L;
    long h2 = 0L;

    final int nblocks = length >> 4; // 16-byte blocks

    for (int i = 0; i < nblocks; i++) {
      final int base = offset + (i << 4);
      long k1 = getLongLE(data, base);
      long k2 = getLongLE(data, base + 8);

      k1 *= C1;
      k1 = Long.rotateLeft(k1, 31);
      k1 *= C2;
      h1 ^= k1;
      h1 = Long.rotateLeft(h1, 27);
      h1 += h2;
      h1 = h1 * 5 + 0x52dce729L;

      k2 *= C2;
      k2 = Long.rotateLeft(k2, 33);
      k2 *= C1;
      h2 ^= k2;
      h2 = Long.rotateLeft(h2, 31);
      h2 += h1;
      h2 = h2 * 5 + 0x38495ab5L;
    }

    long k1 = 0L;
    long k2 = 0L;
    final int tailStart = offset + (nblocks << 4);
    final int tail = length & 15;

    switch (tail) {
      case 15:
        k2 ^= (long) (data[tailStart + 14] & 0xff) << 48;
      // fall through
      case 14:
        k2 ^= (long) (data[tailStart + 13] & 0xff) << 40;
      // fall through
      case 13:
        k2 ^= (long) (data[tailStart + 12] & 0xff) << 32;
      // fall through
      case 12:
        k2 ^= (long) (data[tailStart + 11] & 0xff) << 24;
      // fall through
      case 11:
        k2 ^= (long) (data[tailStart + 10] & 0xff) << 16;
      // fall through
      case 10:
        k2 ^= (long) (data[tailStart + 9] & 0xff) << 8;
      // fall through
      case 9:
        k2 ^= data[tailStart + 8] & 0xff;
        k2 *= C2;
        k2 = Long.rotateLeft(k2, 33);
        k2 *= C1;
        h2 ^= k2;
      // fall through
      case 8:
        k1 ^= (long) (data[tailStart + 7] & 0xff) << 56;
      // fall through
      case 7:
        k1 ^= (long) (data[tailStart + 6] & 0xff) << 48;
      // fall through
      case 6:
        k1 ^= (long) (data[tailStart + 5] & 0xff) << 40;
      // fall through
      case 5:
        k1 ^= (long) (data[tailStart + 4] & 0xff) << 32;
      // fall through
      case 4:
        k1 ^= (long) (data[tailStart + 3] & 0xff) << 24;
      // fall through
      case 3:
        k1 ^= (long) (data[tailStart + 2] & 0xff) << 16;
      // fall through
      case 2:
        k1 ^= (long) (data[tailStart + 1] & 0xff) << 8;
      // fall through
      case 1:
        k1 ^= data[tailStart] & 0xff;
        k1 *= C1;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= C2;
        h1 ^= k1;
        break;
      default:
        // tail == 0: nothing to mix
        break;
    }

    h1 ^= length;
    h2 ^= length;

    h1 += h2;
    h2 += h1;

    h1 = fmix64(h1);
    h2 = fmix64(h2);

    h1 += h2;
    h2 += h1;

    return new Hash128(h1, h2);
  }

  private static long fmix64(long k) {
    k ^= k >>> 33;
    k *= 0xff51afd7ed558ccdL;
    k ^= k >>> 33;
    k *= 0xc4ceb9fe1a85ec53L;
    k ^= k >>> 33;
    return k;
  }

  private static long getLongLE(final byte[] data, final int index) {
    return (data[index] & 0xffL)
        | (data[index + 1] & 0xffL) << 8
        | (data[index + 2] & 0xffL) << 16
        | (data[index + 3] & 0xffL) << 24
        | (data[index + 4] & 0xffL) << 32
        | (data[index + 5] & 0xffL) << 40
        | (data[index + 6] & 0xffL) << 48
        | (data[index + 7] & 0xffL) << 56;
  }

  /**
   * An immutable 128-bit hash value, usable as a map key. Equality and hash code are derived from
   * both 64-bit halves.
   *
   * @param high the high 64 bits of the digest
   * @param low the low 64 bits of the digest
   */
  public record Hash128(long high, long low) {}
}
