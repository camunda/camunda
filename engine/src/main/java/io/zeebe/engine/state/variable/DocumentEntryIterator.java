/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.variable;

import io.zeebe.msgpack.spec.MsgPackReader;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2IntHashMap.EntryIterator;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Iterates of a document by reading the offsets directly from the associated {@code
 * offsetIterator}. Expected usage only through {@link IndexedDocument#iterator()}.
 *
 * <p>Note that keys are expected to be strings, and as such the length is removed from the MsgPack
 * representation before being added as the name in the {@link DocumentEntry}. String values are
 * kept as is, as values can be any kind of object.
 */
final class DocumentEntryIterator implements Iterator<DocumentEntry> {

  private final MsgPackReader reader;
  private final DocumentEntry entry = new DocumentEntry();
  private final DirectBuffer document = new UnsafeBuffer();

  private EntryIterator offsetIterator;
  private int documentLength;

  DocumentEntryIterator() {
    this(new MsgPackReader());
  }

  DocumentEntryIterator(final MsgPackReader reader) {
    this.reader = reader;
  }

  @Override
  public boolean hasNext() {
    return offsetIterator.hasNext();
  }

  @Override
  public DocumentEntry next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    offsetIterator.next();
    final int keyOffset = offsetIterator.getIntKey();
    final int valueOffset = offsetIterator.getIntValue();

    reader.wrap(document, keyOffset, documentLength - keyOffset);
    final int nameLength = reader.readStringLength();
    final int nameOffset = keyOffset + reader.getOffset();

    reader.wrap(document, valueOffset, documentLength - valueOffset);
    reader.skipValue();
    final int valueLength = reader.getOffset();

    entry.wrap(document, nameOffset, nameLength, valueOffset, valueLength);
    return entry;
  }

  @Override
  public void remove() {
    offsetIterator.remove();
  }

  void wrap(final DirectBuffer document, final EntryIterator offsetIterator) {
    this.document.wrap(document);
    this.offsetIterator = offsetIterator;
    documentLength = document.capacity();
  }
}
