/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.translate;

/**
 * The frozen variant-k1 hash/mix functions {@link LakeTranslator} folds a process instance's
 * activated elements and taken sequence flows through (see its "Variant capture (scheme
 * variant-k1)" javadoc section for the full scheme). Both functions are part of the durable scheme
 * contract, not an implementation detail: a stored {@code variant_hash} column can only ever be
 * reproduced — e.g. by a SQL-side reimplementation reading the {@code variants} dictionary table's
 * {@code elements}/{@code flows} columns — by applying exactly these two functions, in this order,
 * over exactly the same inputs. Neither may change without the scheme id itself changing (to {@code
 * variant-k2} or later).
 *
 * <h2>{@link #h64(CharSequence)}</h2>
 *
 * <p>FNV-1a 64-bit over the UTF-8 encoding of the input, offset basis {@code 0xcbf29ce484222325},
 * prime {@code 0x100000001b3}: {@code hash = OFFSET_BASIS; for each byte b: hash = (hash ^ b) *
 * PRIME}. The UTF-8 bytes are produced on the fly, one {@code char} (or one surrogate pair) at a
 * time, so hashing a {@link CharSequence} never allocates an intermediate {@code byte[]} or {@code
 * String} — see {@link #foldChar}.
 *
 * <h2>{@link #mix64(long, long)}</h2>
 *
 * <p>MurmurHash3's 128-bit finalizer step ({@code fmix64}) applied to {@code seed ^ x}: {@code z =
 * seed ^ x; z ^= z >>> 33; z *= 0xff51afd7ed558ccd; z ^= z >>> 33; z *= 0xc4ceb9fe1a85ec53; z ^= z
 * >>> 33; return z}. A well-documented, public-domain 64-bit avalanche mix — reused rather than
 * invented, because a frozen scheme is exactly the situation a known-good, precisely specified
 * function suits best.
 *
 * <h2>Fold and hex encoding</h2>
 *
 * <p>Per distinct element/flow id first seen on an instance: {@code idHash = h64(id)}; {@code h32 =
 * (int) idHash} (the low 32 bits, truncated — used only as the seen-set's compact dedup key, see
 * {@link LakeTranslator}); {@code seed = h64(bpmnProcessId)}; the instance's running hash is XORed
 * with {@code mix64(seed, idHash)} (the <b>full</b> 64-bit {@code idHash}, not the truncated {@code
 * h32}). The stored {@code variant_hash} column is {@link #toHex16(long)} of the final running
 * hash: 16 lowercase hex characters, most significant byte first.
 */
final class VariantHash {

  private static final long FNV_OFFSET_BASIS_64 = 0xcbf29ce484222325L;
  private static final long FNV_PRIME_64 = 0x100000001b3L;

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private VariantHash() {}

  /** FNV-1a 64-bit hash of {@code s}'s UTF-8 encoding — see class javadoc. Garbage-free. */
  static long h64(final CharSequence s) {
    long hash = FNV_OFFSET_BASIS_64;
    final int length = s.length();
    for (int i = 0; i < length; i++) {
      final char c = s.charAt(i);
      if (Character.isHighSurrogate(c)
          && i + 1 < length
          && Character.isLowSurrogate(s.charAt(i + 1))) {
        final int codePoint = Character.toCodePoint(c, s.charAt(i + 1));
        i++; // the low surrogate is consumed as part of this one code point
        hash = foldCodePoint4(hash, codePoint);
      } else {
        hash = foldChar(hash, c);
      }
    }
    return hash;
  }

  /**
   * The MurmurHash3 128-bit finalizer's {@code fmix64} step over {@code seed ^ x} — see class
   * javadoc.
   */
  static long mix64(final long seed, final long x) {
    long z = seed ^ x;
    z ^= z >>> 33;
    z *= 0xff51afd7ed558ccdL;
    z ^= z >>> 33;
    z *= 0xc4ceb9fe1a85ec53L;
    z ^= z >>> 33;
    return z;
  }

  /** 16 lowercase hex characters of {@code value}, most significant byte first. Garbage-free. */
  static String toHex16(final long value) {
    final char[] hex = new char[16];
    for (int i = 0; i < 16; i++) {
      final int shift = (15 - i) * 4;
      hex[i] = HEX_DIGITS[(int) ((value >>> shift) & 0xF)];
    }
    return new String(hex);
  }

  /**
   * Folds one UTF-16 {@code char} in {@code [0, 0xFFFF]} (a BMP code point, including an unpaired
   * surrogate) as its 1-3 byte UTF-8 encoding. Unpaired surrogates encode WTF-8-style rather than
   * being rejected: this only has to be a deterministic, reimplementable function, not a strict
   * UTF-8 validator, and BPMN element/flow/process ids are not expected to contain one.
   */
  private static long foldChar(final long hash, final char c) {
    if (c < 0x80) {
      return foldByte(hash, (byte) c);
    }
    if (c < 0x800) {
      long h = foldByte(hash, (byte) (0xC0 | (c >>> 6)));
      return foldByte(h, (byte) (0x80 | (c & 0x3F)));
    }
    long h = foldByte(hash, (byte) (0xE0 | (c >>> 12)));
    h = foldByte(h, (byte) (0x80 | ((c >>> 6) & 0x3F)));
    return foldByte(h, (byte) (0x80 | (c & 0x3F)));
  }

  /**
   * Folds one supplementary-plane code point (from a surrogate pair) as its 4-byte UTF-8 encoding.
   */
  private static long foldCodePoint4(final long hash, final int codePoint) {
    long h = foldByte(hash, (byte) (0xF0 | (codePoint >>> 18)));
    h = foldByte(h, (byte) (0x80 | ((codePoint >>> 12) & 0x3F)));
    h = foldByte(h, (byte) (0x80 | ((codePoint >>> 6) & 0x3F)));
    return foldByte(h, (byte) (0x80 | (codePoint & 0x3F)));
  }

  private static long foldByte(final long hash, final byte b) {
    return (hash ^ (b & 0xFFL)) * FNV_PRIME_64;
  }
}
