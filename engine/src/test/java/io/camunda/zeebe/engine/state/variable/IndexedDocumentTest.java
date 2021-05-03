/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Some notes about these tests:
 *
 * <ul>
 *   <li>the document iterator reuses the same document entry, so it's important to clone the entry
 *       when collecting it for testing
 *   <li>keys are stored without the string length - this is because variable names are always
 *       strings, and the whole buffer is the string. When creating ad hoc document entries for
 *       comparison, you have to pack the string then remove its length for the key
 * </ul>
 */
final class IndexedDocumentTest {

  private final IndexedDocument indexedDocument = new IndexedDocument();

  @Test
  void shouldIndexDocument() {
    // given
    final Map<String, Object> document = Map.of("foo", "bar", "baz", "buz");

    // when
    indexedDocument.index(MsgPackUtil.asMsgPack(document));

    // then
    final List<DocumentEntry> entries = collectEntries();
    assertThat(entries)
        .hasSize(document.size())
        .containsExactlyInAnyOrder(
            new DocumentEntry(packStringWithoutLength("foo"), packString("bar")),
            new DocumentEntry(packStringWithoutLength("baz"), packString("buz")));
  }

  @Test
  void shouldRemoveEntriesViaIterator() {
    // given
    final Map<String, Object> document = Map.of("foo", "bar", "baz", "buz");
    indexedDocument.index(MsgPackUtil.asMsgPack(document));
    final DocumentEntryIterator iterator = indexedDocument.iterator();

    // when
    final DocumentEntry remainingEntry = cloneEntry(iterator.next());
    final DocumentEntry removedEntry = cloneEntry(iterator.next());
    iterator.remove();

    // then
    final List<DocumentEntry> entries = collectEntries();
    assertThat(entries).hasSize(1).containsOnly(remainingEntry).doesNotContain(removedEntry);
  }

  @Test
  void shouldWrapNewDocument() {
    // given
    final Map<String, Object> initialDocument = Map.of("foo", "bar", "baz", "buz");
    final Map<String, Object> finalDocument = Map.of("bar", "foo", "buz", "baz");
    indexedDocument.index(MsgPackUtil.asMsgPack(initialDocument));
    final List<DocumentEntry> initialEntries = collectEntries();

    // when
    indexedDocument.index(MsgPackUtil.asMsgPack(finalDocument));

    // then
    final List<DocumentEntry> finalEntries = collectEntries();
    assertThat(finalEntries)
        .hasSize(finalDocument.size())
        .doesNotContainAnyElementsOf(initialEntries)
        .containsExactlyInAnyOrder(
            new DocumentEntry(packStringWithoutLength("bar"), packString("foo")),
            new DocumentEntry(packStringWithoutLength("buz"), packString("baz")));
  }

  private List<DocumentEntry> collectEntries() {
    final List<DocumentEntry> entries = new ArrayList<>();
    for (final DocumentEntry entry : indexedDocument) {
      entries.add(cloneEntry(entry));
    }
    return entries;
  }

  private DocumentEntry cloneEntry(final DocumentEntry entry) {
    return new DocumentEntry(
        BufferUtil.cloneBuffer(entry.getName()), BufferUtil.cloneBuffer(entry.getValue()));
  }

  private DirectBuffer packString(final String value) {
    return MsgPackUtil.encodeMsgPack(b -> b.packString(value));
  }

  private DirectBuffer packStringWithoutLength(final String key) {
    final MsgPackReader reader = new MsgPackReader();
    final DirectBuffer buffer = packString(key);
    reader.wrap(buffer, 0, buffer.capacity());
    reader.readStringLength();

    final int offset = reader.getOffset();
    return new UnsafeBuffer(buffer, offset, buffer.capacity() - offset);
  }
}
