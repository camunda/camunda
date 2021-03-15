/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.variable;

import io.zeebe.msgpack.spec.MsgPackReader;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * This class indexes a MsgPack document from the given buffer by doing an initial parsing and
 * caching the offsets for each key-value pair. When iterating, it will then only iterate over these
 * pairs and access them directly via the buffer.
 *
 * <p>This class is meant to be mutable and reusable - this means that the expected usage is to
 * index the document and read it/iterate over it BEFORE indexing a new document again.
 *
 * <p>Similarly, the iterator will reuse and mutate the same {@link DocumentEntry} instance on each
 * {@link DocumentEntryIterator#next()} call, meaning that if you want to collect entries you should
 * clone them before calling {@link DocumentEntryIterator#next()} again.
 */
public final class IndexedDocument implements Iterable<DocumentEntry> {

  private final MsgPackReader reader;

  // variable name offset -> variable value offset
  private final Int2IntHashMap entries = new Int2IntHashMap(-1);
  private final DocumentEntryIterator iterator = new DocumentEntryIterator();
  private final DirectBuffer document = new UnsafeBuffer();

  public IndexedDocument() {
    this(new MsgPackReader());
  }

  public IndexedDocument(final MsgPackReader reader) {
    this.reader = reader;
  }

  public void index(final DirectBuffer document) {
    this.document.wrap(document);
    entries.clear();
    reader.wrap(document, 0, document.capacity());

    final int variables = reader.readMapHeader();
    for (int i = 0; i < variables; i++) {
      final int keyOffset = reader.getOffset();
      reader.skipValue();
      final int valueOffset = reader.getOffset();
      reader.skipValue();

      entries.put(keyOffset, valueOffset);
    }
  }

  @Override
  public DocumentEntryIterator iterator() {
    iterator.wrap(document, entries.entrySet().iterator());
    return iterator;
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }
}
