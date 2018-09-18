/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack.mapping;

import static io.zeebe.msgpack.mapping.MsgPackTreeNodeIdConstructor.construct;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public class MappingDiff implements MsgPackDiff {

  private static final int RESULT_ENTRY_LENGTH = 2 * BitUtil.SIZE_OF_INT;

  private Mapping[] mappings;
  private DirectBuffer document;
  private final ExpandableArrayBuffer mappingResults = new ExpandableArrayBuffer();

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

  private static int mapToResultIndex(int mappingIndex) {
    return mappingIndex * RESULT_ENTRY_LENGTH;
  }

  public void setResultOffset(int mappingIndex, int offset) {
    mappingResults.putInt(mapToResultIndex(mappingIndex), offset);
  }

  public void setResultLength(int mappingIndex, int length) {
    mappingResults.putInt(mapToResultIndex(mappingIndex) + BitUtil.SIZE_OF_INT, length);
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

          mergeValueInto(
              document,
              parentId,
              nodeName,
              mapping.getType(),
              ourDocumentId,
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
