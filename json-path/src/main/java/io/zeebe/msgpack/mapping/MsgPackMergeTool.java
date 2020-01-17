/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

import io.zeebe.msgpack.spec.MsgPackFormat;
import io.zeebe.msgpack.spec.MsgPackType;
import io.zeebe.util.EnsureUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MsgPackMergeTool {

  /**
   * The message for the exception, which is thrown if the resulting document is not a map (json
   * object).
   */
  public static final String EXCEPTION_MSG_RESULTING_DOCUMENT_IS_NOT_OF_TYPE_MAP =
      "Processing failed, since mapping will result in a non map object (json object).";

  private final MsgPackDocumentIndexer documentIndexer;
  private final MsgPackDocumentExtractor documentExtractor;
  private final MsgPackDocumentTreeWriter treeWriter;

  private final MsgPackTree currentTree = new MsgPackTree();
  private final UnsafeBuffer resultBuffer = new UnsafeBuffer(0, 0);

  public MsgPackMergeTool(final int initialDocumentSize) {
    documentIndexer = new MsgPackDocumentIndexer();
    documentExtractor = new MsgPackDocumentExtractor();
    treeWriter = new MsgPackDocumentTreeWriter(initialDocumentSize);

    reset();
  }

  public void reset() {
    currentTree.clear();
  }

  /**
   * Throws exceptions on ambiguous mapping results
   *
   * @throws MappingException in case a mapping has ambiguous results
   */
  public void mergeDocument(final DirectBuffer document, final Mapping... mappings) {
    EnsureUtil.ensureNotNull("document", document);

    if (mappings != null && mappings.length > 0) {
      final MsgPackDiff diff = documentExtractor.extract(document, mappings);
      diff.mergeInto(currentTree);
    } else {
      final MsgPackDiff diff = documentIndexer.index(document);

      diff.mergeInto(currentTree);
    }
  }

  public DirectBuffer writeResultToBuffer() {
    final int resultLen = treeWriter.write(currentTree);
    resultBuffer.wrap(treeWriter.getResult(), 0, resultLen);

    // would be nicer to do this in the merge methods, but not easily doable with
    // current MsgPackTree implementation
    ensureDocumentIsAMsgPackMap(resultBuffer, EXCEPTION_MSG_RESULTING_DOCUMENT_IS_NOT_OF_TYPE_MAP);

    return resultBuffer;
  }

  private void ensureDocumentIsAMsgPackMap(final DirectBuffer document, final String exceptionMsg) {
    final byte b = document.getByte(0);
    final MsgPackFormat format = MsgPackFormat.valueOf(b);
    if (format.getType() != MsgPackType.MAP && format.getType() != MsgPackType.NIL) {
      throw new MappingException(exceptionMsg);
    }
  }
}
