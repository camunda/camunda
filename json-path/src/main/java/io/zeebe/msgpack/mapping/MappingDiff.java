/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

import static io.zeebe.msgpack.mapping.MsgPackTreeNodeIdConstructor.construct;

import io.zeebe.msgpack.spec.MsgPackWriter;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MappingDiff implements MsgPackDiff {

  public static final DirectBuffer CONSTANTS_DOCUMENT;
  /*
   * - offset
   * - length
   * - sourceDocument (1) or nullDocument (0)
   */
  private static final int RESULT_ENTRY_LENGTH = 2 * BitUtil.SIZE_OF_INT + BitUtil.SIZE_OF_BYTE;

  static {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[2]);
    final MsgPackWriter writer = new MsgPackWriter();
    writer.wrap(buffer, 0);
    writer.writeMapHeader(0);
    writer.writeNil();
    CONSTANTS_DOCUMENT = buffer;
  }

  private final ExpandableArrayBuffer mappingResults = new ExpandableArrayBuffer();
  private Mapping[] mappings;
  private DirectBuffer document;

  public void init(Mapping[] mappings, DirectBuffer document) {
    this.mappings = mappings;
    this.document = document;
  }

  public int getResultOffset(int mappingIndex) {
    return mappingResults.getInt(mapToResultIndex(mappingIndex));
  }

  public int getResultLength(int mappingIndex) {
    return mappingResults.getInt(mapToResultIndex(mappingIndex) + BitUtil.SIZE_OF_INT);
  }

  public boolean isMappedFromSourceDocument(int mappingIndex) {
    return mappingResults.getByte(mapToResultIndex(mappingIndex) + (2 * BitUtil.SIZE_OF_INT)) == 1;
  }

  private static int mapToResultIndex(int mappingIndex) {
    return mappingIndex * RESULT_ENTRY_LENGTH;
  }

  public void setResult(int mappingIndex, int offset, int length) {
    setResult(mappingIndex, offset, length, true);
  }

  public void setNullResult(int mappingIndex) {
    setResult(mappingIndex, 1, 1, false);
  }

  public void setEmptyMapResult(int mappingIndex) {
    setResult(mappingIndex, 0, 1, false);
  }

  private void setResult(int mappingIndex, int offset, int length, boolean fromSourceDocument) {
    int mappingResultOffset = mapToResultIndex(mappingIndex);
    mappingResults.putInt(mappingResultOffset, offset);

    mappingResultOffset += BitUtil.SIZE_OF_INT;
    mappingResults.putInt(mappingResultOffset, length);

    mappingResultOffset += BitUtil.SIZE_OF_INT;
    mappingResults.putByte(mappingResultOffset, fromSourceDocument ? (byte) 1 : (byte) 0);
  }

  private boolean isIndex(String nodeName) {
    final int len = nodeName.length();
    for (int i = 0; i < len; i++) {
      final char currentChar = nodeName.charAt(i);
      if (currentChar < '0' || currentChar > '9') {
        return false;
      }
    }
    return true;
  }

  @Override
  public void mergeInto(MsgPackTree document) {
    final int constantsDocumentId = document.addDocument(CONSTANTS_DOCUMENT);
    final int ourDocumentId = document.addDocument(this.document);

    for (int i = 0; i < mappings.length; i++) {
      final Mapping mapping = mappings[i];
      final String[] targetPathElements = mapping.getTargetPointer().getPathElements();

      String parentId = "";

      for (int j = 0; j < targetPathElements.length; j++) {
        final String nodeName = targetPathElements[j];

        if (j == targetPathElements.length - 1) {
          final int valueOffset = getResultOffset(i);
          final int valueLength = getResultLength(i);
          final int documentId =
              isMappedFromSourceDocument(i) ? ourDocumentId : constantsDocumentId;

          mergeValueInto(
              document,
              parentId,
              nodeName,
              mapping.getType(),
              documentId,
              valueOffset,
              valueLength);

        } else {
          parentId = mergeContainerInto(document, parentId, nodeName, targetPathElements[j + 1]);
        }
      }
    }
  }

  private String mergeContainerInto(
      MsgPackTree document, String parentId, String nodeName, String nextPathElement) {

    final String nodeId = construct(parentId, nodeName);

    if (document.hasNode(nodeId)) {
      if (!isIndex(nextPathElement)) {
        document.convertToMapNode(nodeId);
      }

      return nodeId;
    } else {
      if (isIndex(nextPathElement)) {
        return document.addArrayNode(parentId, nodeName);
      } else {
        return document.addMapNode(parentId, nodeName);
      }
    }
  }

  private void mergeValueInto(
      MsgPackTree document,
      String parentId,
      String nodeName,
      Mapping.Type mappingType,
      int documentId,
      int valueOffset,
      int valueLength) {
    switch (mappingType) {
      case COLLECT:
        document.appendToArray(parentId, nodeName, documentId, valueOffset, valueLength);
        break;
      case PUT:
      default:
        document.addValueNode(parentId, nodeName, documentId, valueOffset, valueLength);
        break;
    }
  }
}
