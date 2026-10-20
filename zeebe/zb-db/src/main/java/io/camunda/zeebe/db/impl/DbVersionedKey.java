/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import java.util.Collection;
import java.util.Collections;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Appends a version to any given key, so that one column family can hold several versions of the
 * same key.
 *
 * <p>Version 0 is written without a suffix, and a key without a suffix is read as version 0. A key
 * at version 0 is therefore byte-identical to the wrapped key on its own, and existing data written
 * without a version stays readable without a migration. Any other version is appended as a
 * big-endian long, so all versions of one key sort together, in ascending order, after the version
 * 0 row.
 *
 * <p>Two rules follow from the elided suffix:
 *
 * <ul>
 *   <li>The version is always the last part of the key.
 *   <li>The wrapped key must be self-delimiting: its length must follow from its own bytes. A
 *       wrapped key that consumes the rest of the buffer, such as {@link DbBytes}, swallows the
 *       version.
 * </ul>
 */
public record DbVersionedKey<WrappedKey extends DbKey>(WrappedKey wrappedKey, DbLong version)
    implements DbKey, ContainsForeignKeys {

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    wrappedKey.wrap(buffer, offset, length);
    final int suffixLength = length - wrappedKey.getLength();
    if (suffixLength == 0) {
      version.wrapLong(0);
    } else if (suffixLength == Long.BYTES) {
      version.wrap(buffer, offset + length - Long.BYTES, Long.BYTES);
    } else {
      throw new IllegalStateException(
          "Expected a version suffix of 0 or %d bytes after the wrapped key, but found %d bytes"
              .formatted(Long.BYTES, suffixLength));
    }
  }

  @Override
  public int getLength() {
    return wrappedKey.getLength() + (version.getValue() == 0 ? 0 : Long.BYTES);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    final long value = version.getValue();
    if (value < 0) {
      throw new IllegalArgumentException(
          "Expected a version of 0 or greater, but was %d".formatted(value));
    }
    int written = wrappedKey.write(buffer, offset);
    if (value != 0) {
      written += version.write(buffer, offset + written);
    }
    return written;
  }

  @Override
  public Collection<DbForeignKey<DbKey>> containedForeignKeys() {
    if (wrappedKey instanceof final ContainsForeignKeys foreignKeyProvider) {
      return foreignKeyProvider.containedForeignKeys();
    }
    return Collections.emptyList();
  }
}
